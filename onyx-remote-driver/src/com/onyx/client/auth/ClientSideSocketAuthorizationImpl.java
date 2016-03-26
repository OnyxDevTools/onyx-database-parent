package com.onyx.client.auth;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Client Authorization Implementation
 */
public final class ClientSideSocketAuthorizationImpl extends SocketAuthorizationImpl {

    private final AuthData authData;

    public ClientSideSocketAuthorizationImpl(Socket socket, AuthData authData) {
        super(socket);

        if (authData == null) {
            throw new NullPointerException("authData");
        }
        this.authData = authData;
    }

    /**
     * Authorize client information
     * @throws IOException
     */
    @Override
    public void checkAuthorized() throws IOException {
        if (authorized) {
            return;
        }

        // Write Authorization data
        final DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(authData.username);
        dos.writeUTF(authData.password);
        dos.flush();

        // read response from socket
        int authResponse = socket.getInputStream().read();
        if (authResponse == AUTH_SUCCEEDED) {
            authorized = true;
        } else {
            socket.close();
            throw new SocketAuthorizationFailedException("Invalid Credentials");
        }
    }
}
