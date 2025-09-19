package com.onyx.cloud.integration

import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.from
import com.onyx.cloud.api.onyx
import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.api.save
import com.onyx.cloud.exceptions.NotFoundException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnyxClientErrorHandlingIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var client: IOnyxDatabase<Any>

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        val baseUrl = server.url("/").toString().trimEnd('/')
        client = onyx.init(baseUrl = baseUrl, databaseId = "db", apiKey = "key", apiSecret = "secret")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun followsTemporaryRedirectsForSaveRequests() {
        val redirectServer = MockWebServer().apply { start() }
        try {
            val redirectLocation = redirectServer.url("/data/db/User").toString()
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", redirectLocation)
            )
            val responseBody = """{"id":"1","username":"redirected"}"""
            redirectServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(responseBody)
            )

            val user = User(id = "1", username = "redirected")
            val saved = client.save(User::class, user)

            assertEquals("redirected", saved.username)

            val initialRequest = server.takeRequest()
            assertEquals("PUT", initialRequest.method)
            assertEquals("/data/db/User", initialRequest.requestUrl?.encodedPath)

            val redirectedRequest = redirectServer.takeRequest()
            assertEquals("PUT", redirectedRequest.method)
            assertEquals("/data/db/User", redirectedRequest.requestUrl?.encodedPath)
            assertTrue(redirectedRequest.body.readUtf8().contains("\"username\":\"redirected\""))
        } finally {
            redirectServer.shutdown()
        }
    }

    @Test
    fun refusesPermanentRedirectsWhenRequestHasBody() {
        server.enqueue(
            MockResponse()
                .setResponseCode(301)
                .addHeader("Location", server.url("/data/db/User").toString())
        )

        val error = assertFailsWith<RuntimeException> {
            client.save(User(id = "1", username = "redirected"))
        }

        assertTrue(error.message.orEmpty().contains("Refusing to follow 301 redirect"))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
    }

    @Test
    fun findByIdReturnsNullWhenEntityMissing() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("User missing")
        )

        val result: User? = client.findById(User::class, "missing")
        assertNull(result)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/data/db/User/missing", request.requestUrl?.encodedPath)
    }

    @Test
    fun getDocumentSurfacesServerErrors() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("internal boom")
        )

        val error = assertFailsWith<RuntimeException> {
            client.getDocument("doc-1")
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("500"))
        assertTrue(message.contains("internal boom"))

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/data/db/document/doc-1", request.requestUrl?.encodedPath)
    }

    @Test
    fun getDocumentPropagatesNotFound() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("missing doc")
        )

        val error = assertFailsWith<NotFoundException> {
            client.getDocument("doc-2")
        }

        assertTrue(error.message.orEmpty().contains("404"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/data/db/document/doc-2", request.requestUrl?.encodedPath)
    }

    @Test
    fun streamingCapturesHttpErrors() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("stream failure")
        )

        client

        val subscription = client.from<User>().stream<User>(
            includeQueryResults = true,
            keepAlive = false,
        )

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("PUT", request.method)
        assertEquals("/data/db/query/stream/User", request.requestUrl?.encodedPath)

        subscription.join()
        val error = subscription.error
        assertNotNull(error)
        assertTrue(error.message.orEmpty().contains("HTTP Error: 500"))
        assertTrue(error.message.orEmpty().contains("stream failure"))
        subscription.cancel()
    }

    @Test
    fun throwsAfterTooManyRedirects() {
        repeat(6) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", server.url("/data/db/document/doc-3").toString())
            )
        }

        val error = assertFailsWith<RuntimeException> {
            client.getDocument("doc-3")
        }

        assertTrue(error.message.orEmpty().contains("too many redirects", ignoreCase = true))
        assertTrue(server.requestCount >= 5)
    }
}

