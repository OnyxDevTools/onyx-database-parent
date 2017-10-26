package com.onyx.network.transport.engine.impl

import com.onyx.network.transport.engine.AbstractTransportEngine
import com.onyx.network.transport.engine.PacketTransportEngine

import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException
import java.nio.ByteBuffer

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This class is used to wrap SSL packets.  It is just a wrapper that uses the SSLEngine implementation.
 *
 * @since 1.2.0
 */
class SecurePacketTransportEngine (private val sslEngine: SSLEngine) : AbstractTransportEngine(), PacketTransportEngine {

    /**
     * Get Handshake status
     *
     * @return The current handshake status
     * @since 1.2.0
     */
    override val handshakeStatus: SSLEngineResult.HandshakeStatus
        get() = sslEngine.handshakeStatus

    /**
     * Wrap a from Buffer and put it into another buffer.  It is wrapped with the meta data
     * that indicated how it is interpreted as a packet.
     *
     * @param fromBuffer Raw bytes from
     * @param toBuffer   Destination Buffer
     * @return Result of wrap.  Whether it was successful or we need more data
     * @throws SSLException Something bad happened.  Typically indicates some bad memory issues
     * @since 1.2.0
     */
    @Throws(SSLException::class)
    override fun wrap(fromBuffer: ByteBuffer, toBuffer: ByteBuffer): SSLEngineResult = sslEngine.wrap(fromBuffer, toBuffer)

    /**
     * Unwrap the byte buffer and have a nice clean packet buffer.
     *
     * @param fromBuffer Network buffer
     * @param toBuffer   Packet application buffer
     * @return The status whether it was successful or not.  Typically OK if successful or BUFFER_UNDERFLOW, if the information
     * was insufficient.  Buffer Overflow if the allocated destination buffer was not large enough.
     * That should not happen though.
     *
     * @throws SSLException Something went bad when working with buffers
     */
    @Throws(SSLException::class)
    override fun unwrap(fromBuffer: ByteBuffer, toBuffer: ByteBuffer): SSLEngineResult = sslEngine.unwrap(fromBuffer, toBuffer)

    /**
     * Close the outbound connection.  Awww snap, no soup for you
     *
     * @since 1.2.0
     */
    override fun closeOutbound() {
        sslEngine.closeOutbound()
    }

    /**
     * Close the inbound connection.  Awww snap, no soup for you
     *
     * @throws SSLException General exception occurred when closing the inbound socket.
     * @since 1.2.0
     */
    @Throws(SSLException::class)
    override fun closeInbound() {
        sslEngine.closeInbound()
    }

    /**
     * Get the maximum size of packets thrown over the network.  For SSL, that means 16k.  Not sure why that is the
     * golden number.  For unsecured, this can be adjusted.
     *
     * @since 1.2.0
     * @return Size of the network packet
     */
    override val packetSize: Int
        get() = sslEngine.session.packetBufferSize

    /**
     * Get the size of the application buffer.  This needs to be a little smaller than the packet size so it can account
     * for the difference between a wrapped packet and the raw bytes.
     *
     * @return Default size of the application buffer.
     *
     * @since 1.2.0
     */
    override val applicationSize: Int
        get() = sslEngine.session.applicationBufferSize

    /**
     * Is the inbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    override val isInboundDone: Boolean
        get() = sslEngine.isInboundDone

    /**
     * Is the outbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    override val isOutboundDone: Boolean
        get() = sslEngine.isOutboundDone

    /**
     * Start the handshake process.  This is officiated on purpose.  Need more info.  Tough shit.
     * @throws SSLException Handshake did not go well :(  Nobody wants to be your friend.
     * @since 1.2.0
     */
    @Throws(SSLException::class)
    override fun beginHandshake() {
        sslEngine.beginHandshake()
    }
}
