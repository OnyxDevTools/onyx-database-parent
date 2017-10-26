package com.onyx.network.auth.impl

import com.onyx.network.auth.AuthenticationManager
import com.onyx.entity.SystemUser
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.interactors.encryption.EncryptionInteractor

/**
 * Created by Tim Osborn on 2/13/17.
 *
 * Default implementation of the authentication manager
 */
class DefaultAuthenticationManager(private val persistenceManager: PersistenceManager, private val encryption: EncryptionInteractor) : AuthenticationManager {

    /**
     * Verify Account exists and has correct password
     * @param username User id
     * @param password Password
     * @throws InitializationException Thrown if account is not verified
     * @since 1.2.0
     */
    @Throws(InitializationException::class)
    override fun verify(username: String, password: String) {
        val user: SystemUser?
        user = try {
            persistenceManager.findById(SystemUser::class.java, username)
        } catch (e: OnyxException) {
            throw InitializationException(InitializationException.UNKNOWN_EXCEPTION, e)
        }

        if (user == null)
            throw InitializationException(InitializationException.INVALID_CREDENTIALS)
        if (user.password != encryption.encrypt(password))
            throw InitializationException(InitializationException.INVALID_CREDENTIALS)
    }
}
