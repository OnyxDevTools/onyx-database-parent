package com.onyx.client.base.engine;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 2/12/17.
 * <p>
 * This contract is meant to encapsulate the handling of partial packets.  If packets
 * are over the exceeded buffer amount or under, this is used to identify if
 * the packet contains the right stuff.
 * <p>
 * It was based off the SSLEngine so it contains much of its characteristics but, can be
 * used as a non secure transport.  The SSL Result objects are re-used in this interface.
 *
 * @since 1.2.0
 */
public interface PacketTransportEngine {

    /**
     * Get Handshake status
     *
     * @return The current handshake status
     * @since 1.2.0
     */
    SSLEngineResult.HandshakeStatus getHandshakeStatus();

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
    SSLEngineResult wrap(ByteBuffer fromBuffer, ByteBuffer toBuffer) throws SSLException;

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
    SSLEngineResult unwrap(ByteBuffer fromBuffer, ByteBuffer toBuffer) throws SSLException;

    /**
     * Return runnable for delegating hand shaking
     * @since 1.2.0
     * @return Thread if it applies
     */
    Runnable getDelegatedTask();

    /**
     * Close the inbound connection.  Awww snap, no soup for you
     *
     * @throws SSLException General exception occurred when closing the inbound socket.
     * @since 1.2.0
     */
    void closeInbound() throws SSLException;

    /**
     * Close the outbound connection.  Awww snap, no soup for you
     *
     * @since 1.2.0
     */
    void closeOutbound();

    /**
     * Is the inbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    boolean isInboundDone();

    /**
     * Is the outbound connection done throwing data at you
     * @return Whether it is all wrapped up
     * @since 1.2.0
     */
    boolean isOutboundDone();

    /**
     * Start the handshake process.  This is officiated on purpose.  Need more info.  Tough shit.
     * @throws SSLException Handshake did not go well :(  Nobody wants to be your friend.
     * @since 1.2.0
     */
    void beginHandshake() throws SSLException;

    /**
     * Get the maximum size of packets thrown over the network.  For SSL, that means 16k.  Not sure why that is the
     * golden number.  For unsecured, this can be adjusted.
     *
     * @since 1.2.0
     * @return Size of the network packet
     */
    int getPacketSize();

    /**
     * Get the size of the application buffer.  This needs to be a little smaller than the packet size so it can account
     * for the difference between a wrapped packet and the raw bytes.
     *
     * @return Default size of the application buffer.
     *
     * @since 1.2.0
     */
    int getApplicationSize();

}
