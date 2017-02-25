package com.onyx.client.auth;

import com.onyx.client.exception.RequestTimeoutException;
import com.onyx.exception.InitializationException;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This contract serves as an authentication / identity verification class
 */
public interface AuthenticationManager {

    /**
     * Verify the username and password are valid
     * @param username User id
     * @param password Password
     */
    @SuppressWarnings("RedundantThrows")
    void verify(String username, String password) throws InitializationException, RequestTimeoutException;
}
