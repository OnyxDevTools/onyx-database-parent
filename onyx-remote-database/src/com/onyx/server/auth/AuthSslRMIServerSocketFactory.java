package com.onyx.server.auth;

import java.io.IOException;
import java.net.ServerSocket;
import javax.net.ssl.SSLContext;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import com.onyx.client.auth.Authorize;
/**
 * T
 */
public class AuthSslRMIServerSocketFactory extends SslRMIServerSocketFactory {

    private final Authorize authorizer;

    /**
     * Конструктор.
     *
     * @param enabledCipherSuites
     * @param enabledProtocols
     * @param needClientAuth
     * @param authorizer Авторизатор соединений.
     *
     * @throws IllegalArgumentException
     *
     * @see SslRMIServerSocketFactory#SslRMIServerSocketFactory(String[], String[], boolean)
     */
    public AuthSslRMIServerSocketFactory(String[] enabledCipherSuites, String[] enabledProtocols, boolean needClientAuth, Authorize authorizer) throws IllegalArgumentException {
        super(enabledCipherSuites, enabledProtocols, needClientAuth);

        if (authorizer == null) {
            throw new NullPointerException("authorizer");
        }
        this.authorizer = authorizer;
    }

    /**
     * Конструктор.
     *
     * @param enabledCipherSuites
     * @param enabledProtocols
     * @param needClientAuth
     * @param authorizer Авторизатор соединений.
     *
     * @throws IllegalArgumentException
     *
     * @see SslRMIServerSocketFactory#SslRMIServerSocketFactory(String[], String[], boolean)
     */
    public AuthSslRMIServerSocketFactory(SSLContext context, String[]enabledCipherSuites, String[] enabledProtocols, boolean needClientAuth, Authorize authorizer) throws IllegalArgumentException {
        super(context, enabledCipherSuites, enabledProtocols, needClientAuth);

        if (authorizer == null) {
            throw new NullPointerException("authorizer");
        }
        this.authorizer = authorizer;
    }
    /**
     * Конструктор.
     *
     * @param authorizer Авторизатор соединений.
     */
    public AuthSslRMIServerSocketFactory(Authorize authorizer) {
        super();

        if (authorizer == null) {
            throw new NullPointerException("authorizer");
        }
        this.authorizer = authorizer;
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        return new ServerSocketAuthWrap(super.createServerSocket(port), authorizer);
    }

}
