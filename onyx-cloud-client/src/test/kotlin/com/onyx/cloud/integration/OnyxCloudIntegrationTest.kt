package com.onyx.cloud.integration

import com.onyx.cloud.OnyxClient
import com.onyx.cloud.eq
import kotlin.test.*
import java.util.Date
import java.util.UUID

/**
 * Integration tests exercising the Onyx Cloud backend.
 */
class OnyxCloudIntegrationTest {
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

    private fun newUser(now: Date, isActive: Boolean = true) = User(
        id = UUID.randomUUID().toString(),
        username = "user-${UUID.randomUUID().toString().substring(0, 8)}",
        email = "user${UUID.randomUUID().toString().substring(0, 8)}@example.com",
        isActive = isActive,
        createdAt = now,
        updatedAt = now
    )

    private fun newProfile(userId: String, now: Date) = UserProfile(
        id = UUID.randomUUID().toString(),
        userId = userId,
        firstName = "Test",
        lastName = "User",
        createdAt = now
    )

    private fun newRole(now: Date) = Role(
        id = UUID.randomUUID().toString(),
        name = "role-${UUID.randomUUID().toString().substring(0, 8)}",
        isSystem = false,
        createdAt = now,
        updatedAt = now
    )

    private fun newPermission(now: Date) = Permission(
        id = UUID.randomUUID().toString(),
        name = "perm-${UUID.randomUUID().toString().substring(0, 8)}",
        createdAt = now,
        updatedAt = now
    )

    private fun newRolePermission(role: Role, permission: Permission, now: Date) = RolePermission(
        id = UUID.randomUUID().toString(),
        roleId = role.id!!,
        permissionId = permission.id!!,
        createdAt = now
    )

    private fun newUserRole(userId: String, roleId: String, now: Date) = UserRole(
        id = UUID.randomUUID().toString(),
        userId = userId,
        roleId = roleId,
        createdAt = now
    )

