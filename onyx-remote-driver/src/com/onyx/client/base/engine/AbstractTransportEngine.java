package com.onyx.client.base.engine;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

/**
 * Created by tosborn1 on 3/29/17.
 *
 * Abstract implementation of a transport engine
 */
public abstract class AbstractTransportEngine {

    /**
     * Get Handshake status
     *
     * @return The current handshake status
     * @since 1.2.0
     */
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() { return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING; }

    /**
     * Return runnable for delegating hand shaking
     * @since 1.2.0
     * @return Thread if it applies
     */
    public Runnable getDelegatedTask() { return null; }

    /**
     * Close the inbound connection.  Awww snap, no soup for you
     *
     * @throws SSLException General exception occurred when closing the inbound socket.
     * @since 1.2.0
     */
    public void closeInbound() throws SSLException{}

    /**
     * Is the inbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    public boolean isInboundDone() { return true; }

    /**
     * Is the outbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    public boolean isOutboundDone() { return  true; }

    /**
     * Start the handshake process.  This is officiated on purpose.  Need more info.  Tough shit.
     * @throws SSLException Handshake did not go well :(  Nobody wants to be your friend.
     * @since 1.2.0
     */
    public void beginHandshake() throws SSLException {}

}
