package com.onyx.server.auth;

import com.onyx.client.auth.Authorize;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

/**
 * The purpose of this class is to ensure the socket is secure and requires authentication prior to use
 */
public class AuthRMIServerSocketFactory implements RMIServerSocketFactory, Serializable {

    private final Authorize authorizer;

    /**
     * AuthRMIServerSocketFactory.  Default Constructor
     *
     * @param authorizer Implementation of authorization method
     */
    public AuthRMIServerSocketFactory(Authorize authorizer) {
        super();

        if (authorizer == null) {
            throw new NullPointerException("authorizer");
        }
        this.authorizer = authorizer;
    }

    /**
     *
     * Overridden method to create a server socket with a port
     * @param port
     * @return
     * @throws IOException
     */
    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        return new ServerSocketAuthWrap(new ServerSocket(port), authorizer);
    }

}
