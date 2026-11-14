package com.ogrchatai.app.util

import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.MessageRole
import kotlin.math.sqrt

/**
 * One formatted conversation turn, ready to be placed into a prompt.
 */
data class ContextInput(
    val message: ChatMessage,
    val formatted: String,
    val tokens: Int
)

data class ContextBuildResult(
    val contextText: String,
    val usedTokens: Int,
    val includedMessageCount: Int,
    val recalledMessageCount: Int,
    val memoryNoteCount: Int
) {
    fun hasContext(): Boolean = contextText.isNotBlank()
}

/**
 * Builds a compact, RAG-like conversation context without running a second model.
 *
 * The prompt is assembled from three layers:
 *  1. A short memory of older, important statements (preferences, names, plans,
 *     numbers and other facts).
 *  2. Earlier messages that are lexically relevant to the current question.
 *  3. The most recent messages, kept verbatim so the current topic stays coherent.
 *
 * Low-value chatter is skipped in the older layers. Recency and relevance decay
 * means a topic that is never mentioned again gradually disappears. The caller
 * can pass a memory cutoff timestamp after "Clear memory" to ignore all older
 * turns while keeping them visible in the chat UI.
 */
object ConversationContextBuilder {

    fun build(
        messages: List<ContextInput>,
        currentQuery: String,
        budgetTokens: Int,
        memoryClearedAt: Long = 0L
    ): ContextBuildResult {
        if (messages.isEmpty() || budgetTokens <= 0) {
            return ContextBuildResult("", 0, 0, 0, 0)
        }

        val filtered = messages
            .filter { it.message.timestamp > memoryClearedAt }
            .takeLast(MAX_CONTEXT_MESSAGES)
            .filter { it.formatted.isNotBlank() && !isFluff(it.message, it.formatted) }

        if (filtered.isEmpty()) {
            return ContextBuildResult("", 0, 0, 0, 0)
        }

        val usableBudget = (budgetTokens - PROMPT_HEADER_RESERVE_TOKENS).coerceAtLeast(64)
        val sectionBudget = (usableBudget - SECTION_HEADER_RESERVE_TOKENS).coerceAtLeast(64)
        val blocks = filtered.map { input ->
            Block(
                input = input,
                id = input.message.id.takeIf { it > 0 } ?: input.message.timestamp,
                role = input.message.role,
                content = input.message.content,
                timestamp = input.message.timestamp,
                text = input.formatted.trim(),
                tokens = input.tokens.coerceAtLeast(1)
            )
        }

        val recentBudget = (sectionBudget * RECENT_BUDGET_RATIO).toInt().coerceAtLeast(MIN_RECENT_BUDGET)
        val recent = ArrayDeque<Block>()
        var recentTokens = 0

        for (index in blocks.indices.reversed()) {
            if (recent.size >= MAX_RECENT_MESSAGES) break
            val block = blocks[index]
            if (recent.isEmpty()) {
                val available = recentBudget.coerceAtLeast(MIN_NEWEST_BLOCK_TOKENS)
                val clipped = block.clippedTo(available)
                recent.addFirst(clipped)
                recentTokens += clipped.tokens
                continue
            }
            if (recentTokens + block.tokens > recentBudget) break
            recent.addFirst(block)
            recentTokens += block.tokens
        }

        val recentIds = recent.mapTo(mutableSetOf()) { it.id }
        val older = blocks.filterNot { it.id in recentIds }
        val queryTerms = terms(currentQuery)
        val scored = older.map { block ->
            ScoredBlock(block, score(block, queryTerms, older.size, older.indexOf(block)))
        }

        val retrievalBudget = ((sectionBudget - recentTokens) * RETRIEVAL_BUDGET_RATIO)
            .toInt()
            .coerceIn(0, (sectionBudget * MAX_RETRIEVAL_SHARE).toInt())
        val retrieved = selectRetrieved(scored, retrievalBudget)
        val retrievedIds = retrieved.mapTo(mutableSetOf()) { it.id }

        val memoryBudget = (sectionBudget - recentTokens - retrieved.sumOf { it.tokens })
            .coerceAtLeast(0)
        val notes = buildMemoryNotes(
            scored = scored.filterNot { it.block.id in retrievedIds },
            budgetTokens = memoryBudget.coerceAtMost((sectionBudget * MAX_MEMORY_SHARE).toInt())
        )
        val noteLines = notes.map { it.text }

        val sections = buildList {
            if (noteLines.isNotEmpty()) {
                add(
                    buildString {
                        appendLine("[Conversation memory - compressed notes from earlier turns]")
                        appendLine("Use these notes only as background. Do not repeat or quote them.")
                        noteLines.forEach { append("- ").appendLine(it) }
                    }.trim()
                )
            }
            if (retrieved.isNotEmpty()) {
                add(
                    buildString {
                        appendLine("[Relevant earlier messages]")
                        retrieved.forEach { block ->
                            appendLine(block.text)
                        }
                    }.trim()
                )
            }
            if (recent.isNotEmpty()) {
                add(
                    buildString {
                        appendLine("[Recent conversation - background only]")
                        recent.forEach { block ->
                            appendLine(block.text)
                        }
                    }.trim()
                )
            }
        }

        var contextText = sections.joinToString("\n\n").trim()
        if (TokenEstimator.estimate(contextText) > usableBudget) {
            // The newest messages are the most valuable. Trim only the oldest part
            // if the section headers pushed the result slightly over budget.
            contextText = clipStartToBudget(contextText, usableBudget)
        }

        return ContextBuildResult(
            contextText = contextText,
            usedTokens = TokenEstimator.estimate(contextText),
            includedMessageCount = recent.size + retrieved.size,
            recalledMessageCount = retrieved.size,
            memoryNoteCount = noteLines.size
        )
    }

