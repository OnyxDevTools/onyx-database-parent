package com.onyx.cloud.integration

import com.onyx.cloud.OnyxClient
import com.onyx.cloud.asc
import com.onyx.cloud.startsWith
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests validating paging behavior against the cloud backend.
 */
class QueryPagingIntegrationTest {
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
            // Ignore if already removed
        }
    }

    private fun newUser(prefix: String, index: Int, baseTime: Date): User {
        val suffix = index.toString().padStart(2, '0')
        val timestamp = Date(baseTime.time + index)
        return User(
            id = UUID.randomUUID().toString(),
            username = "$prefix-$suffix",
            email = "$prefix-$suffix@example.com",
            isActive = true,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }

    @Test
    fun pageThroughUsersWithCustomPageSize() {
        val now = Date()
        val prefix = "paging-${UUID.randomUUID().toString().substring(0, 8)}"
        val createdIds = mutableListOf<String>()
        val expectedUsernames = (1..5).map { index -> "$prefix-${index.toString().padStart(2, '0')}" }

        try {
            expectedUsernames.forEachIndexed { idx, _ ->
                val saved = client.save(newUser(prefix, idx + 1, now))
                saved.id?.let { createdIds += it }
            }

            val results = client.from<User>()
                .where("username".startsWith(prefix))
                .orderBy(asc("username"))
                .pageSize(2)
                .list<User>()

            val firstPageUsernames = results.records.mapNotNull { it.username }
            assertEquals(expectedUsernames.take(2), firstPageUsernames, "First page should honor requested ordering and size")
            assertNotNull(results.nextPage, "Additional pages should provide a next page token")

            val allUsernames = results.getAllRecords().mapNotNull { it.username }
            assertEquals(expectedUsernames, allUsernames, "Paging should return every matching record in order")
        } finally {
            createdIds.forEach { id -> safeDelete("User", id) }
        }
    }

    @Test
    fun forEachPageStopsWhenActionReturnsFalse() {
        val now = Date()
        val prefix = "paging-${UUID.randomUUID().toString().substring(0, 8)}"
        val createdIds = mutableListOf<String>()
        val expectedUsernames = (1..4).map { index -> "$prefix-${index.toString().padStart(2, '0')}" }

        try {
            expectedUsernames.forEachIndexed { idx, _ ->
                val saved = client.save(newUser(prefix, idx + 1, now))
                saved.id?.let { createdIds += it }
            }

            val results = client.from<User>()
                .where("username".startsWith(prefix))
                .orderBy(asc("username"))
                .pageSize(2)
                .list<User>()

            assertNotNull(results.nextPage, "Should have more than one page for this data set")

            var visitedPages = 0
            val visitedUsernames = mutableListOf<String>()
            results.forEachPage { page ->
                visitedPages += 1
                visitedUsernames += page.mapNotNull { it.username }
                assertTrue(page.size <= 2, "Each page should contain no more than the requested page size")
                false
            }

            assertEquals(1, visitedPages, "Iteration should stop after the first page when action returns false")
            assertEquals(expectedUsernames.take(2), visitedUsernames, "Only the first page should have been visited")
        } finally {
            createdIds.forEach { id -> safeDelete("User", id) }
        }
    }
}
