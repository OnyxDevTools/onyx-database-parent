package com.onyx.client.auth;

import java.io.IOException;
import java.net.Socket;

/**
 * Socket Authorization base implementation class
 */
public abstract class SocketAuthorizationImpl {

    protected static final byte AUTH_SUCCEEDED = 0;
    protected static final byte AUTH_FAILED = 1;

    protected final Socket socket;
    protected boolean authorized;

    /**
     * Default constructor with socket
     * @param socket
     */
    public SocketAuthorizationImpl(Socket socket) {
        if (socket == null) {
            throw new NullPointerException("socket");
        }

        this.socket = socket;
    }

    /**
     * Method to be implemented to check authorized state
     */
    public abstract void checkAuthorized() throws IOException;

}