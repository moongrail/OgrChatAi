package com.ogrchatai.app.util

import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBuilderTest {

    @Test
    fun keepsRecentConversationVerbatim() {
        val messages = listOf(
            message(1, MessageRole.USER, "Hello", 100),
            message(2, MessageRole.ASSISTANT, "Hi! How can I help?", 200),
            message(3, MessageRole.USER, "What is two plus two?", 300),
            message(4, MessageRole.ASSISTANT, "Two plus two is four.", 400)
        )

        val result = ConversationContextBuilder.build(
            messages = messages,
            currentQuery = "What is three plus three?",
            budgetTokens = 600
        )

        assertTrue(result.hasContext())
        assertTrue(result.contextText.contains("[Recent conversation - background only]"))
        assertTrue(result.contextText.contains("Two plus two is four."))
        assertTrue(result.includedMessageCount >= 3)
    }

    @Test
    fun ignoresMessagesAtOrBeforeClearMemoryTimestamp() {
        val result = ConversationContextBuilder.build(
            messages = listOf(
                message(1, MessageRole.USER, "Old secret project name", 100),
                message(2, MessageRole.ASSISTANT, "The old secret is Bluebird.", 200),
                message(3, MessageRole.USER, "New topic: what is the weather?", 300),
                message(4, MessageRole.ASSISTANT, "I cannot check live weather.", 400)
            ),
            currentQuery = "Tell me more",
            budgetTokens = 600,
            memoryClearedAt = 250
        )

        assertFalse(result.contextText.contains("Bluebird"))
        assertTrue(result.contextText.contains("what is the weather"))
    }

    @Test
    fun filtersLowValueChatter() {
        val result = ConversationContextBuilder.build(
            messages = listOf(
                message(1, MessageRole.USER, "ok", 100),
                message(2, MessageRole.ASSISTANT, "ok", 150),
                message(3, MessageRole.USER, "Remember: my name is Alex.", 200),
                message(4, MessageRole.ASSISTANT, "thanks", 250),
                message(5, MessageRole.USER, "What is my name?", 300)
            ),
            currentQuery = "What is my name?",
            budgetTokens = 500
        )

        assertFalse(result.contextText.contains("User: ok"))
        assertFalse(result.contextText.contains("Assistant: thanks"))
        assertTrue(result.contextText.contains("my name is Alex"))
    }

    @Test
    fun neverExceedsThePromptBudget() {
        val messages = (1..20).map { index ->
            message(
                id = index.toLong(),
                role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "This is a deliberately long message number $index. ".repeat(8),
                timestamp = index * 100L
            )
        }

        val result = ConversationContextBuilder.build(
            messages = messages,
            currentQuery = "Summarize the important parts",
            budgetTokens = 320
        )

        assertTrue(result.hasContext())
        assertTrue("used ${result.usedTokens} > budget", result.usedTokens <= 320)
    }

    @Test
    fun recallsAnOlderRelevantMessage() {
        val messages = buildList {
            add(message(1, MessageRole.USER, "The launch code is 42. Remember it.", 100))
            add(message(2, MessageRole.ASSISTANT, "Understood, I will remember the code.", 200))
            (3..14).forEach { index ->
                add(
                    message(
                        id = index.toLong(),
                        role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                        content = "General conversation filler sentence number $index.",
                        timestamp = index * 100L
                    )
                )
            }
        }

        val result = ConversationContextBuilder.build(
            messages = messages,
            currentQuery = "What was the launch code?",
            budgetTokens = 200
        )

        assertTrue(result.contextText.contains("launch code is 42"))
        assertTrue(result.recalledMessageCount >= 1)
    }

    @Test
    fun buildsCompressedMemoryNotes() {
        val messages = buildList {
            (1..24).forEach { index ->
                add(
                    message(
                        id = index.toLong(),
                        role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                        content = "Remember this important plan number $index for later.",
                        timestamp = index * 100L
                    )
                )
            }
        }

        val result = ConversationContextBuilder.build(
            messages = messages,
            currentQuery = "hello",
            budgetTokens = 300
        )

        assertTrue(
            "used=${result.usedTokens}, included=${result.includedMessageCount}, " +
                "recalled=${result.recalledMessageCount}, notes=${result.memoryNoteCount}",
            result.memoryNoteCount > 0
        )
        assertTrue(result.contextText.contains("[Conversation memory"))
        assertTrue(result.usedTokens <= 300)
    }

    private fun message(
        id: Long,
        role: MessageRole,
        content: String,
        timestamp: Long
    ): ContextInput {
        val chatMessage = ChatMessage(
            id = id,
            chatId = 1L,
            role = role,
            content = content,
            timestamp = timestamp
        )
        val label = if (role == MessageRole.USER) "User" else "Assistant"
        val formatted = "$label: $content"
        return ContextInput(
            message = chatMessage,
            formatted = formatted,
            tokens = TokenEstimator.estimate(formatted)
        )
    }
}
