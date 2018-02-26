package com.onyx.entity

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Relationship
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import java.security.Principal
import javax.security.auth.Subject

/**
 * Created by Chris Osborn on 3/2/2015.
 *
 * User for the database.
 */
@Entity
data class SystemUser @JvmOverloads constructor(

    @Identifier(loadFactor = 3)
    var username: String? = null,

    @Attribute(size = 255)
    var password: String? = null,

    @Attribute
    var role: SystemUserRole? = null

) : AbstractSystemEntity(), IManagedEntity, Principal {

    @Attribute
    var firstName: String? = null

    @Attribute
    var lastName: String? = null

    @Suppress("UNUSED")
    @Attribute
    var phoneNumber: String?  = null

    @Suppress("UNUSED")
    @Attribute
    var emailAddress: String?  = null

    @Suppress("UNUSED")
    @Relationship(type = RelationshipType.ONE_TO_MANY,
            inverse = "user",
            inverseClass = SystemDirectory::class,
            loadFactor = 1,
            cascadePolicy = CascadePolicy.NONE,
            fetchPolicy = FetchPolicy.LAZY)
    var directories: MutableList<SystemDirectory> = ArrayList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as SystemUser?

        return if (username != null)
            username == that!!.username
        else
            that!!.username == null && if (password != null) password == that.password else that.password == null
    }

    // region Principal

    override fun getName(): String = username!!

    override fun implies(subject: Subject?): Boolean = false

    override fun hashCode(): Int {
        var result = username?.hashCode() ?: 0
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + (role?.hashCode() ?: 0)
        return result
    }

    // endregion
}