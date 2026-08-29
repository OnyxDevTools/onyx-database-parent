package com.onyx.cloud.integration

import com.onyx.cloud.api.eq
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

/**
 * Integration tests for update queries using the cloud client.
 */
class UpdateQueryIntegrationTest {
    private val client by lazy { CloudIntegrationFixture.client() }

    @Test
    fun updateNonexistentRecordsReturnsZero() {
        val updated = client.from<User>()
            .where("username" eq "missing-${UUID.randomUUID()}")
            .setUpdates("email" to "noop@example.com")
            .update()

        assertEquals(0, updated, "No rows should be updated when no records match")
    }
}
