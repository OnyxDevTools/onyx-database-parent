package com.onyx.entity

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

import javax.security.auth.Subject
import java.security.Principal

/**
 * Created by cosbor11 on 3/2/2015.
 *
 * User for the database.
 */
@Entity
data class SystemUser @JvmOverloads constructor(

    @Identifier(loadFactor = 3)
    @Attribute
    var username: String? = null,

    @Attribute(size = 255)
    var password: String? = null,

    @Attribute
    var role: SystemUserRole? = null

) : AbstractSystemEntity(), IManagedEntity, Principal {


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

    // endregion
}