package com.onyx.cloud

import kotlin.test.Test
import kotlin.test.assertEquals

class CascadePayloadSerializationTest {
    @Test
    fun wrapsSingleEntity() {
        val client = OnyxClient(baseUrl = "https://example.com", databaseId = "db", apiKey = "key", apiSecret = "secret")
        val method = OnyxClient::class.java.getDeclaredMethod("buildCascadePayload", Any::class.java)
        method.isAccessible = true
        val entity = mapOf("id" to "1", "name" to "Test")
        @Suppress("UNCHECKED_CAST")
        val payload = method.invoke(client, entity) as Map<String, Any?>
        val inner = payload["payload"] as Map<*, *>
        assertEquals(entity, inner["single"])
    }

    @Test
    fun wrapsEntityList() {
        val client = OnyxClient(baseUrl = "https://example.com", databaseId = "db", apiKey = "key", apiSecret = "secret")
        val method = OnyxClient::class.java.getDeclaredMethod("buildCascadePayload", Any::class.java)
        method.isAccessible = true
        val entities = listOf(mapOf("id" to "1"), mapOf("id" to "2"))
        @Suppress("UNCHECKED_CAST")
        val payload = method.invoke(client, entities) as Map<String, Any?>
        val inner = payload["payload"] as Map<*, *>
        assertEquals(entities, inner["items"])
    }
}

