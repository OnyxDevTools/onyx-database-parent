package com.onyx.ai.agent.chat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class ChatHistoryCondenserAgentTest {

    @Test
    fun testCondense() {
        // Setup test data
        val history = listOf(
            mapOf("id" to "1", "role" to "user", "content" to "Hello! How are you?"),
            mapOf(
                "id" to "2", "role" to "assistant",
                "content" to "I'm doing great, thank you for asking! Also, I can help with any questions you have. Additionally, there are some important points to discuss."
            ),
            mapOf("id" to "3", "role" to "user", "content" to "Goodbye!")
        )

        // Create condenser instance
        val condenser = ChatHistoryCondenserAgent()

        // Condense history
        val condensed = condenser.condense(history)

        // Assertions
        assertEquals(3, condensed.size)

        // Check user message remains unchanged
        val userMessage = condensed.find { it["role"] == "user" }
        assertNotNull(userMessage)
        assertEquals("Hello! How are you?", userMessage?.get("content"))

        // Check assistant message has been condensed
        val assistantMessage = condensed.find { it["role"] == "assistant" }
        assertNotNull(assistantMessage)
        assertEquals(
            """I'm doing great, thank you for asking! I can help with any questions you have. there are some important points to discuss.""",
            assistantMessage?.get("content")
        )

        // Check timestamps and IDs
        condensed.forEach { message ->
            assertTrue(message.containsKey("timestamp"))
            assertTrue(message.containsKey("id"))
            assertTrue(message.containsKey("processed"))
        }
    }

    @Test
    fun testRelevanceFiltering() {
        val history = listOf(
            mapOf("id" to "1", "role" to "user", "content" to "Hello!"),
            mapOf(
                "id" to "2", "role" to "assistant",
                "content" to "I'm here to help. Additional information: not important details."
            ),
            mapOf(
                "id" to "3", "role" to "assistant",
                "content" to "Important note! This is critical.", "important" to true
            ),
            mapOf(
                "id" to "4", "role" to "assistant",
                "content" to "A long message with no important keywords but exceeding length threshold."
            )
        )

        val condenser = ChatHistoryCondenserAgent()
        val condensed = condenser.condense(history)

        // Only messages marked important or containing 'critical' should be kept
        assertEquals(2, condensed.size)

        // User message should be kept
        assertTrue(condensed.any {
            it["role"] == "user" && it["content"]?.toString() == "Hello!"
        })

        // Important assistant message should be kept
        assertTrue(condensed.any {
            it["content"]?.toString() == "Important note! This is critical."
        })

        // Non-important assistant messages should be filtered out
        assertFalse(condensed.any {
            it["content"]?.toString() == "I'm here to help. Additional information: not important details."
        })
        
        assertFalse(condensed.any {
            it["content"]?.toString() == "A long message with no important keywords but exceeding length threshold."
        })
    }
}
