package com.ogrchatai.app.util

/**
 * Separates model "thinking" text from the final answer.
 *
 * Some models emit explicit `<think>...</think>` tags, while others (for example
 * Qwen 3) rely on the chat template to open the thinking block and only emit the
 * closing tag. Tokens can also split a tag across boundaries, so a short suffix
 * is retained until it is clear whether it starts a tag.
 *
 * @param expectImplicitReasoning when true, the parser starts in an undecided
 * state and waits for a closing reasoning tag. This captures reasoning that has
 * no visible opening tag without leaking it into the answer.
 */
class ReasoningTagParser(
    private val expectImplicitReasoning: Boolean = false
) {

    data class Chunk(
        val text: String,
        val isReasoning: Boolean
    )

    private enum class Mode { ANSWER, REASONING, UNDECIDED }

    private val pending = StringBuilder()
    private var mode = if (expectImplicitReasoning) Mode.UNDECIDED else Mode.ANSWER

    fun process(token: String): List<Chunk> {
        if (token.isEmpty()) return emptyList()
        pending.append(token)
        return drain(final = false)
    }

    fun finish(): List<Chunk> = drain(final = true)

    private fun drain(final: Boolean): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        while (pending.isNotEmpty()) {
            when (mode) {
                Mode.UNDECIDED -> {
                    val open = findEarliest(pending, OPEN_TAGS)
                    val close = findEarliest(pending, CLOSE_TAGS)
                    when {
                        open != null && (close == null || open.index <= close.index) -> {
                            addChunk(chunks, pending.substring(0, open.index), isReasoning = false)
                            pending.delete(0, open.index + open.tag.length)
                            mode = Mode.REASONING
                        }

                        close != null -> {
                            // The opening tag was supplied by the chat template.
                            addChunk(chunks, pending.substring(0, close.index), isReasoning = true)
                            pending.delete(0, close.index + close.tag.length)
                            mode = Mode.ANSWER
                        }

                        final -> {
                            // No reasoning markers at all: this was a normal answer.
                            addChunk(chunks, pending.toString(), isReasoning = false)
                            pending.clear()
                        }

                        pending.length >= IMPLICIT_REASONING_FALLBACK_CHARS -> {
                            // A model that ignores the reasoning instruction should
                            // not block the answer forever.
                            mode = Mode.ANSWER
                        }

                        else -> return chunks
                    }
                }

                Mode.REASONING -> {
                    val open = findEarliest(pending, OPEN_TAGS)
                    val close = findEarliest(pending, CLOSE_TAGS)
                    when {
                        open != null && (close == null || open.index < close.index) -> {
                            // A duplicate opening tag inside the block is formatting,
                            // not content.
                            addChunk(chunks, pending.substring(0, open.index), isReasoning = true)
                            pending.delete(0, open.index + open.tag.length)
                        }

                        close != null -> {
                            addChunk(chunks, pending.substring(0, close.index), isReasoning = true)
                            pending.delete(0, close.index + close.tag.length)
                            mode = Mode.ANSWER
                        }

                        final -> {
                            addChunk(chunks, pending.toString(), isReasoning = true)
                            pending.clear()
                        }

                        else -> {
                            val keep = longestPossibleTagSuffix(pending, OPEN_TAGS + CLOSE_TAGS)
                            val emitLength = pending.length - keep
                            if (emitLength > 0) {
                                addChunk(chunks, pending.substring(0, emitLength), isReasoning = true)
                                pending.delete(0, emitLength)
                            }
                            return chunks
                        }
                    }
                }

                Mode.ANSWER -> {
                    val open = findEarliest(pending, OPEN_TAGS)
                    if (open != null) {
                        addChunk(chunks, pending.substring(0, open.index), isReasoning = false)
                        pending.delete(0, open.index + open.tag.length)
                        mode = Mode.REASONING
                        continue
                    }

                    // A stray closing tag is not answer content.
                    val close = findEarliest(pending, CLOSE_TAGS)
                    if (close != null) {
                        addChunk(chunks, pending.substring(0, close.index), isReasoning = false)
                        pending.delete(0, close.index + close.tag.length)
                        continue
                    }

                    if (final) {
                        addChunk(chunks, pending.toString(), isReasoning = false)
                        pending.clear()
                    } else {
                        val keep = longestPossibleTagSuffix(pending, OPEN_TAGS + CLOSE_TAGS)
                        val emitLength = pending.length - keep
                        if (emitLength > 0) {
                            addChunk(chunks, pending.substring(0, emitLength), isReasoning = false)
                            pending.delete(0, emitLength)
                        }
                    }
                    return chunks
                }
            }
        }
        return chunks
    }

    private fun addChunk(chunks: MutableList<Chunk>, text: String, isReasoning: Boolean) {
        if (text.isNotEmpty()) {
            chunks.add(Chunk(text, isReasoning))
        }
    }

    private fun findEarliest(text: CharSequence, tags: List<String>): FoundTag? {
        var bestIndex = Int.MAX_VALUE
        var bestTag: String? = null
        tags.forEach { tag ->
            val index = text.indexOf(tag, ignoreCase = true)
            if (index >= 0 && (index < bestIndex ||
                    (index == bestIndex && bestTag != null && tag.length > bestTag!!.length))
            ) {
                bestIndex = index
                bestTag = tag
            }
        }
        return bestTag?.let { FoundTag(bestIndex, it) }
    }

    private fun longestPossibleTagSuffix(text: CharSequence, tags: List<String>): Int {
        val max = tags.maxOfOrNull { it.length - 1 } ?: 0
        val start = (text.length - max).coerceAtLeast(0)
        for (length in text.length - start downTo 1) {
            val suffix = text.substring(text.length - length)
            if (tags.any { it.startsWith(suffix, ignoreCase = true) }) {
                return length
            }
        }
        return 0
    }

    private data class FoundTag(val index: Int, val tag: String)

    companion object {
        private const val IMPLICIT_REASONING_FALLBACK_CHARS = 6_000

        private val OPEN_TAGS = listOf(
            "<think>",
            "<thinking>",
            "<|thinking|>",
            "<reasoning>",
            "<|reasoning|>"
        )

        private val CLOSE_TAGS = listOf(
            "</think>",
            "</thinking>",
            "<|/thinking|>",
            "</reasoning>",
            "<|/reasoning|>",
            "<｜end▁of▁thinking｜>"
        )
    }
}
