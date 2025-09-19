package com.onyx.cloud.integration

import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.api.*
import java.util.Date
import java.util.UUID
import kotlin.test.*

/**
 * Integration tests validating various query operators against the cloud backend.
 */
class QueryCriteriaOperatorIntegrationTest {
    private val client = onyx.init(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    )

    @Suppress("SameParameterValue")
    private fun safeDelete(table: String, id: String) {
        try {
            client.delete(table, id)
        } catch (_: Exception) {
            // ignore if already removed
        }
    }

    private fun newUser(now: Date, suffix: String? = null) = User(
        id = UUID.randomUUID().toString(),
        username = "user-${suffix ?: UUID.randomUUID().toString().substring(0, 8)}",
        email = "user${UUID.randomUUID().toString().substring(0, 8)}@example.com",
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun inOperator() {
        val now = Date()
        val user1 = newUser(now)
        val user2 = newUser(now)
        client.save(user1)
        client.save(user2)
        try {
            val results = client.from<User>()
                .where("id" inOp listOf(user1.id!!, user2.id!!))
                .list<User>()
            val ids = results.getAllRecords().map { it.id }
            assertTrue(ids.containsAll(listOf(user1.id, user2.id)))
        } finally {
            safeDelete("User", user1.id!!)
            safeDelete("User", user2.id!!)
        }
    }

    @Test
    fun notInOperator() {
        val now = Date()
        val user1 = newUser(now)
        val user2 = newUser(now)
        client.save(user1)
        client.save(user2)
        try {
            val results = client.from<User>()
                .where("id" notIn listOf(user1.id!!))
                .list<User>()
            val ids = results.getAllRecords().map { it.id }
            assertFalse(ids.contains(user1.id))
            assertTrue(ids.contains(user2.id))
        } finally {
            safeDelete("User", user1.id!!)
            safeDelete("User", user2.id!!)
        }
    }

    @Test
    fun betweenOperator() {
        val now = Date()
        val user = newUser(now)
        client.save(user)
        try {
            val start = Date(now.time - 1000)
            val end = Date(now.time + 1000)
            val results = client.from<User>()
                .where("createdAt".between(start, end))
                .and("id" eq user.id!!)
                .list<User>()
            assertTrue(results.getAllRecords().any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun likeOperator() {
        val now = Date()
        val user = newUser(now, suffix = "like-test")
        user.username = "user-like-test"
        client.save(user)
        try {
            val results = client.from<User>()
                .where("username".like("user-Like-test"))
                .list<User>()
            assertTrue(results.getAllRecords().any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun matchesOperator() {
        val now = Date()
        val user = newUser(now, suffix = "regex-test")
        client.save(user)
        try {
            val results = client.from<User>()
                .where("username".matches("user-regex-test.*"))
                .list<User>()
            assertTrue(results.getAllRecords().any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun containsOperator() {
        val now = Date()
        val user = newUser(now, suffix = "contains")
        client.save(user)
        try {
            val results = client.from<User>()
                .where("username".contains("contain"))
                .list<User>()
            assertTrue(results.getAllRecords().any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun containsIgnoreCaseOperator() {
        val now = Date()
        val user = newUser(now, suffix = "CaseTest")
        client.save(user)
        try {
            val results = client.from<User>()
                .where("username".containsIgnoreCase("casetest"))
                .list<User>()
            assertTrue(results.getAllRecords().any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun notContainsOperator() {
        val now = Date()
        val user = newUser(now, suffix = "nocontain")
        client.save(user)
        try {
            val results = client.from<User>()
                .where("username".notContains("xyz"))
                .and("id" eq user.id!!)
                .list<User>()
            assertTrue(results.getAllRecords().any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun notContainsIgnoreCaseOperator() {
        val now = Date()
        val user = newUser(now, suffix = "nocase")
        client.save(user)
        try {
            val results = client.from<User>()
                .where("username".notContainsIgnoreCase("xyz"))
                .and("id" eq user.id!!)
                .list<User>()
            assertTrue(results.getAllRecords().any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }
}

