package com.onyx.cloud

import kotlin.test.*

/**
 * Exercises utility helpers within [OnyxClient].
 */
class OnyxClientTest {
    private val client = OnyxClient(baseUrl = "http://localhost", databaseId = "db", apiKey = "k", apiSecret = "s")

    private fun buildQuery(options: Map<String, Any?>): String {
        val method = OnyxClient::class.java.getDeclaredMethod("buildQueryString", Map::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(client, options) as String
    }

    @Test
    fun encodeStringsProperly() {
        assertEquals("hello+world%21", client.encode("hello world!"))
    }

    @Test
    fun buildQueryStringHandlesListsAndNulls() {
        val qs = buildQuery(mapOf("partition" to "users", "fetch" to listOf("friends", "groups"), "nullOpt" to null))
        assertEquals("?partition=users&fetch=friends%2Cgroups", qs)
    }

    @Test
    fun buildQueryStringEmptyMap() {
        val qs = buildQuery(emptyMap())
        assertEquals("", qs)
    }

    @Test
    fun buildQueryStringWithNumbersAndBooleans() {
        val qs = buildQuery(mapOf("limit" to 5, "active" to true))
        assertTrue(qs.contains("limit=5", ignoreCase = false))
        assertTrue(qs.contains("active=true", ignoreCase = false))
    }
}

