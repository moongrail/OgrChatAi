package com.ogrchatai.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningTagParserTest {

    @Test
    fun separatesGemmaThinkingChannelFromFinalAnswer() {
        val parser = ReasoningTagParser()
        val chunks = mutableListOf<ReasoningTagParser.Chunk>()

        listOf(
            "<|channel>thought",
            "\nThinking about the question.",
            "<channel|>",
            "Final answer."
        ).forEach { token ->
            chunks += parser.process(token)
        }
        chunks += parser.finish()

        assertEquals(
            "\nThinking about the question.",
            chunks.filter { it.isReasoning }.joinToString("") { it.text }
        )
        assertEquals(
            "Final answer.",
            chunks.filterNot { it.isReasoning }.joinToString("") { it.text }
        )
    }

    @Test
    fun handlesGemmaTagsSplitAcrossTokens() {
        val parser = ReasoningTagParser()
        val chunks = mutableListOf<ReasoningTagParser.Chunk>()

        listOf("<|chan", "nel>thought", " Think", "<chan", "nel|>", "Hello").forEach { token ->
            chunks += parser.process(token)
        }
        chunks += parser.finish()

        assertEquals(" Think", chunks.filter { it.isReasoning }.joinToString("") { it.text })
        assertEquals("Hello", chunks.filterNot { it.isReasoning }.joinToString("") { it.text })
    }

    @Test
    fun normalAnswerIsNotMarkedAsReasoning() {
        val parser = ReasoningTagParser()
        val chunks = parser.process("Just a normal answer.") + parser.finish()

        assertEquals(0, chunks.count { it.isReasoning })
        assertEquals(
            "Just a normal answer.",
            chunks.joinToString("") { it.text }
        )
    }
}