    private fun selectRetrieved(scored: List<ScoredBlock>, budgetTokens: Int): List<Block> {
        if (budgetTokens <= 0) return emptyList()

        val selected = mutableListOf<Block>()
        var used = 0
        scored.asSequence()
            .filter { it.score >= RETRIEVAL_SCORE_THRESHOLD }
            .filter { it.block.tokens > 0 }
            .sortedWith(
                compareByDescending<ScoredBlock> { it.score }
                    .thenByDescending { it.block.timestamp }
            )
            .forEach { candidate ->
                if (selected.size >= MAX_RETRIEVED_MESSAGES) return@forEach
                val clipped = candidate.block.clippedTo(MAX_RETRIEVED_BLOCK_TOKENS)
                if (used + clipped.tokens > budgetTokens) return@forEach
                selected += clipped
                used += clipped.tokens
            }

        return selected.sortedBy { it.timestamp }
    }

    private fun buildMemoryNotes(scored: List<ScoredBlock>, budgetTokens: Int): List<MemoryNote> {
        if (budgetTokens <= 0) return emptyList()

        val notes = mutableListOf<MemoryNote>()
        val seen = mutableSetOf<String>()
        var used = 0

        scored.asSequence()
            .filter { it.score >= MEMORY_SCORE_THRESHOLD }
            .sortedByDescending { it.score }
            .forEach { candidate ->
                if (notes.size >= MAX_MEMORY_NOTES) return@forEach
                val note = compressNote(candidate.block).ifBlank { return@forEach }
                val key = note.lowercase()
                    .replace(Regex("\\s+"), " ")
                    .take(80)
                if (!seen.add(key)) return@forEach

                val tokens = TokenEstimator.estimate(note) + 3
                if (used + tokens > budgetTokens) return@forEach
                notes += MemoryNote(note, candidate.block.timestamp)
                used += tokens
            }

        return notes.sortedBy { it.timestamp }.map { it.copy(text = it.text) }
    }

    private fun compressNote(block: Block): String {
        val normalized = block.content
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return ""

        val sentences = normalized
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (sentences.isEmpty()) return ""

        val important = sentences.filter { importanceOf(it) >= 1.0 }
        val chosen = when {
            block.role == MessageRole.USER && important.isEmpty() -> sentences.take(2)
            important.isNotEmpty() -> important.take(2)
            else -> sentences.take(1)
        }
        val prefix = if (block.role == MessageRole.USER) "User" else "Assistant"
        val text = chosen.joinToString(" ").take(MAX_MEMORY_NOTE_CHARS)
        return "$prefix: $text"
    }