    @Test
    fun saveAndFindUser() {
        val now = Date()
        var user = newUser(now)
        user = client.save(user)
        try {
            val found = client.findById<User>(user.id!!)
            assertNotNull(found)
            assertEquals(user.username, found.username)
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun queryActiveUsers() {
        val now = Date()
        val user = newUser(now, isActive = true)
        client.save(user)
        try {
            val results = client.from<User>()
                .where("isActive" eq true)
                .list<User>()
            assertTrue(results.records.any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun compoundQuery() {
        val now = Date()
        val user = newUser(now)
        client.save(user)
        try {
            val results = client.from<User>()
                .where("username" eq user.username!!)
                .or("email" eq user.email!!)
                .list<User>()
            assertTrue(results.records.any { it.id == user.id })
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun updateUserUsername() {
        val now = Date()
        val user = newUser(now)
        client.save(user)
        try {
            val newUsername = "updated-${UUID.randomUUID().toString().substring(0, 8)}"
            val updated = client.from<User>()
                .where("id" eq user.id!!)
                .setUpdates("username" to newUsername)
                .update()
            assertEquals(1, updated)
            val fetched = client.findById<User>(user.id!!)
            assertEquals(newUsername, fetched?.username)
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun resolvesProfileAndRoles() {
        val now = Date()
        val role = newRole(now)
        val perm = newPermission(now)
        val rolePerm = newRolePermission(role, perm, now)
        val user = newUser(now)
        val profile = newProfile(user.id!!, now)
        val userRole = newUserRole(user.id!!, role.id!!, now)

        client.save(role)
        client.save(perm)
        client.save(rolePerm)
        client.save(user)
        client.save(profile)
        client.save(userRole)

        try {
            val resolved = client.from<User>()
                .where("id" eq user.id!!)
                .resolve("roles", "profile")
                .list<User>()
                .firstOrNull()
            assertNotNull(resolved)
            assertEquals(role.name, resolved.roles?.firstOrNull()?.name)
            assertEquals(profile.firstName, resolved.profile?.firstName)
        } finally {
            safeDelete("User", user.id!!)
            safeDelete("RolePermission", rolePerm.id!!)
            safeDelete("Permission", perm.id!!)
            safeDelete("Role", role.id!!)
        }
    }

    @Test
    fun cascadeDeletesUser() {
        val now = Date()
        val role = client.save(newRole(now))
        val perm = client.save(newPermission(now))
        val rolePerm = client.save(newRolePermission(role, perm, now))
        val user = client.save(newUser(now))
        val profile = client.save(newProfile(user.id!!, now))
        val userRole = client.save(newUserRole(user.id!!, role.id!!, now))

        try {
            // Delete user and cascade delete profile and userRoles
            client.cascade(
                "profile",
                "userRoles"
            ).delete("User", user.id!!)
            assertNull(client.findById<User>(user.id!!))
            assertNull(client.findById<UserProfile>(profile.id!!))
            assertNull(client.findById<UserRole>(userRole.id!!))
        } finally {
            safeDelete("RolePermission", rolePerm.id!!)
            safeDelete("Permission", perm.id!!)
            safeDelete("Role", role.id!!)
        }
    }

    @Test
    fun saveMultipleUsersInSingleRequest() {
        val now = Date()
        val users = List(3) { newUser(now) }

        try {
            client.save(User::class, users)
            users.forEach { user ->
                val fetched = client.findById<User>(user.id!!)
                assertNotNull(fetched, "User ${user.id} should exist after batch save")
                assertEquals(user.username, fetched.username)
            }
        } finally {
            users.forEach { user -> safeDelete("User", user.id!!) }
        }
    }

    @Test
    fun updateMultipleUsersInSingleRequest() {
        val now = Date()
        val users = List(2) { newUser(now) }

        try {
            client.save(User::class, users)

            users.forEach { user ->
                user.username = "batch-updated-${UUID.randomUUID().toString().substring(0, 8)}"
                user.updatedAt = Date()
            }

            client.save(User::class, users)
            users.forEach { user ->
                val fetched = client.findById<User>(user.id!!)
                assertNotNull(fetched, "User ${user.id} should be updated after batch save")
                assertEquals(user.username, fetched.username)
            }
        } finally {
            users.forEach { user -> safeDelete("User", user.id!!) }
        }
    }

    @Test
    fun cascadeSavesUserWithProfileAndRoles() {
        val now = Date()
        val role = client.save(newRole(now))
        try {
            // Prepare nested payload for user with profile and roles
            val userId = UUID.randomUUID().toString()
            val userPayload = mapOf(
                "id" to userId,
                "email" to "cascade@example.com",
                "userRoles" to listOf(mapOf("roleId" to role.id!!)),
                "profile" to mapOf("firstName" to "Cascaded", "lastName" to "User")
            )
            client.cascade(
                "userRoles:UserRole(userId,id)",
                "profile:UserProfile(userId,id)"
            ).save("User", userPayload)

            // Verify nested entities were created
            val profiles = client.from<UserProfile>()
                .where("userId" eq userId)
                .list<UserProfile>()
            assertTrue(profiles.records.any { it.firstName == "Cascaded" })

            val rolesAssigned = client.from<UserRole>()
                .where("userId" eq userId)
                .list<UserRole>()
            assertTrue(rolesAssigned.records.any { it.roleId == role.id })
        } finally {
            // Clean up
            client.run { from<User>().where("email" eq "cascade@example.com").delete() }
            safeDelete("Role", role.id!!)
        }
    }

    @Test
    fun countUsersByEmail() {
        val now = Date()
        // Create a unique user for counting
        val email = "count-${UUID.randomUUID()}@example.com"
        val user = newUser(now).apply { this.email = email }
        client.save(user)
        try {
            val cnt = client.from<User>()
                .where("email" eq email)
                .count()
            assertEquals(1, cnt, "Expected exactly one user with the test email")
        } finally {
            safeDelete("User", user.id!!)
        }
    }

    @Test
    fun groupByActiveUsers() {
        client.from<User>().delete()
        val now = Date()
        val active1 = newUser(now, isActive = true)
        val active2 = newUser(now, isActive = true)
        val inactive = newUser(now, isActive = false)

        client.save(active1)
        client.save(active2)
        client.save(inactive)

        try {
            val results = client.from("User")
                .select("isActive", "count(id)")
                .groupBy("isActive")
                .list<Map<String, Any>>()

            val counts = results.records.associate {
                (it["isActive"] as Boolean) to (it["count(id)"] as Number).toInt()
            }

            assertEquals(2, counts[true], "Expected two active users")
            assertEquals(1, counts[false], "Expected one inactive user")
        } finally {
            safeDelete("User", active1.id!!)
            safeDelete("User", active2.id!!)
            safeDelete("User", inactive.id!!)
        }
    }
}
