package com.onyx.cloud.integration

import com.onyx.cloud.OnyxClient
import com.onyx.cloud.StreamSubscription
import com.onyx.cloud.eq
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Integration tests for streaming functionality against the real Onyx Cloud backend.
 */
class OnyxCloudStreamingIntegrationTest {
    private val client = OnyxClient(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    )

    private fun newUser(now: Date, isActive: Boolean = true) = User(
        id = UUID.randomUUID().toString(),
        username = "stream-user-${UUID.randomUUID().toString().substring(0, 8)}",
        email = "stream${UUID.randomUUID().toString().substring(0, 8)}@example.com",
        isActive = isActive,
        createdAt = now,
        updatedAt = now
    )

    private fun safeDelete(id: String?) {
        try { if (id != null) client.delete("User", id) } catch (_: Exception) { }
    }

    @Test
    fun streamIncludesQueryResults() {
        val now = Date()
        val initialUser = newUser(now, isActive = true)
        val createdUser = newUser(now, isActive = true)
        client.save(initialUser)
        var subscription: StreamSubscription? = null
        try {
            val initial = mutableListOf<User>()
            val added = mutableListOf<User>()
            subscription = client.from<User>()
                .where("isActive" eq true)
                .onItem<User> { initial.add(it) }
                .onItemAdded<User> { added.add(it) }
                .stream<User>(includeQueryResults = true, keepAlive = true)

            Thread.sleep(1000)
            client.save(createdUser)
            Thread.sleep(1000)
            subscription.cancelAndJoin()
            subscription = null

            assertTrue(initial.any { it.id == initialUser.id })
            assertTrue(added.any { it.id == createdUser.id })
        } finally {
            subscription?.cancelAndJoin()
            safeDelete(initialUser.id)
            safeDelete(createdUser.id)
        }
    }

    @Test
    fun streamWithPredicate() {
        val now = Date()
        val inactiveInitial = newUser(now, isActive = false)
        val inactiveCreated = newUser(now, isActive = false)
        val activeCreated = newUser(now, isActive = true)
        client.save(inactiveInitial)
        var subscription: StreamSubscription? = null
        try {
            val initial = mutableListOf<User>()
            val added = mutableListOf<User>()
            subscription = client.from<User>()
                .where("isActive" eq false)
                .onItem<User> { initial.add(it) }
                .onItemAdded<User> { added.add(it) }
                .stream<User>(includeQueryResults = true, keepAlive = true)

            Thread.sleep(1000)
            client.save(inactiveCreated)
            Thread.sleep(500)
            client.save(activeCreated)
            Thread.sleep(1000)
            subscription.cancelAndJoin()
            subscription = null

            assertTrue(initial.any { it.id == inactiveInitial.id })
            assertTrue(added.any { it.id == inactiveCreated.id })
            assertTrue(added.none { it.id == activeCreated.id })
        } finally {
            subscription?.cancelAndJoin()
            safeDelete(inactiveInitial.id)
            safeDelete(inactiveCreated.id)
            safeDelete(activeCreated.id)
        }
    }

    @Test
    fun addSaveListenerWithoutQueryResults() {
        val now = Date()
        val toCreate = newUser(now, isActive = true)
        val initial = mutableListOf<User>()
        val added = mutableListOf<User>()
        var subscription: StreamSubscription? = null
        try {
            subscription = client.from<User>()
                .where("isActive" eq true)
                .onItem<User> { initial.add(it) }
                .onItemAdded<User> { added.add(it) }
                .stream<User>(includeQueryResults = false, keepAlive = true)

            Thread.sleep(1000)
            client.save(toCreate)
            Thread.sleep(1000)
            subscription.cancelAndJoin()
            subscription = null

            assertTrue(initial.isEmpty())
            assertTrue(added.any { it.id == toCreate.id })
        } finally {
            subscription?.cancelAndJoin()
            safeDelete(toCreate.id)
        }
    }

    @Test
    fun addDeleteListenerWithoutQueryResults() {
        val now = Date()
        val toDelete = newUser(now, isActive = true)
        client.save(toDelete)
        var subscription: StreamSubscription? = null
        try {
            val deleted = mutableListOf<User>()
            subscription = client.from<User>()
                .where("id" eq toDelete.id!!)
                .onItemDeleted<User> { deleted.add(it) }
                .stream<User>(includeQueryResults = false, keepAlive = true)

            Thread.sleep(1000)
            client.delete("User", toDelete.id!!)
            Thread.sleep(1000)
            subscription.cancelAndJoin()
            subscription = null

            assertTrue(deleted.any { it.id == toDelete.id })
        } finally {
            subscription?.cancelAndJoin()
            safeDelete(toDelete.id)
        }
    }

    @Test
    fun addUpdateListenerWithoutQueryResults() {
        val now = Date()
        val toUpdate = newUser(now, isActive = true)
        client.save(toUpdate)
        var subscription: StreamSubscription? = null
        try {
            val updated = mutableListOf<User>()
            subscription = client.from<User>()
                .where("id" eq toUpdate.id!!)
                .onItemUpdated<User> { updated.add(it) }
                .stream<User>(includeQueryResults = false, keepAlive = true)

            Thread.sleep(1000)
            toUpdate.username = "updated-${UUID.randomUUID().toString().substring(0, 8)}"
            client.save(toUpdate)
            Thread.sleep(1000)
            subscription.cancelAndJoin()
            subscription = null

            assertTrue(updated.any { it.id == toUpdate.id && it.username == toUpdate.username })
        } finally {
            subscription?.cancelAndJoin()
            safeDelete(toUpdate.id)
        }
    }
}
