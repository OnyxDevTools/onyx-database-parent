package com.onyx.cloud

import com.onyx.cloud.api.EntityWireFormat
import com.onyx.cloud.api.insert
import com.onyx.cloud.extensions.EntityMessagePack
import com.onyx.cloud.impl.OnyxClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import kotlin.test.*

class InsertTransportTest {
    data class Observation(val id: String, val value: Int)

    @Test
    fun `insert uses POST and surfaces conflicts without retrying`() {
        for (binary in listOf(false, true)) {
            MockWebServer().use { server ->
                server.start()
                val row = Observation("new", 2)
                val success = MockResponse().setResponseCode(201)
                if (binary) success.addHeader("Content-Type", "application/vnd.msgpack")
                    .setBody(Buffer().write(EntityMessagePack.encode(mapOf("id" to row.id, "value" to row.value))))
                else success.addHeader("Content-Type", "application/json").setBody("""{"id":"new","value":2}""")
                server.enqueue(success)
                server.enqueue(MockResponse().setResponseCode(409).addHeader("Content-Type", "application/json")
                    .setBody("""{"error":{"message":"Entity already exists"}}"""))
                val client = OnyxClient(baseUrl = server.url("/").toString().trimEnd('/'),
                    databaseId = "db", apiKey = "key", apiSecret = "secret",
                    entityWireFormat = if (binary) EntityWireFormat.MESSAGE_PACK else EntityWireFormat.JSON)
                assertEquals(row, client.insert(row))
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/data/db/Observation", request.path)
                assertTrue(request.getHeader("Content-Type")!!.startsWith(if (binary) "application/vnd.msgpack" else "application/json"))
                val failure = assertFailsWith<RuntimeException> { client.insert(row) }
                assertContains(failure.message.orEmpty(), "409")
                assertContains(failure.message.orEmpty(), "Entity already exists")
                assertEquals(2, server.requestCount)
                client.close()
            }
        }
    }
}
