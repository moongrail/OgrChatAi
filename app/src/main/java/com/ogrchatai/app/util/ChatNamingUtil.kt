package com.ogrchatai.app.util

import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChatNamingUtil {

    private const val MIN_MESSAGES_FOR_NAMING = 3
    private const val MAX_TITLE_LENGTH = 50

    /**
     * Generates a chat title based on the first few messages in the conversation.
     * Returns null if there aren't enough messages or if naming fails.
     */
    suspend fun generateChatTitle(messages: List<ChatMessage>): String? = withContext(Dispatchers.IO) {
        val userMessages = messages
            .filter { it.role == MessageRole.USER }
            .take(3)
            .map { it.content }
            .filter { it.isNotBlank() }

        if (userMessages.size < 2) return@withContext null

        val combinedText = userMessages.joinToString(" ")
        val title = extractTopic(combinedText)
        
        if (title.isNotBlank()) {
            title.trim().take(MAX_TITLE_LENGTH)
        } else {
            null
        }
    }

    private fun extractTopic(text: String): String {
        val lowerText = text.lowercase()
        
        val topics = mapOf(
            "программирование" to listOf("код", "code", "программ", "функци", "class", "function", "variable", "api", "баг", "debug", "ошибка", "error"),
            "обучение" to listOf("как", "how", "обуч", "learn", "tutorial", "guide", "пример", "example", "урок", "lesson"),
            "письмо" to listOf("напиши", "write", "состави", "compose", "текст", "text", "статью", "article", "письмо", "letter"),
            "перевод" to listOf("перевед", "translate", "на английск", "на русск", "на немецк", "на французск"),
            "анализ" to listOf("анализ", "analyze", "разбери", "explain", "почему", "why", "как работает", "how does"),
            "творчество" to listOf("идея", "idea", "креатив", "creative", "придумай", "invent", "сценарий", "script", "рассказ", "story"),
            "математика" to listOf("математик", "math", "формула", "formula", "вычисли", "calculate", "уравнение", "equation"),
            "наука" to listOf("наука", "science", "физик", "physics", "хими", "chemistry", "биолог", "biology"),
            "бизнес" to listOf("бизнес", "business", "стартап", "startup", "маркетинг", "marketing", "продаж", "sales"),
            "здоровье" to listOf("здоровь", "health", "врач", "doctor", "лечени", "treatment", "симптом", "symptom"),
            "путешествия" to listOf("путешеств", "travel", "поездк", "trip", "отель", "hotel", "билет", "ticket", "виза", "visa"),
            "еда" to listOf("рецепт", "recipe", "готовк", "cook", "еда", "food", "ингредиент", "ingredient"),
            "технологии" to listOf("технолог", "technology", "ai", "ии", "нейросет", "neural", "ml", "machine learning"),
        )

        for ((topic, keywords) in topics) {
            if (keywords.any { lowerText.contains(it) }) {
                return topic.capitalize()
            }
        }

        val words = text.split(" ").filter { it.length > 3 }
        if (words.size >= 2) {
            return words.take(3).joinToString(" ").capitalize()
        }

        return text.take(50).trim()
    }

    /**
     * Generates a simple fallback title based on the first user message.
     */
    fun generateFallbackTitle(firstUserMessage: String): String {
        val cleaned = firstUserMessage.trim()
        if (cleaned.length <= MAX_TITLE_LENGTH) return cleaned
        return cleaned.take(MAX_TITLE_LENGTH - 3) + "..."
    }
}
