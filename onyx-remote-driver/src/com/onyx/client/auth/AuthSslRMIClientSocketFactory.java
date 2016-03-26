package com.onyx.client.auth;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.net.Socket;

/**
 * SSL Socket Client factory
 */
public class AuthSslRMIClientSocketFactory extends SslRMIClientSocketFactory {


    private volatile AuthData authData;

    /**
     * @param authData  User Authorization data
     */
    public void setAuthData(AuthData authData) {
        if (authData == null) {
            throw new NullPointerException("authData");
        }

        this.authData = authData;
    }

    /**
     * Create a client socket that is SSL and username secure
     * @param host
     * @param port
     * @return
     * @throws IOException
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = super.createSocket(host, port);
        new ClientSideSocketAuthorizationImpl(socket, authData).checkAuthorized();
        return socket;
    }
}
