package com.onyx.cloud.integration

import java.util.Date

/**
 * Data classes mirroring the cloud schema for integration tests.
 */
data class User(
    var id: String? = null,
    var username: String? = null,
    var email: String? = null,
    var isActive: Boolean = false,
    var lastLoginAt: Date? = null,
    var createdAt: Date? = null,
    var updatedAt: Date? = null,
    var deletedAt: Date? = null,
    // Resolvers
    @Transient var roles: List<Role>? = null,
    @Transient var profile: UserProfile? = null,
    @Transient var userRoles: List<UserRole>? = null,
)

data class UserProfile(
    var id: String? = null,
    var userId: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var phone: String? = null,
    var address: Map<String, Any?>? = null,
    var avatarUrl: String? = null,
    var bio: String? = null,
    var createdAt: Date? = null,
    var updatedAt: Date? = null,
    var deletedAt: Date? = null,
    var age: Int? = null,
)

data class Role(
    var id: String? = null,
    var name: String? = null,
    var description: String? = null,
    var isSystem: Boolean = false,
    var createdAt: Date? = null,
    var updatedAt: Date? = null,
    var deletedAt: Date? = null,
    // Resolvers
    @Transient var permissions: List<Permission>? = null,
    @Transient var rolePermissions: List<RolePermission>? = null,
)

data class Permission(
    var id: String? = null,
    var name: String? = null,
    var description: String? = null,
    var createdAt: Date? = null,
    var updatedAt: Date? = null,
    var deletedAt: Date? = null,
)

data class UserRole(
    var id: String? = null,
    var userId: String? = null,
    var roleId: String? = null,
    var createdAt: Date? = null,
    // Resolver
    @Transient var role: Role? = null,
)

data class RolePermission(
    var id: String? = null,
    var roleId: String? = null,
    var permissionId: String? = null,
    var createdAt: Date? = null,
    // Resolvers
    @Transient var permission: Permission? = null,
    @Transient var role: Role? = null,
)
