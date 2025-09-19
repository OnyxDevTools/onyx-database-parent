package com.onyx.cloud

import com.onyx.cloud.api.FetchInit
import com.onyx.cloud.api.FetchResponse
import com.onyx.cloud.impl.OnyxClient
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.*

/**
 * Exercises utility helpers within [com.onyx.cloud.impl.OnyxClient].
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

    @Test
    fun customFetchDelegatesRequests() {
        var capturedUrl: String? = null
        var capturedInit: FetchInit? = null
        val customClient = OnyxClient(
            baseUrl = "https://example.com",
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
            fetch = { url, init ->
                capturedUrl = url
                capturedInit = init
                StubFetchResponse(bodyText = "true")
            }
        )

        val success = customClient.delete("users", "42")

        assertTrue(success)
        assertEquals("https://example.com/data/db/users/42", capturedUrl)
        assertEquals("DELETE", capturedInit?.method)
        assertEquals("key", capturedInit?.headers?.get("x-onyx-key"))
        assertEquals("secret", capturedInit?.headers?.get("x-onyx-secret"))
    }

    @Test
    fun defaultPartitionAppliedWhenNotProvided() {
        var capturedUrl: String? = null
        val customClient = OnyxClient(
            baseUrl = "https://example.com",
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
            defaultPartition = "tenant-a",
            fetch = { url, _ ->
                capturedUrl = url
                StubFetchResponse(bodyText = "{\"id\":\"123\"}")
            }
        )

        val entity: ExampleEntity? = customClient.findById(ExampleEntity::class, "123", null)

        assertNotNull(entity)
        assertEquals("123", entity.id)
        assertTrue(capturedUrl?.contains("partition=tenant-a") == true)
    }

    @Test
    fun requestAndResponseLoggingEmitsOutput() {
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured))
        try {
            val loggingClient = OnyxClient(
                baseUrl = "https://example.com",
                databaseId = "db",
                apiKey = "key",
                apiSecret = "super-secret-value",
                fetch = { _, _ -> StubFetchResponse(bodyText = "true") },
                requestLoggingEnabled = true,
                responseLoggingEnabled = true
            )

            assertTrue(loggingClient.deleteDocument("doc-1"))
        } finally {
            System.setOut(originalOut)
        }

        val output = captured.toString()
        assertTrue(output.contains("OnyxClient Request"))
        assertTrue(output.contains("OnyxClient Response"))
        assertFalse(output.contains("super-secret-value"))
        assertTrue(output.contains("****"))
    }

    @Test
    fun ttlHeaderIsPropagated() {
        var capturedInit: FetchInit? = null
        val ttlClient = OnyxClient(
            baseUrl = "https://example.com",
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
            ttl = 60_000,
            fetch = { _, init ->
                capturedInit = init
                StubFetchResponse(bodyText = "true")
            }
        )

        assertTrue(ttlClient.delete("users", "42"))
        assertEquals("60000", capturedInit?.headers?.get("x-onyx-ttl"))
    }

    private data class ExampleEntity(val id: String)

    private class StubFetchResponse(
        override val status: Int = 200,
        private val bodyText: String = "",
        private val headerValues: Map<String, String> = emptyMap(),
        override val statusText: String = "OK"
    ) : FetchResponse {
        override val ok: Boolean get() = status in 200..299
        override fun header(name: String): String? = headerValues[name]
        override fun text(): String = bodyText
        override val body: Any? = bodyText
    }
}

