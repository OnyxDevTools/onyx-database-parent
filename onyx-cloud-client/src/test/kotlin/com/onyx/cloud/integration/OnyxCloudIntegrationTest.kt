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
        val user = newUser(now)
        client.save(user)
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
            assertTrue(client.delete("User", user.id!!))
            assertNull(client.findById<User>(user.id!!))
            assertNull(client.findById<UserProfile>(profile.id!!))
            assertNull(client.findById<UserRole>(userRole.id!!))
        } finally {
            safeDelete("RolePermission", rolePerm.id!!)
            safeDelete("Permission", perm.id!!)
            safeDelete("Role", role.id!!)
        }
    }
}
