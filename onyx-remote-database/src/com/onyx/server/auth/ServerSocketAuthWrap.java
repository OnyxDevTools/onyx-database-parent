package com.onyx.server.auth;

import com.onyx.client.auth.Authorize;

import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;

/**
 * Wrapper for a server socket so we may extend the functionality to include security such as authorization and SSL
 */
final class ServerSocketAuthWrap extends ServerSocket {

    private final ServerSocket sock;

    private final Authorize authorizer;

    /**
     * Constructor including socket and authorizer
     * @param sock Server Socket
     * @param authorizer Authentication provider instance
     * @throws IOException
     */
    public ServerSocketAuthWrap(ServerSocket sock, Authorize authorizer) throws IOException {
        this.sock = sock;

        if (authorizer == null) {
            throw new NullPointerException("authorizer");
        }
        this.authorizer = authorizer;
    }

    /**
     * Determines whether we should accept the socket connection or not
     * @return
     * @throws IOException
     */
    public Socket accept() throws IOException {
        Socket socket = sock.accept();
        new ServerSideSocketAuthorizationImpl(socket, authorizer).checkAuthorized();
        return socket;
    }

    /////////////////////////////////////////////////////////////////
    //
    //  Socket setter and getter methods
    //
    /////////////////////////////////////////////////////////////////

    public synchronized void setSoTimeout(int timeout) throws SocketException {
        sock.setSoTimeout(timeout);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        sock.setReuseAddress(on);
    }

    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        sock.setReceiveBufferSize(size);
    }

    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        sock.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    public boolean isClosed() {
        return sock.isClosed();
    }

    public boolean isBound() {
        return sock.isBound();
    }

    public synchronized int getSoTimeout() throws IOException {
        return sock.getSoTimeout();
    }

    public boolean getReuseAddress() throws SocketException {
        return sock.getReuseAddress();
    }

    public synchronized int getReceiveBufferSize() throws SocketException {
        return sock.getReceiveBufferSize();
    }

    public SocketAddress getLocalSocketAddress() {
        return sock.getLocalSocketAddress();
    }

    public int getLocalPort() {
        return sock.getLocalPort();
    }

    public InetAddress getInetAddress() {
        return sock.getInetAddress();
    }

    public ServerSocketChannel getChannel() {
        return sock.getChannel();
    }

    public void close() throws IOException {
        sock.close();
    }

    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        sock.bind(endpoint, backlog);
    }

    public void bind(SocketAddress endpoint) throws IOException {
        sock.bind(endpoint);
    }
}
