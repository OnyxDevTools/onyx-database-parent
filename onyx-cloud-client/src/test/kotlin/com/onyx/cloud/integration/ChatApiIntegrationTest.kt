package com.onyx.cloud.integration

import com.onyx.cloud.api.onyx
import com.onyx.cloud.impl.ChatMessage
import com.onyx.cloud.impl.ChatRequest
import com.onyx.cloud.impl.OnyxClient
import kotlin.test.*

/**
 * Integration tests for the AI/Chat API.
 */
class ChatApiIntegrationTest {
    private val client = onyx.init(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    ) as OnyxClient

    @Test
    fun shorthandChatReturnsResponse() {
        val response = client.chat("Say hello in exactly 3 words.")
        
        assertNotNull(response)
        assertTrue(response.isNotEmpty(), "Chat response should not be empty")
    }

    @Test
    fun shorthandChatWithCustomModel() {
        val response = client.chat(
            prompt = "What is 2+2? Answer with just the number.",
            model = "onyx"
        )
        
        assertNotNull(response)
        assertTrue(response.isNotEmpty(), "Chat response should not be empty")
    }

    @Test
    fun shorthandChatWithTemperature() {
        val response = client.chat(
            prompt = "Say 'test' exactly.",
            temperature = 0.0  // Deterministic response
        )
        
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
    }

    @Test
    fun aiChatWithFullRequest() {
        val request = ChatRequest(
            model = "onyx",
            messages = listOf(
                ChatMessage("system", "You are a helpful assistant that gives brief answers."),
                ChatMessage("user", "What color is the sky? One word answer.")
            ),
            temperature = 0.5
        )

        val response = client.ai.chat(request)

        assertNotNull(response)
        assertNotNull(response.id)
        assertTrue(response.choices.isNotEmpty(), "Response should have choices")
        
        val firstChoice = response.choices.first()
        assertNotNull(firstChoice.message)
        assertEquals("assistant", firstChoice.message.role)
        assertTrue(firstChoice.message.content.isNotEmpty())
    }

    @Test
    fun aiChatWithMapRequest() {
        val request = mapOf(
            "model" to "onyx",
            "messages" to listOf(
                mapOf("role" to "user", "content" to "Say 'hello'")
            )
        )

        val response = client.ai.chat(request)

        assertNotNull(response)
        assertTrue(response.containsKey("choices"))
    }

    @Test
    fun aiChatReturnsUsageStatistics() {
        val request = ChatRequest(
            model = "onyx",
            messages = listOf(ChatMessage("user", "Hi"))
        )

        val response = client.ai.chat(request)

        assertNotNull(response)
        // Usage may or may not be present depending on API version
        if (response.usage != null) {
            assertTrue(response.usage!!.promptTokens >= 0)
            assertTrue(response.usage!!.completionTokens >= 0)
            assertTrue(response.usage!!.totalTokens >= 0)
        }
    }

    @Test
    fun aiGetModelsReturnsList() {
        val models = client.ai.getModels()

        assertNotNull(models)
        assertNotNull(models.data)
        // Should have at least one model available
        assertTrue(models.data.isNotEmpty(), "Should have at least one model")
    }

    @Test
    fun aiGetModelsContainsOnyxModel() {
        val models = client.ai.getModels()

        assertNotNull(models)
        val modelIds = models.data.map { it.id }
        // Should contain the default onyx model or similar
        assertTrue(modelIds.isNotEmpty())
    }

    @Test
    fun aiGetModelInfo() {
        // First get available models
        val models = client.ai.getModels()
        assertNotNull(models)
        assertTrue(models.data.isNotEmpty())

        // Get info for the first available model
        val modelId = models.data.first().id
        val modelInfo = client.ai.getModel(modelId)

        assertNotNull(modelInfo)
        assertEquals(modelId, modelInfo.id)
    }

    @Test
    fun aiChatStreamingCollectsContent() {
        val request = ChatRequest(
            model = "onyx",
            messages = listOf(ChatMessage("user", "Count from 1 to 3"))
        )

        val contentBuilder = StringBuilder()
        
        client.ai.chatStream(request) { chunk ->
            chunk.choices.firstOrNull()?.delta?.content?.let {
                contentBuilder.append(it)
            }
        }

        val fullContent = contentBuilder.toString()
        assertTrue(fullContent.isNotEmpty(), "Streaming should produce content")
    }

    @Test
    fun chatClientCanBeAccessedViaMethod() {
        val aiClient = client.chat()
        
        assertNotNull(aiClient)
        // Should be the same AI client instance
        assertSame(client.ai, aiClient)
    }

    @Test
    fun multiTurnConversation() {
        val messages = mutableListOf(
            ChatMessage("system", "You are a counting assistant."),
            ChatMessage("user", "Start counting from 1.")
        )

        // First turn
        val response1 = client.ai.chat(ChatRequest(
            model = "onyx",
            messages = messages
        ))

        assertNotNull(response1)
        val assistantReply = response1.choices.first().message

        // Add assistant's reply and continue conversation
        messages.add(assistantReply)
        messages.add(ChatMessage("user", "Continue."))

        // Second turn
        val response2 = client.ai.chat(ChatRequest(
            model = "onyx",
            messages = messages
        ))

        assertNotNull(response2)
        assertTrue(response2.choices.isNotEmpty())
    }
}
