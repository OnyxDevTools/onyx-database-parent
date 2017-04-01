package com.onyx.client.base.engine.impl;

import com.onyx.client.base.engine.AbstractTransportEngine;
import com.onyx.client.base.engine.PacketTransportEngine;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This class is used to wrap SSL packets.  It is just a wrapper that uses the SSLEngine implementation.
 *
 * @since 1.2.0
 */
public class SecurePacketTransportEngine extends AbstractTransportEngine implements PacketTransportEngine {

    private final SSLEngine sslEngine;

    /**
     * Constructor with underlying SSL Engine
     * @param sslEngine SSL Engine
     */
    public SecurePacketTransportEngine(SSLEngine sslEngine)
    {
        this.sslEngine = sslEngine;
    }

    /**
     * Get Handshake status
     *
     * @return The current handshake status
     * @since 1.2.0
     */
    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return sslEngine.getHandshakeStatus();
    }

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
    @Override
    public SSLEngineResult wrap(ByteBuffer fromBuffer, ByteBuffer toBuffer) throws SSLException {
        return sslEngine.wrap(fromBuffer, toBuffer);
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
    public SSLEngineResult unwrap(ByteBuffer fromBuffer, ByteBuffer toBuffer) throws SSLException{
        return sslEngine.unwrap(fromBuffer, toBuffer);
    }

    /**
     * Return runnable for delegating hand shaking
     * @since 1.2.0
     * @return Thread if it applies
     */
    @Override
    public Runnable getDelegatedTask() {
        return sslEngine.getDelegatedTask();
    }

    /**
     * Close the outbound connection.  Awww snap, no soup for you
     *
     * @since 1.2.0
     */
    @Override
    public void closeOutbound() {
        sslEngine.closeOutbound();
    }

    /**
     * Close the inbound connection.  Awww snap, no soup for you
     *
     * @throws SSLException General exception occurred when closing the inbound socket.
     * @since 1.2.0
     */
    @Override
    public void closeInbound() throws SSLException{
        sslEngine.closeInbound();
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
        return sslEngine.getSession().getPacketBufferSize();
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
        return sslEngine.getSession().getApplicationBufferSize();
    }

    /**
     * Is the inbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    @Override
    public boolean isInboundDone() {
        return sslEngine.isInboundDone();
    }

    /**
     * Is the outbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    @Override
    public boolean isOutboundDone() {
        return sslEngine.isOutboundDone();
    }

    /**
     * Start the handshake process.  This is officiated on purpose.  Need more info.  Tough shit.
     * @throws SSLException Handshake did not go well :(  Nobody wants to be your friend.
     * @since 1.2.0
     */
    @Override
    public void beginHandshake() throws SSLException
    {
        sslEngine.beginHandshake();
    }
}
