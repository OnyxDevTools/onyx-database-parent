package com.onyx.client.base.engine.impl;

import com.onyx.client.base.engine.AbstractTransportEngine;
import com.onyx.client.base.engine.PacketTransportEngine;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This class was crated to have an option of an unsecured transport that took advantage
 * of the nice neat SSLEngine implementation.  This on the other hand is far more efficient
 * because it does not have to work with encryption.
 *
 * @since 1.2.0
 */
public class UnsecuredPacketTransportEngine extends AbstractTransportEngine implements PacketTransportEngine {

    private static final int DEFAULT_BUFFER_SIZE = (100*1024); // 100 KB

    // Underlying channel
    private SocketChannel socketChannel;

    /**
     * Constructor
     * @since 1.2.0
     */
    public UnsecuredPacketTransportEngine()
    {

    }

    /**
     * Constructor with socket channel
     * @param channel connection channel
     * @since 1.2.0
     */
    public UnsecuredPacketTransportEngine(SocketChannel channel)
    {
        this.socketChannel = channel;
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
     *                      not catch that
     * @since 1.2.0
     */
    @Override
    public SSLEngineResult wrap(ByteBuffer fromBuffer, ByteBuffer toBuffer) throws SSLException {
        int size = fromBuffer.limit();
        toBuffer.putInt(size);
        toBuffer.put(fromBuffer);

        return new SSLEngineResult(SSLEngineResult.Status.OK, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, fromBuffer.limit() + Integer.BYTES, fromBuffer.limit() + Integer.BYTES);
    }


    /**
     * Unwrap the byte buffer and have a nice clean packet buffer.
     *
     * @param fromBuffer Network buffer
     * @param toBuffer   Packet application buffer
     * @return The status whether it was successful or not.  Typically OK if successful or BUFFER_UNDERFLOW, if the information
     *         was insufficient.  Buffer Overflow if the allocated destination buffer was not large enough.
     *         That should not happen though.
     *
     * @throws SSLException Something went bad when working with buffers
     */
    @Override
    public SSLEngineResult unwrap(ByteBuffer fromBuffer, ByteBuffer toBuffer) throws SSLException {
        int fromBufferStartingPosition = fromBuffer.position();
        int size = fromBuffer.getInt();

        // We do not have the entire packet.  Go back read some more from the socket and try again
        if(fromBuffer.limit() - fromBuffer.position() < size)
        {
            fromBuffer.position(fromBuffer.position()-Integer.BYTES);
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, fromBuffer.limit(), Integer.BYTES);
        }

        // Copy only the packet buffer bytes.  Leave everything else
        try {
            for (int i = 0; i < size; i++)
                toBuffer.put(fromBuffer.get());
        } catch (BufferUnderflowException e)
        {
            // Packet buffer was not big enough.  Try again next round.
            fromBuffer.position(fromBufferStartingPosition);
            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, fromBuffer.limit(), Integer.BYTES);
        }

        // All Good
        return new SSLEngineResult(SSLEngineResult.Status.OK, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, fromBuffer.limit(), fromBuffer.limit());
    }

    /**
     * Close the outbound connection.  Awww snap, no soup for you
     *
     * @since 1.2.0
     */
    @Override
    public void closeOutbound() {
        try {
            socketChannel.close();
        } catch (IOException ignore) {
        }
    }

    /**
     * Get the maximum size of packets thrown over the network.  For SSL, that means 16k.  Not sure why that is the
     * golden number.  For unsecured, this can be adjusted.
     *
     * @since 1.2.0
     * @return Size of the network packet
     */
    @Override
    public int getPacketSize()
    {
        return DEFAULT_BUFFER_SIZE;
    }

    /**
     * Get the size of the application buffer.  This needs to be a little smaller than the packet size so it can account
     * for the difference between a wrapped packet and the raw bytes.
     *
     * @return Default size of the application buffer.
     *
     * @since 1.2.0
     */
    @Override
    public int getApplicationSize()
    {
        return DEFAULT_BUFFER_SIZE + 4; // Add the additional identification bytes
    }

    /**
     * Setter for socket channel
     * @param socketChannel Socket Channel
     */
    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }
}
