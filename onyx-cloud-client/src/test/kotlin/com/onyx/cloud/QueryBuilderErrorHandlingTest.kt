package com.onyx.cloud

import com.google.gson.JsonParser
import com.onyx.cloud.api.matches
import com.onyx.cloud.exceptions.NotFoundException
import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.integration.User
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QueryBuilderErrorHandlingTest {

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
    fun selectingUnknownAttributeSurfacesServerMessage() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("Entity attribute does not exist: favoriteColor not found on entity Users")
        )

        val error = assertFailsWith<RuntimeException> {
            client.from<User>()
                .select("id", "favoriteColor")
                .list<Map<String, Any?>>()
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("favoriteColor", ignoreCase = false), "Missing attribute message not propagated")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/data/db/query/User", request.requestUrl?.encodedPath)

        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        val fields = payload["fields"]?.asJsonArray?.map { it.asString } ?: emptyList()
        assertTrue("favoriteColor" in fields)
    }

    @Test
    fun resolvingUnknownRelationshipSurfacesServerMessage() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("Resolver not found: ghost")
        )

        val error = assertFailsWith<RuntimeException> {
            client.from<Users>()
                .resolve("friends", "ghost")
                .list<Map<String, Any?>>()
        }

        val resolverMessage = error.message.orEmpty()
        assertTrue(resolverMessage.contains("Resolver not found", ignoreCase = false))

        val request = server.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        val resolvers = payload["resolvers"]?.asJsonArray?.map { it.asString } ?: emptyList()
        assertTrue("ghost" in resolvers)
    }

    @Test
    fun queryingUnknownEntityThrowsNotFoundException() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Entity MissingEntity not registered")
        )

        val error = assertFailsWith<NotFoundException> {
            client.from<MissingEntity>()
                .list<Map<String, Any?>>()
        }

        val notFoundMessage = error.message.orEmpty()
        assertTrue(notFoundMessage.contains("MissingEntity", ignoreCase = false))

        val request = server.takeRequest()
        assertEquals("/data/db/query/MissingEntity", request.requestUrl?.encodedPath)
    }

    @Test
    fun unsupportedPredicateErrorMessageIsPreserved() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("Predicate 'MATCHES' is not supported for field status")
        )

        val error = assertFailsWith<RuntimeException> {
            client.from<Users>()
                .where("status".matches(".*active.*"))
                .list<Map<String, Any?>>()
        }

        val predicateMessage = error.message.orEmpty()
        assertTrue(predicateMessage.contains("MATCHES", ignoreCase = false))

        val request = server.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertNotNull(payload["conditions"], "Query payload should include conditions")
    }

    @Test
    fun missingTableNameFailsFastWithoutNetworkCall() {
        val error = assertFailsWith<IllegalStateException> {
            client.select("id")
                .list<Map<String, Any?>>()
        }

        val fastFailMessage = error.message.orEmpty()
        assertTrue(fastFailMessage.contains("Table name must be specified", ignoreCase = false))
        assertEquals(0, server.requestCount)
    }
}

data class Users(val id: String, val name: String, val email: String)
data class MissingEntity(val id: String, val name: String, val email: String)