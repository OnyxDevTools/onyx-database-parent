package com.onyx.cloud

import com.google.gson.JsonParser
import com.onyx.cloud.api.eq
import com.onyx.cloud.api.inOp
import com.onyx.cloud.api.notIn
import com.onyx.cloud.impl.OnyxClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Ignore
class QueryBuilderSubQueryTest {

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
    fun listQueryUsesIdsFromSubQuery() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                      "records": [
                        {"id": "user-1"},
                        {"id": "user-2"}
                      ],
                      "totalResults": 2
                    }
                """.trimIndent())
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"records": [], "totalResults": 0}""")
        )

        val subQuery = client.from<SubQueryUser>().where("email".eq("test@example.com"))

        client.from<SubQueryUser>()
            .where("id" inOp subQuery)
            .list<Map<String, Any?>>()

        val subRequest = server.takeRequest()
        assertEquals("/data/db/query/SubQueryUser", subRequest.requestUrl?.encodedPath)

        val request = server.takeRequest()
        val rawBody = request.body.readUtf8()
        val payload = JsonParser.parseString(rawBody).asJsonObject
        val criteria = payload["conditions"]!!.asJsonObject["criteria"].asJsonObject
        val valueElement = criteria["value"]!!

        assertTrue(valueElement.isJsonArray, rawBody)
        val values = valueElement.asJsonArray.map { it.asString }

        assertEquals(listOf("user-1", "user-2"), values, rawBody)
        assertEquals("IN", criteria["operator"]?.asString)
    }

    @Test
    fun listQueryUsesSingleEntryMapValuesFromSubQuery() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                      "records": [
                        {"userId": "user-1"},
                        {"userId": "user-2"}
                      ],
                      "totalResults": 2
                    }
                """.trimIndent())
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"records": [], "totalResults": 0}""")
        )

        val subQuery = client.from("SubQueryUser").where("email".eq("map@example.com"))

        client.from<SubQueryUser>()
            .where("id" inOp subQuery)
            .list<Map<String, Any?>>()

        server.takeRequest() // subquery
        val request = server.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        val criteria = payload["conditions"]!!.asJsonObject["criteria"].asJsonObject
        val valueElement = criteria["value"]!!

        assertTrue(valueElement.isJsonArray, payload.toString())
        val values = valueElement.asJsonArray.map { it.asString }

        assertEquals(listOf("user-1", "user-2"), values, payload.toString())
    }

    @Test
    fun updateAndDeleteQueriesSupportSubQueryNotIn() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"records": [{"id": 5}], "totalResults": 1}""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("1"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"records": [{"id": 7}], "totalResults": 1}""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("1"))

        val subQueryForUpdate = client.from<SubQueryUser>().where("name".eq("inactive"))
        val updated = client.from<SubQueryUser>()
            .where("id" notIn subQueryForUpdate)
            .setUpdates("name" to "active")
            .update()
        assertEquals(1, updated)

        val updateRequest = server.takeRequest() // sub query
        assertEquals("/data/db/query/SubQueryUser", updateRequest.requestUrl?.encodedPath)
        val updatePayload = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val updateCriteria = updatePayload["conditions"]!!.asJsonObject["criteria"].asJsonObject
        assertEquals("NOT_IN", updateCriteria["operator"]?.asString)
        assertEquals(listOf("5"), updateCriteria["value"]!!.asJsonArray.map { it.asString })

        val subQueryForDelete = client.from<SubQueryUser>().where("name".eq("archived"))
        val deleted = client.from<SubQueryUser>()
            .where("id" notIn subQueryForDelete)
            .delete()
        assertEquals(1, deleted)

        val deleteSubQuery = server.takeRequest()
        assertEquals("/data/db/query/SubQueryUser", deleteSubQuery.requestUrl?.encodedPath)
        val deletePayload = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val deleteCriteria = deletePayload["conditions"]!!.asJsonObject["criteria"].asJsonObject
        assertEquals("NOT_IN", deleteCriteria["operator"]?.asString)
        assertEquals(listOf("7"), deleteCriteria["value"]!!.asJsonArray.map { it.asString })
    }
}

data class SubQueryUser(val id: String, val name: String, val email: String)