    private fun score(
        block: Block,
        queryTerms: Set<String>,
        olderCount: Int,
        index: Int
    ): Double {
        val blockTerms = terms(block.content)
        val overlap = if (queryTerms.isEmpty()) 0 else blockTerms.count { it in queryTerms }
        val lexical = if (blockTerms.isEmpty()) {
            0.0
        } else {
            overlap.toDouble() * 4.0 / sqrt(blockTerms.size.toDouble())
        }
        val importance = importanceOf(block.content)
        if (lexical == 0.0 && importance == 0.0) return 0.0

        val recency = 0.7 + 0.6 * (index.toDouble() / olderCount.coerceAtLeast(1))
        val roleBoost = if (block.role == MessageRole.USER) 1.15 else 1.0
        return (lexical + importance) * recency * roleBoost
    }

    private fun importanceOf(text: String): Double {
        val lower = text.lowercase()
        var score = 0.0
        IMPORTANT_MARKERS.forEach { marker ->
            if (lower.contains(marker)) score += 1.0
        }
        if (DIGIT_REGEX.containsMatchIn(lower)) score += 0.45
        if (lower.contains('?')) score += 0.15
        if (text.length in 80..600) score += 0.15
        return score.coerceAtMost(3.0)
    }

    private fun isFluff(message: ChatMessage, formatted: String): Boolean {
        if (message.attachments.isNotEmpty()) return false
        val normalized = message.content
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
        if (normalized.isEmpty()) return true
        if (normalized.length <= 3) return true

        val assistantOnlyFluff = message.role == MessageRole.ASSISTANT &&
            normalized.length < 18 &&
            DIGIT_REGEX.find(normalized) == null &&
            !normalized.contains('?')
        if (assistantOnlyFluff) return true

        return FLUFF_REGEX.matches(normalized) && DIGIT_REGEX.find(normalized) == null
    }

    private fun terms(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        return WORD_REGEX.findAll(text.lowercase())
            .map { it.value.trim('\'', '"', '`', '.', ',', '!', '?', ':', ';', '(', ')', '[', ']') }
            .filter { it.length >= MIN_TERM_LENGTH && it !in STOP_WORDS }
            .toSet()
    }

    private fun clipStartToBudget(text: String, budgetTokens: Int): String {
        if (TokenEstimator.estimate(text) <= budgetTokens) return text
        var trimmed = text
        while (trimmed.isNotEmpty() && TokenEstimator.estimate(trimmed) > budgetTokens) {
            val overflow = TokenEstimator.estimate(trimmed) - budgetTokens
            val removeChars = (overflow * CHARS_PER_ESTIMATED_TOKEN).coerceAtLeast(32)
            trimmed = trimmed.drop(removeChars.coerceAtMost(trimmed.length / 2))
                .trimStart()
        }
        return trimmed
    }

    private fun Block.clippedTo(maxTokens: Int): Block {
        if (tokens <= maxTokens) return this
        val maxChars = (maxTokens * CHARS_PER_ESTIMATED_TOKEN).coerceAtLeast(64)
        if (text.length <= maxChars) return copy(tokens = maxTokens)
        val clipped = text.take(maxChars).trimEnd() + "\n[...truncated]"
        return copy(text = clipped, tokens = TokenEstimator.estimate(clipped))
    }

    private data class Block(
        val input: ContextInput,
        val id: Long,
        val role: MessageRole,
        val content: String,
        val timestamp: Long,
        val text: String,
        val tokens: Int
    )

    private data class ScoredBlock(val block: Block, val score: Double)

    private data class MemoryNote(val text: String, val timestamp: Long)

