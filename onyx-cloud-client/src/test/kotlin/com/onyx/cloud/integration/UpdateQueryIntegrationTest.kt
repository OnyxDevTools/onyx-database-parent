package com.onyx.cloud.integration

import com.onyx.cloud.api.eq
import com.onyx.cloud.api.from
import com.onyx.cloud.api.onyx
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

/**
 * Integration tests for update queries using the cloud client.
 */
class UpdateQueryIntegrationTest {
    private val client = onyx.init(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    )

    @Test
    fun updateNonexistentRecordsReturnsZero() {
        val updated = client.from<User>()
            .where("username" eq "missing-${UUID.randomUUID()}")
            .setUpdates("email" to "noop@example.com")
            .update()

        assertEquals(0, updated, "No rows should be updated when no records match")
    }
}
