package com.onyx.cloud

import com.google.gson.JsonParser
import com.onyx.cloud.api.eq
import com.onyx.cloud.api.inOp
import com.onyx.cloud.impl.OnyxClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryBuilderJoinSubqueryTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OnyxClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        val base = server.url("/").toString().trimEnd('/')
        client = OnyxClient(baseUrl = base, databaseId = "db", apiKey = "key", apiSecret = "secret")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listQuerySupportsInOperatorWithQueryBuilderValue() {
        server.enqueue(
            MockResponse()
                .setBody("""{"records":[],"totalResults":0}""")
        )

        val subQuery = client.from<JoinUser>().where("status".eq("active"))

        client.from<JoinUser>()
            .where("resolver" inOp subQuery)
            .list<Map<String, Any?>>()

        val request = server.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        val outerCriteria = payload["conditions"].asJsonObject["criteria"].asJsonObject
        assertEquals("IN", outerCriteria["operator"].asString)

        val subQueryPayload = outerCriteria["value"].asJsonObject
        assertEquals("JoinUser", subQueryPayload["table"].asString)

        val nestedQuery = subQueryPayload["query"].asJsonObject
        assertEquals("SelectQuery", nestedQuery["type"].asString)

        val nestedCriteria = nestedQuery["conditions"].asJsonObject["criteria"].asJsonObject
        assertEquals("status", nestedCriteria["field"].asString)
        assertEquals("active", nestedCriteria["value"].asString)
    }

    @Test
    fun updateQuerySupportsInOperatorWithQueryBuilderValue() {
        server.enqueue(MockResponse().setBody("1"))

        val subQuery = client.from<JoinUser>().where("status".eq("pending"))

        client.from<JoinUser>()
            .where("resolver" inOp subQuery)
            .setUpdates("status" to "approved")
            .update()

        val request = server.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertEquals("UpdateQuery", payload["type"].asString)
        val subQueryPayload = payload["conditions"].asJsonObject["criteria"].asJsonObject["value"].asJsonObject
        assertEquals("JoinUser", subQueryPayload["table"].asString)
        assertTrue(subQueryPayload["query"].isJsonObject)
    }

    @Test
    fun deleteQuerySupportsInOperatorWithQueryBuilderValue() {
        server.enqueue(MockResponse().setBody("1"))

        val subQuery = client.from<JoinUser>().where("status".eq("archived"))

        client.from<JoinUser>()
            .where("resolver" inOp subQuery)
            .delete()

        val request = server.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject

        assertEquals("SelectQuery", payload["type"].asString)
        val subQueryPayload = payload["conditions"].asJsonObject["criteria"].asJsonObject["value"].asJsonObject
        assertEquals("JoinUser", subQueryPayload["table"].asString)
    }
}

data class JoinUser(val id: String = "", val status: String = "")