    private const val MAX_CONTEXT_MESSAGES = 160
    private const val MAX_RECENT_MESSAGES = 24
    private const val MAX_RETRIEVED_MESSAGES = 8
    private const val MAX_MEMORY_NOTES = 10
    private const val MIN_TERM_LENGTH = 3
    private const val MAX_MEMORY_NOTE_CHARS = 240
    private const val MAX_RETRIEVED_BLOCK_TOKENS = 700
    private const val RECENT_BUDGET_RATIO = 0.58
    private const val RETRIEVAL_BUDGET_RATIO = 0.45
    private const val MAX_RETRIEVAL_SHARE = 0.28
    private const val MAX_MEMORY_SHARE = 0.22
    private const val MIN_RECENT_BUDGET = 96
    private const val MIN_NEWEST_BLOCK_TOKENS = 96
    private const val RETRIEVAL_SCORE_THRESHOLD = 0.8
    private const val MEMORY_SCORE_THRESHOLD = 0.9
    private const val PROMPT_HEADER_RESERVE_TOKENS = 48
    private const val SECTION_HEADER_RESERVE_TOKENS = 40
    private const val CHARS_PER_ESTIMATED_TOKEN = 3

    private val DIGIT_REGEX = Regex("\\d")
    private val WORD_REGEX = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'_-]*")

    private val FLUFF_REGEX = Regex(
        "^(ok|okay|sure|yes|no|yep|nope|thanks|thank you|got it|understood|cool|great|nice|" +
            "хорошо|конечно|да|нет|спасибо|понял|поняла|ладно|ясно|" +
            "vale|claro|sí|si|gracias|de acuerdo|" +
            "d'accord|oui|non|merci|bien|" +
            "okay|ja|nein|danke|gut|verstanden)[.!]?$"
    )

    private val IMPORTANT_MARKERS = listOf(
        "remember", "important", "prefer", "my name is", "i am", "i live", "always", "never",
        "don't forget", "do not forget", "my favorite", "goal", "plan", "must",
        "запомни", "важно", "предпочитаю", "меня зовут", "я живу", "всегда", "никогда",
        "не забудь", "мой", "моя", "любимый", "цель", "план", "нужно",
        "recuerda", "importante", "prefiero", "me llamo", "siempre", "nunca", "mi favorito",
        "souviens", "important", "préfère", "je m'appelle", "toujours", "jamais", "mon préféré",
        "erinnere", "wichtig", "bevorzuge", "ich heiße", "immer", "nie", "mein lieblings"
    )

    private val STOP_WORDS = setOf(
        // English
        "the", "and", "for", "you", "your", "are", "was", "were", "with", "this", "that",
        "have", "has", "had", "not", "but", "what", "when", "where", "who", "why", "how",
        "can", "could", "would", "should", "will", "just", "from", "about", "into", "they",
        "them", "there", "here", "been", "being", "does", "did", "doing", "also", "than",
        "then", "very", "some", "any", "all", "our", "out", "get", "got", "let", "its",
        // Russian
        "что", "как", "это", "для", "или", "при", "без", "над", "под", "про", "через",
        "если", "так", "вот", "уже", "ещё", "еще", "его", "она", "они", "оно", "мы", "вы",
        "ты", "я", "мне", "меня", "мой", "моя", "мои", "все", "весь", "всё", "нет", "да",
        "быть", "был", "была", "было", "были", "будет", "можно", "нужно", "только", "очень",
        // Spanish
        "que", "los", "las", "del", "una", "por", "para", "con", "como", "pero", "sus",
        "este", "esta", "esto", "son", "fue", "han", "hay", "muy", "sin", "sobre", "entre",
        "cuando", "donde", "porque", "también", "tambien", "más", "mas", "pero", "sus",
        // French
        "les", "des", "une", "pour", "avec", "dans", "sur", "pas", "plus", "par", "aux",
        "est", "sont", "été", "etre", "être", "fait", "faire", "comme", "mais", "tout",
        "tous", "cette", "ces", "qui", "que", "dont", "où", "ou", "très", "tres", "bien",
        // German
        "der", "die", "das", "und", "ist", "nicht", "ein", "eine", "mit", "sich", "auf",
        "für", "fur", "von", "den", "dem", "des", "auch", "als", "aus", "bei", "nach",
        "wird", "werden", "war", "waren", "hat", "haben", "wie", "was", "wer", "wo", "sehr"
    )
}
