package com.onyx.cloud.integration

import com.onyx.cloud.OnyxClient
import com.onyx.cloud.exceptions.NotFoundException
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("unused")
private val enforceIpv4Stack = run {
    System.setProperty("java.net.preferIPv4Stack", "true")
    System.setProperty("java.net.preferIPv6Addresses", "false")
}

/**
 * Exercises Onyx client error handling scenarios against the real cloud backend.
 */
class OnyxClientErrorHandlingIntegrationTest {
    private val client = OnyxClient(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    )

    private val unauthorizedClient = OnyxClient(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "invalid-secret"
    )

    private fun newUser(now: Date) = User(
        id = UUID.randomUUID().toString(),
        username = "unauth-${'$'}{UUID.randomUUID().toString().substring(0, 8)}",
        email = "unauth${'$'}{UUID.randomUUID().toString().substring(0, 8)}@example.com",
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun saveWithInvalidCredentialsReturnsUnauthorizedError() {
        val user = newUser(Date())
        val error = assertFailsWith<RuntimeException> {
            unauthorizedClient.save(user)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("401"), "Expected 401 status in message but was: ${'$'}message")
        assertTrue(message.contains("Invalid credentials"), "Expected invalid credentials message but was: ${'$'}message")
    }

    @Test
    fun findByIdReturnsNullWhenEntityMissing() {
        val missingId = "missing-${'$'}{UUID.randomUUID()}"
        val result = client.findById<User>(missingId)
        assertNull(result, "Expected missing entity to return null")
    }

    @Test
    fun getDocumentPropagatesNotFound() {
        val missingDocument = "missing-${'$'}{UUID.randomUUID()}"
        val error = assertFailsWith<NotFoundException> {
            client.getDocument(missingDocument)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("404"), "Expected 404 in message but was: ${'$'}message")
        assertTrue(message.contains(missingDocument), "Expected document id in message but was: ${'$'}message")
    }

    @Test
    fun queryUnknownTableThrowsNotFoundException() {
        val tableName = "MissingTable${'$'}{UUID.randomUUID().toString().substring(0, 8)}"
        val error = assertFailsWith<NotFoundException> {
            client.from(tableName)
                .list<Map<String, Any?>>()
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains(tableName), "Expected missing table name in error message but was: ${'$'}message")
    }

    @Test
    fun streamingWithInvalidCredentialsCapturesUnauthorizedError() {
        val subscription = unauthorizedClient.stream(
            table = "User",
            selectQuery = mapOf("type" to "SelectQuery"),
            includeQueryResults = false,
            keepAlive = false
        ) { }

        subscription.join()

        val error = subscription.error
        assertNotNull(error, "Expected streaming to surface error")

        val message = error.message.orEmpty()
        assertTrue(message.contains("401"), "Expected 401 status in message but was: ${'$'}message")
        assertTrue(message.contains("Invalid credentials"), "Expected invalid credentials message but was: ${'$'}message")

        subscription.cancel()
    }
}
