package com.onyx.network.transport.engine.impl

import com.onyx.network.transport.engine.AbstractTransportEngine
import com.onyx.network.transport.engine.PacketTransportEngine
import com.onyx.extension.common.catchAll

import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/**
 * Created by Tim Osborn on 2/12/17.
 *
 * This class was crated to have an option of an unsecured transport that took advantage
 * of the nice neat SSLEngine implementation.  This on the other hand is far more efficient
 * because it does not have to work with encryption.
 *
 * @since 1.2.0
 */
class UnsecuredPacketTransportEngine() : AbstractTransportEngine(), PacketTransportEngine {

    // Underlying channel
    var socketChannel: SocketChannel? = null


    /**
     * Constructor with socket channel
     * @param channel connection channel
     * @since 1.2.0
     */
    constructor(channel: SocketChannel):this() {
        this.socketChannel = channel
    }

    /**
     * Wrap a from Buffer and put it into another buffer.  It is wrapped with the meta data
     * that indicated how it is interpreted as a packet.
     *
     * In this case, we put the size before so that we know exactly how big it is.  That is all we need
     * to identify if we have the entire packet.
     *
     * @param fromBuffer Raw bytes from
     * @param toBuffer   Destination Buffer
     * @return Result of wrap.  Whether it was successful or we need more data
     * @throws SSLException Something bad happened.  Should not happen since the buffers should be setup.  In any case we do
     * not catch that
     * @since 1.2.0
     */
    @Throws(SSLException::class)
    override fun wrap(fromBuffer: ByteBuffer, toBuffer: ByteBuffer): SSLEngineResult {
        toBuffer.put(fromBuffer)
        return SSLEngineResult(SSLEngineResult.Status.OK, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, fromBuffer.limit(), fromBuffer.limit())
    }


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
    override fun unwrap(fromBuffer: ByteBuffer, toBuffer: ByteBuffer): SSLEngineResult {
        if (!fromBuffer.hasRemaining()) {
            return SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0)
        }

        val position = fromBuffer.position()
        val sizeOfPacket = fromBuffer.int
        fromBuffer.position(position)

        return if (sizeOfPacket <= fromBuffer.limit() - fromBuffer.position()) {
            for (i in 0 until sizeOfPacket)
                toBuffer.put(fromBuffer.get())
            SSLEngineResult(SSLEngineResult.Status.OK, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, sizeOfPacket, sizeOfPacket)
        } else {
            for (i in 0 until sizeOfPacket)
                toBuffer.put(fromBuffer)
            SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, sizeOfPacket, sizeOfPacket)
        }
    }

    /**
     * Close the outbound connection.
     *
     * @since 1.2.0
     */
    override fun closeOutbound() = catchAll {
        socketChannel!!.close()
    }

    /**
     * Get the maximum size of packets thrown over the network.  For SSL, that means 16k.  Not sure why that is the
     * golden number.  For unsecured, this can be adjusted.
     *
     * @since 1.2.0
     * @return Size of the network packet
     */
    override val packetSize: Int
        get() = DEFAULT_BUFFER_SIZE * 1024

    /**
     * Get the size of the application buffer.  This needs to be a little smaller than the packet size so it can account
     * for the difference between a wrapped packet and the raw bytes.
     *
     * @return Default size of the application buffer.
     *
     * @since 1.2.0
     */
    override val applicationSize: Int
        get() = packetSize

    companion object {
        private val DEFAULT_BUFFER_SIZE = 100 // In KB
    }
}
