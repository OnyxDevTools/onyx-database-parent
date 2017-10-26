package com.onyx.network.transport.engine

import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException

/**
 * Created by Tim Osborn on 3/29/17.
 *
 * Abstract implementation of a transport engine
 */
abstract class AbstractTransportEngine {

    /**
     * Get Handshake status
     *
     * @return The current handshake status
     * @since 1.2.0
     */
    open val handshakeStatus: SSLEngineResult.HandshakeStatus
        get() = SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING

    /**
     * Is the inbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    open val isInboundDone: Boolean
        get() = true

    /**
     * Is the outbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    open val isOutboundDone: Boolean
        get() = true

    /**
     * Close the inbound connection.
     *
     * @throws SSLException General exception occurred when closing the inbound socket.
     * @since 1.2.0
     */
    @Throws(SSLException::class)
    open fun closeInbound() { }

    /**
     * Start the handshake process.  This is officiated on purpose.  Need more info.  Tough shit.
     * @throws SSLException Handshake did not go well :(  Nobody wants to be your friend.
     * @since 1.2.0
     */
    @Throws(SSLException::class)
    open fun beginHandshake() {}

}
