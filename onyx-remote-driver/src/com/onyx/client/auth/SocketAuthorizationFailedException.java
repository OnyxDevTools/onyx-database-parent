package com.onyx.client.auth;

import java.io.IOException;

/**
 * Exception class for failed socket authorization
 */
public class SocketAuthorizationFailedException extends IOException {

    /**
     * Default Constructor
     * @param message Error message
     */
    public SocketAuthorizationFailedException(String message) {
        super(message);
    }

}
