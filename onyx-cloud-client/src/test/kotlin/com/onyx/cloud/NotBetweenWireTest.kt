package com.onyx.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onyx.cloud.api.EntityWireFormat
import com.onyx.cloud.api.QueryCriteriaOperator
import com.onyx.cloud.api.notBetween
import com.onyx.cloud.impl.OnyxClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotBetweenWireTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OnyxClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OnyxClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
            entityWireFormat = EntityWireFormat.JSON,
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun notBetweenUsesTheServerOperatorAndTwoElementWireValue() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"records":[],"totalResults":0}""")
        )

        client.from<NotBetweenWireEntity>()
            .where("age".notBetween(18, 30))
            .list<NotBetweenWireEntity>()

        val criteria = JsonParser.parseString(server.takeRequest().body.readUtf8())
            .asJsonObject.objectAt("conditions").objectAt("criteria")
        assertEquals("age", criteria["field"].asString)
        assertEquals(QueryCriteriaOperator.NOT_BETWEEN.name, criteria["operator"].asString)
        assertEquals(listOf(18, 30), criteria["value"].asJsonArray.map { it.asInt })
    }

    private fun JsonObject.objectAt(name: String): JsonObject = get(name).asJsonObject
}

private data class NotBetweenWireEntity(val id: String = "", val age: Int = 0)
