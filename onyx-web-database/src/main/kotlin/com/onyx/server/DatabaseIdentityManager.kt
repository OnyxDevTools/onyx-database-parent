package com.onyx.server

import com.onyx.entity.SystemUser
import com.onyx.exception.OnyxException
import com.onyx.persistence.manager.PersistenceManager
import io.undertow.security.idm.Account
import io.undertow.security.idm.Credential
import io.undertow.security.idm.IdentityManager
import io.undertow.security.idm.PasswordCredential

/**
 * This class is responsible for performing the account validation for http basic auth.
 *
 * This is an undertow implementation
 */
class DatabaseIdentityManager(private val persistenceManager: PersistenceManager) : IdentityManager {

    override fun verify(account: Account): Account = account
    override fun verify(credential: Credential): Account? = null

    override fun verify(id: String, credential: Credential): Account? {
        val account = getAccount(id)
        return if (account != null && verifyCredential(account, credential)) {
            account
        } else null

    }

    private fun verifyCredential(account: Account?, credential: Credential): Boolean {
        if (credential is PasswordCredential) {
            val password = credential.password
            val sentPassword = String(password)

            return account != null && sentPassword == (account as DatabaseUserAccount).password

        }
        return false
    }

    private fun getAccount(id: String): Account? {
        return try {
            val user = persistenceManager.findById<SystemUser>(SystemUser::class.java, id) ?: return null
            DatabaseUserAccount(user)
        } catch (e: OnyxException) {
            null
        }
    }

}