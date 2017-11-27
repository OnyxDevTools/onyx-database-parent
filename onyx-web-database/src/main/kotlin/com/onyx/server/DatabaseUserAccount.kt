package com.onyx.server

import com.onyx.entity.SystemUser
import io.undertow.security.idm.Account

import java.security.Principal
import java.util.HashSet

/**
 * Created by timothy.osborn on 4/23/15.
 *
 * Auth account object
 */
internal class DatabaseUserAccount(private val user: SystemUser?) : Account {

    private var roles: MutableSet<String>? = null

    val password: String?
        get() = user!!.password

    override fun getPrincipal(): Principal? = user

    override fun getRoles(): Set<String>? {
        if (user == null) {
            return null
        }
        if (roles == null) {
            roles = HashSet()
            if (user.role != null) {
                roles!!.add(user.role!!.name)
            }
        }
        return roles
    }

}
