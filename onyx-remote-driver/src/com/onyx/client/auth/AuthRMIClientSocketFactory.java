package com.onyx.client.auth;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client socket factory for RMI implementation that handles authorization
 *
 * @see AuthRMIClientSocketFactory
 */
public class AuthRMIClientSocketFactory implements RMIClientSocketFactory, Serializable {

    // Map containing all host authorization information  key = host and value is auth data
    private static final Map<String, AuthData> hostAuthData = new ConcurrentHashMap<String, AuthData> ();

    /**
     * @param host Host data.
     * @param authData User authorization information containing username and password
     */
    public static void setHostAuthData(String host, AuthData authData) {
        if (host == null) {
            throw new NullPointerException("host");
        }
        if (authData == null) {
            throw new NullPointerException("authData");
        }

        AuthRMIClientSocketFactory.hostAuthData.put(host, authData);
    }

    /**
     * Socket factory override that creates a socket
     * @param host
     * @param port
     * @return
     * @throws IOException
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);

        AuthData authData = hostAuthData.get(host);
        if (authData == null) {
            throw new SocketAuthorizationFailedException("No authentication data for host " + host);
        }
        new ClientSideSocketAuthorizationImpl(socket, authData).checkAuthorized();

        return socket;
    }
}
