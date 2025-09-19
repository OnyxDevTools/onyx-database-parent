package com.onyx.cloud.integration

import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.api.OnyxDocument
import com.onyx.cloud.api.onyx
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("unused")
private val enforceIpv4Stack = run {
    System.setProperty("java.net.preferIPv4Stack", "true")
    System.setProperty("java.net.preferIPv6Addresses", "false")
}

/**
 * Integration tests for document endpoints against the shared Onyx Cloud database.
 */
class DocumentIntegrationTest {
    private val client = onyx.init(
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    )

    private fun newDocument(): OnyxDocument {
        val documentId = UUID.randomUUID().toString()
        val now = Date()
        val plainContent = "Integration document ${'$'}{UUID.randomUUID()}"
        val encodedContent = Base64.getEncoder().encodeToString(plainContent.toByteArray(StandardCharsets.UTF_8))
        return OnyxDocument(
            documentId = documentId,
            path = "/integration/tests/${'$'}documentId.txt",
            created = now,
            updated = now,
            mimeType = "text/plain",
            content = encodedContent
        )
    }

    private fun cleanupDocument(documentId: String) {
        try {
            client.deleteDocument(documentId)
        } catch (_: Exception) {
            // Best-effort cleanup. Ignore network errors during teardown.
        }
    }

    @Test
    fun saveDocumentReturnsServerMetadata() {
        val document = newDocument()
        val saved = client.saveDocument(document)
        try {
            assertEquals(document.documentId, saved.documentId)
            assertEquals(document.path, saved.path)
            assertEquals(document.mimeType, saved.mimeType)
            assertTrue(saved.created.time > 0)
            assertTrue(saved.updated.time > 0)
            assertTrue(saved.content.isEmpty(), "Server response should not echo document content")
        } finally {
            cleanupDocument(document.documentId)
        }
    }

    @Test
    fun getDocumentReturnsOriginalContent() {
        val document = newDocument()
        client.saveDocument(document)
        val expectedContent = String(Base64.getDecoder().decode(document.content), StandardCharsets.UTF_8)
        try {
            val fetched = client.getDocument(document.documentId)
            assertEquals(expectedContent, fetched)
        } finally {
            cleanupDocument(document.documentId)
        }
    }
}
