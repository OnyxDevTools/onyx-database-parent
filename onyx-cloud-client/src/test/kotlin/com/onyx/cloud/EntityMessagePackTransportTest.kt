package com.onyx.cloud

import com.onyx.cloud.api.EntityWireFormat
import com.onyx.cloud.api.FetchInit
import com.onyx.cloud.api.FetchResponse
import com.onyx.cloud.api.OnyxConfig
import com.onyx.cloud.api.onyx
import com.onyx.cloud.extensions.ENTITY_MESSAGE_PACK_MEDIA_TYPE
import com.onyx.cloud.extensions.EntityMessagePack
import com.onyx.cloud.extensions.gson
import com.onyx.cloud.impl.OnyxClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityMessagePackTransportTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun binaryQueryNegotiatesAndDecodesByActualResponseContentType() {
        val response = linkedMapOf(
            "records" to listOf(mapOf("id" to "e-1", "name" to "Møøse 🚀")),
            "totalResults" to 1L,
        )
        server.enqueue(messagePackResponse(response))
        val client = binaryClient()

        val json = client.executeQuery(
            "ExampleEntity",
            linkedMapOf("where" to mapOf("id" to "e-1"), "limit" to 1L),
        )

        assertEquals(1, gson.fromJson(json, Map::class.java)["totalResults"]?.let { (it as Number).toInt() })
        val request = server.takeRequest()
        assertEquals(ENTITY_MESSAGE_PACK_MEDIA_TYPE, request.getHeader("Content-Type"))
        assertEquals(
            "$ENTITY_MESSAGE_PACK_MEDIA_TYPE, application/json;q=0.9",
            request.getHeader("Accept"),
        )
        val requestBody = EntityMessagePack.decode(request.body.readByteArray()) as Map<*, *>
        assertEquals(1L, requestBody["limit"])
    }

    @Test
    fun binaryGetHasNoContentTypeAndAcceptsJsonSuccessFallback() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{\"id\":\"e-1\",\"name\":\"fallback\"}"),
        )
        val client = binaryClient()

        val entity: ExampleEntity? = client.findById(ExampleEntity::class, "e-1", null)

        assertEquals(ExampleEntity("e-1", "fallback"), entity)
        val request = server.takeRequest()
        assertNull(request.getHeader("Content-Type"))
        assertTrue(request.getHeader("Accept")!!.startsWith(ENTITY_MESSAGE_PACK_MEDIA_TYPE))
    }

    @Test
    fun everyMutatingEntityOperationUsesBinaryWithoutRetry() {
        val entity = ExampleEntity("e-1", "saved")
        server.enqueue(messagePackResponse(mapOf("id" to entity.id, "name" to entity.name)))
        // DELETE-by-id succeeds with an empty 200 response; it has no negotiated representation.
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(messagePackResponse(2L))
        server.enqueue(messagePackResponse(3L))
        server.enqueue(messagePackResponse(4L))
        val client = binaryClient()

        val saved: ExampleEntity = client.save(ExampleEntity::class, entity)
        assertEquals(entity, saved)
        assertTrue(client.delete("ExampleEntity", entity.id, null))
        assertEquals(2, client.executeCountForQuery("ExampleEntity", mapOf("limit" to 1)))
        assertEquals(3, client.executeUpdateQuery("ExampleEntity", mapOf("updates" to mapOf("name" to "new"))))
        assertEquals(4, client.executeDeleteQuery("ExampleEntity", mapOf("limit" to 1)))

        repeat(5) {
            val request = server.takeRequest()
            if (request.bodySize > 0L) {
                assertEquals(ENTITY_MESSAGE_PACK_MEDIA_TYPE, request.getHeader("Content-Type"))
            } else {
                assertNull(request.getHeader("Content-Type"))
            }
            assertTrue(request.getHeader("Accept")!!.startsWith(ENTITY_MESSAGE_PACK_MEDIA_TYPE))
        }
        assertEquals(5, server.requestCount)
    }

    @Test
    fun jsonErrorIsReadableAndMutatingRequestIsNotRetried() {
        server.enqueue(
            MockResponse()
                .setResponseCode(415)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"binary protocol is disabled\"}"),
        )
        val client = binaryClient()

        val error = assertFailsWith<RuntimeException> {
            client.executeUpdateQuery("ExampleEntity", mapOf("updates" to mapOf("name" to "changed")))
        }

        assertTrue(error.message!!.contains("binary protocol is disabled"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun nonEntityRoutesStayJsonWhenBinaryEntitiesAreEnabled() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("true"),
        )
        val client = binaryClient()

        assertTrue(client.deleteDocument("doc-1"))

        val request = server.takeRequest()
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Content-Type"))
    }

    @Test
    fun defaultWireFormatRemainsJson() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"records\":[],\"totalResults\":0}"),
        )
        val client = OnyxClient(
            baseUrl = server.url("/").toString(),
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
        )

        client.executeQuery("ExampleEntity", mapOf("limit" to 1))

        val request = server.takeRequest()
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.body.readUtf8().startsWith("{"))
    }

    @Test
    fun facadeAndCustomFetchPropagateRawBinaryBodies() {
        var captured: FetchInit? = null
        val responseBytes = EntityMessagePack.encode(7L)
        val client = onyx.init<Any>(
            OnyxConfig(
                baseUrl = "https://example.test",
                databaseId = "db",
                apiKey = "key",
                apiSecret = "secret",
                entityWireFormat = EntityWireFormat.MESSAGE_PACK,
                fetch = { _, init ->
                    captured = init
                    object : FetchResponse {
                        override val ok: Boolean = true
                        override val status: Int = 200
                        override val statusText: String = "OK"
                        override fun header(name: String): String? =
                            if (name.equals("Content-Type", ignoreCase = true)) ENTITY_MESSAGE_PACK_MEDIA_TYPE else null
                        override fun text(): String = error("Binary response must be read as bytes")
                        override fun bytes(): ByteArray = responseBytes
                        override val body: Any? = responseBytes
                    }
                },
            ),
        )

        assertEquals(7, (client as OnyxClient).executeCountForQuery("ExampleEntity", mapOf("limit" to 1)))
        assertNull(captured!!.body)
        assertTrue(captured!!.bodyBytes!!.isNotEmpty())
        assertEquals(ENTITY_MESSAGE_PACK_MEDIA_TYPE, captured!!.headers!!["Content-Type"])
    }

    @Test
    fun binaryLiveQueryConsumesConcatenatedFramesAndSkipsPrimeNil() {
        val bytes = EntityMessagePack.encode(null) +
            EntityMessagePack.encode(mapOf("action" to "CREATE", "entity" to mapOf("id" to "e-1"))) +
            EntityMessagePack.encode(mapOf("action" to "UPDATE", "entity" to mapOf("id" to "e-1")))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", ENTITY_MESSAGE_PACK_MEDIA_TYPE)
                .setBody(Buffer().write(bytes)),
        )
        val events = CopyOnWriteArrayList<String>()
        val client = binaryClient()

        val subscription = client.stream(
            table = "ExampleEntity",
            selectQuery = mapOf("limit" to 1),
            includeQueryResults = true,
            keepAlive = false,
            onLine = events::add,
        )
        subscription.join()

        assertNull(subscription.error)
        assertEquals(2, events.size)
        assertTrue(events[0].contains("CREATE"))
        assertTrue(events[1].contains("UPDATE"))
        assertFalse(events.any { it == "null" })
        val request = server.takeRequest()
        assertEquals(ENTITY_MESSAGE_PACK_MEDIA_TYPE, request.getHeader("Content-Type"))
        assertEquals(1L, (EntityMessagePack.decode(request.body.readByteArray()) as Map<*, *>)["limit"])
    }

    private fun binaryClient(): OnyxClient = OnyxClient(
        baseUrl = server.url("/").toString(),
        databaseId = "db",
        apiKey = "key",
        apiSecret = "secret",
        entityWireFormat = EntityWireFormat.MESSAGE_PACK,
    )

    private fun messagePackResponse(value: Any?): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", ENTITY_MESSAGE_PACK_MEDIA_TYPE)
        .setBody(Buffer().write(EntityMessagePack.encode(value)))

    private data class ExampleEntity(val id: String, val name: String)
}
