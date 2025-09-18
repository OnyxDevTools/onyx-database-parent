package com.onyx.cloud.integration

import com.onyx.cloud.OnyxClient
import com.onyx.cloud.api.*
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests validating SELECT queries with DISTINCT.
 */
class SelectDistinctIntegrationTest {
    private val client = OnyxClient(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    )

    private fun safeDelete(table: String, id: String) {
        try {
            client.delete(table, id)
        } catch (_: Exception) {
            // ignore if already removed
        }
    }

    private fun newUser(now: Date, prefix: String, index: Int, isActive: Boolean) = User(
        id = UUID.randomUUID().toString(),
        username = "$prefix-user-$index",
        email = "$prefix-user-$index@example.com",
        isActive = isActive,
        createdAt = now,
        updatedAt = now
    )

    private fun saveUsers(prefix: String, now: Date, statuses: List<Boolean>): List<User> =
        statuses.mapIndexed { index, isActive ->
            client.save(newUser(now, prefix, index + 1, isActive))
        }

    @Test
    fun distinctRemovesDuplicateValues() {
        val now = Date()
        val prefix = "distinct-${UUID.randomUUID().toString().substring(0, 8)}"
        val savedUsers = saveUsers(prefix, now, listOf(true, true, false))
        val ids = savedUsers.mapNotNull { it.id }

        try {
            val results = client.from<User>()
                .select("isActive")
                .distinct()
                .where("id" inOp ids)
                .list<Map<String, Any>>()

            val statuses = results.getAllRecords().mapNotNull { it["isActive"] as? Boolean }
            assertEquals(2, results.getAllRecords().size, "Distinct should reduce duplicate values")
            assertEquals(setOf(true, false), statuses.toSet(), "Expected both active and inactive flags")
        } finally {
            savedUsers.mapNotNull { it.id }.forEach { safeDelete("User", it) }
        }
    }

    @Test
    fun distinctWorksWithAdditionalFilters() {
        val now = Date()
        val prefix = "distinct-${UUID.randomUUID().toString().substring(0, 8)}"
        val savedUsers = saveUsers(prefix, now, listOf(true, true, false))
        val ids = savedUsers.mapNotNull { it.id }

        try {
            val results = client.from<User>()
                .select("isActive")
                .distinct()
                .where("id" inOp ids)
                .and("isActive" eq true)
                .list<Map<String, Any>>()

            val statuses = results.getAllRecords().mapNotNull { it["isActive"] as? Boolean }
            assertEquals(1, results.getAllRecords().size, "Distinct should collapse duplicate active rows")
            assertTrue(statuses.all { it }, "Only active rows should remain")
        } finally {
            savedUsers.mapNotNull { it.id }.forEach { safeDelete("User", it) }
        }
    }
}
