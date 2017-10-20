package com.onyx.client.auth

import com.onyx.client.exception.RequestTimeoutException
import com.onyx.exception.InitializationException

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This contract serves as an authentication / identity verification class
 */
interface AuthenticationManager {

    /**
     * Verify the username and password are valid
     * @param username User id
     * @param password Password
     */
    @Throws(InitializationException::class, RequestTimeoutException::class)
    fun verify(username: String, password: String)
}
