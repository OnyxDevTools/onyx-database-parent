package com.onyx.cloud.integration

import com.onyx.cloud.api.*
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Integration tests validating that query sorting works against the cloud backend.
 */
class QuerySortOrderIntegrationTest {
    private val client by lazy { CloudIntegrationFixture.client() }

    private fun safeDelete(table: String, id: String) {
        try {
            client.delete(table, id)
        } catch (_: Exception) {
            // ignore if already removed
        }
    }

    private fun newUser(username: String, now: Date) = User(
        id = UUID.randomUUID().toString(),
        username = username,
        email = "${username}-${UUID.randomUUID().toString().substring(0, 8)}@example.com",
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    private fun createUsers(usernames: List<String>): List<User> {
        val baseTime = Date()
        return usernames.mapIndexed { index, username ->
            client.save(newUser(username, Date(baseTime.time + index * 1000L)))
        }
    }

    @Test
    fun orderByAscendingReturnsRecordsInAscendingOrder() {
        val prefix = "sort-asc-${UUID.randomUUID().toString().substring(0, 8)}"
        val usernames = listOf("$prefix-c", "$prefix-a", "$prefix-b")
        val savedUsers = createUsers(usernames)
        val expectedOrder = savedUsers.mapNotNull { it.username }.sorted()
        val ids = savedUsers.map { it.id!! }

        try {
            val results = client.from<User>()
                .where("id" inOp ids)
                .orderBy(asc("username"))
                .list<User>()

            val actualOrder = results.getAllRecords().mapNotNull { it.username }
            assertEquals(expectedOrder, actualOrder)
        } finally {
            savedUsers.forEach { user ->
                user.id?.let { safeDelete("User", it) }
            }
        }
    }

    @Test
    fun orderByDescendingReturnsRecordsInDescendingOrder() {
        val prefix = "sort-desc-${UUID.randomUUID().toString().substring(0, 8)}"
        val usernames = listOf("$prefix-a", "$prefix-c", "$prefix-b")
        val savedUsers = createUsers(usernames)
        val expectedOrder = savedUsers.mapNotNull { it.username }.sortedDescending()
        val ids = savedUsers.map { it.id!! }

        try {
            val results = client.from<User>()
                .where("id" inOp ids)
                .orderBy(desc("username"))
                .list<User>()

            val actualOrder = results.getAllRecords().mapNotNull { it.username }
            assertEquals(expectedOrder, actualOrder)
        } finally {
            savedUsers.forEach { user ->
                user.id?.let { safeDelete("User", it) }
            }
        }
    }
}
