package com.onyx.server.auth;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import com.onyx.client.auth.AuthData;
import com.onyx.client.auth.Authorize;
import com.onyx.client.auth.SocketAuthorizationImpl;
import com.onyx.client.auth.SocketAuthorizationFailedException;

/**
 * Server Socket Authorization
 */
public final class ServerSideSocketAuthorizationImpl extends SocketAuthorizationImpl {

    private final Authorize authorizer;

    /**
     * Default constructor
     * @param socket
     * @param authorizer
     */
    public ServerSideSocketAuthorizationImpl(Socket socket, Authorize authorizer) {
        super(socket);

        if (authorizer == null) {
            throw new NullPointerException("authorizer");
        }
        this.authorizer = authorizer;
    }

    /**
     * Check to ensure socket is authorized
     * @throws IOException
     */
    @Override
    public void checkAuthorized() throws IOException {

        // Already authorized.
        if (authorized) {
            return;
        }

        // Read Credentials
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        String login = dis.readUTF();
        String password = dis.readUTF();

        final AuthData authData = new AuthData(login, password);
        if (authorizer.authorize(authData)) {
            // Write success response
            dos.write(AUTH_SUCCEEDED);
            authorized = true;
        } else {
            // Write back failure
            dos.write(AUTH_FAILED);
            dos.flush();
            socket.close();
            throw new SocketAuthorizationFailedException("Invalid Credentials");
        }
    }
}
