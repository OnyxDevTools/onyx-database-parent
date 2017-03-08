package com.onyx.client;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.client.base.ConnectionProperties;
import com.onyx.client.base.RequestToken;
import com.onyx.client.exception.ServerReadException;
import com.onyx.client.exception.ServerWriteException;
import com.onyx.client.serialization.DefaultServerSerializer;
import com.onyx.client.serialization.ServerSerializer;
import com.onyx.exception.BufferingException;
import com.onyx.exception.InitializationException;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by Tim Osborn 02/13/2017
 * <p>
 * This class contains the base responsibility of the network communication for the
 * server and the client
 *
 * @since 1.2.0
 * <p>
 * It has been in order to remove the dependency on 3rd party libraries and improve performance.
 */
public abstract class AbstractCommunicationPeer extends AbstractSSLPeer {

    // Whether or not the i/o server is active
    protected volatile boolean active;

    // Listener or connection port
    protected int port = 8080;

    // Serializer
    final protected ServerSerializer serverSerializer = new DefaultServerSerializer();

    // Packet indicators
    protected static final byte SINGLE_PACKET = (byte) 0;
    private static final byte MULTI_PACKET_START = (byte) 1;
    private static final byte MULTI_PACKET_MIDDLE = (byte) 2;
    private static final byte MULTI_PACKET_STOP = (byte) 3;

    private static final int MAX_PACKET_SIZE = 16000;
    private static final int MULTI_PACKET_BUFFER_ALLOCATION = MAX_PACKET_SIZE * 3; //50 KB

    /**
     * Read from a socket channel.  This will read and interpret the packets in order to decipher a message.
     * This method is setup to use use buffers that are given to a specific connectionProperties.  There are pools of buffers
     * used that are given by a round robin.
     *
     * @param socketChannel        Socket Channel to read data from.
     * @param connectionProperties Buffer and connectionProperties information
     * @since 1.2.0
     */
    protected void read(SocketChannel socketChannel, ConnectionProperties connectionProperties) {
        connectionProperties.isReading = true;
        try {
            boolean exitReadLoop = false;

            // If the data is in multiple packets, this is used to combine the values
            ByteBuffer readMultiPacketData = null;

            while (!exitReadLoop && active) {
                connectionProperties.readNetworkData.clear();

                // Handle the remainder of the buffer.  This is to be used in the next cycle of reading for messages
                connectionProperties.handleConnectionRemainder();

                try {
                    // Read from the socket channel
                    int bytesRead;
                    try {
                        if (socketChannel.socket() == null) {
                            closeConnection(socketChannel, connectionProperties);
                            return;
                        }
                        bytesRead = socketChannel.read(connectionProperties.readNetworkData);
                        if (bytesRead > 0) {

                            // Iterate through the network data
                            connectionProperties.readNetworkData.flip();
                            while (connectionProperties.readNetworkData.hasRemaining() && active) {

                                // Attempt Unwrap the packet. This can be done via a SSLEngineImpl or an unsecured packetTransportEngine.
                                // Don't let the SSLEngineResult fool you.  This was used as a convenience because SSL already had
                                // this feature built out.
                                connectionProperties.readApplicationData.clear();
                                SSLEngineResult result = connectionProperties.packetTransportEngine.unwrap(connectionProperties.readNetworkData, connectionProperties.readApplicationData);
                                switch (result.getStatus()) {
                                    // Packet was read successfully
                                    case OK:
                                        connectionProperties.readApplicationData.flip();

                                        // Handle a packet
                                        byte packetType = connectionProperties.readApplicationData.get();

                                        // It is a single packet.  Yay, we got what we were looking for and it was small enough to fit into a single pack
                                        if (packetType == SINGLE_PACKET) {
                                            handleMessage(packetType, socketChannel, connectionProperties, connectionProperties.readApplicationData);
                                            exitReadLoop = true;
                                        }
                                        // This is the start of a larger packet that is too big to fit onto a single buffer
                                        // Since that is the case, we will combine multiple reads onto a single buffer.
                                        // This buffer is a throw away since we want to keep memory management sane.
                                        else if (packetType == MULTI_PACKET_START) {
                                            readMultiPacketData = BufferStream.allocate(MULTI_PACKET_BUFFER_ALLOCATION); // Allocate using the Buffer Stream since it encapsulates the endian and potential use of recycled buffers
                                            readMultiPacketData.put(connectionProperties.readApplicationData);
                                        }
                                        // A Packet in the middle
                                        else if (packetType == MULTI_PACKET_MIDDLE) {
                                            // Check to see if the buffer is large enough
                                            readMultiPacketData = ensureBufferCapacity(readMultiPacketData, connectionProperties.readApplicationData.limit());
                                            readMultiPacketData.put(connectionProperties.readApplicationData);
                                        } else if (packetType == MULTI_PACKET_STOP) {
                                            readMultiPacketData = ensureBufferCapacity(readMultiPacketData, connectionProperties.readApplicationData.limit());

                                            // Handle the message buffer and send process it
                                            readMultiPacketData.put(connectionProperties.readApplicationData);
                                            readMultiPacketData.flip();
                                            handleMessage(packetType, socketChannel, connectionProperties, readMultiPacketData);
                                            exitReadLoop = true;
                                            readMultiPacketData = null;
                                        }
                                        break;
                                    case BUFFER_OVERFLOW:
                                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                                    case BUFFER_UNDERFLOW:
                                        connectionProperties.readOverflowData.put(connectionProperties.readNetworkData);
                                        break;
                                    case CLOSED:
                                        try {
                                            closeConnection(socketChannel, connectionProperties);
                                        } catch (IOException ignore) {
                                        }
                                        return;
                                    default:
                                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                                }
                            }
                        } else if (bytesRead < 0) {
                            handleEndOfStream(socketChannel, connectionProperties);
                            return;
                        }
                    } catch (ClosedChannelException closed) {
                        handleEndOfStream(socketChannel, connectionProperties);
                    }

                    // Added a wait so that we can hold off to see the rest
                    // of the packet loaded

                    if (!exitReadLoop) {
                        LockSupport.parkNanos(100);
                    }

                } catch (IOException exception) {
                    exception.printStackTrace();
                    // Write the exception back to the client
                    final ServerReadException readException = new ServerReadException(exception);
                    write(socketChannel, connectionProperties, new RequestToken(Short.MAX_VALUE, readException));  // Create a new token and use Short.MAX_VALUE as a placeholder
                }
            }
        } finally {
            connectionProperties.isReading = false;
        }
    }

    /**
     * Write a single packet to the socket channel.
     * <p>
     * This requires the packet to be less than 16kbs.  If it is larger, this will blow up.
     *
     * @param socketChannel        Socket Channel to write to
     * @param connectionProperties Socket ConnectionProperties
     * @throws IOException Issue writing to the channel.
     * @since 1.2.0
     */

    private void writePacket(SocketChannel socketChannel, ConnectionProperties connectionProperties) throws IOException {

        while (connectionProperties.writeApplicationData.hasRemaining()) {

            // My Net Data is guaranteed to only have 16k of data, so you should never get a UNDERFLOW or OVERFLOW
            connectionProperties.writeNetworkData.clear();

            // Wrap the data.  The wrapping and unwrapping determine how the packet is coded and how we know when the start
            // and stop of the packet.  The PacketTransportEngine encapsulates that information
            SSLEngineResult result = connectionProperties.packetTransportEngine.wrap(connectionProperties.writeApplicationData, connectionProperties.writeNetworkData);

            // Handle Wrap response
            switch (result.getStatus()) {
                case OK:
                    // Everything was ok.  We have a valid packet so, write it to the socket channel
                    connectionProperties.writeNetworkData.flip();
                    while (connectionProperties.writeNetworkData.hasRemaining()) {
                        socketChannel.write(connectionProperties.writeNetworkData);
                    }
                    break;
                case BUFFER_OVERFLOW:
                    throw new SSLException("Socket Channel Buffer Overflow.  You are trying to attempt to write more tha 16kb to the socket");
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Socket Channel Buffer Underflow.  Network buffer was not large enough.");
                case CLOSED:
                    closeConnection(socketChannel, connectionProperties);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }
    }

    /**
     * Unsure the buffer is large enough to handle the additional bytes.  If not, resize it
     *
     * @param buffer     Buffer to check
     * @param additional Additional bytes needed
     * @return Resized or existing buffer based on needs
     * @since 1.2.0
     */
    private ByteBuffer ensureBufferCapacity(ByteBuffer buffer, int additional) {
        if (buffer.capacity() < buffer.position() + additional) {
            ByteBuffer temporaryBuffer = BufferStream.allocate(buffer.capacity() + additional);
            buffer.flip();
            temporaryBuffer.put(buffer);
            BufferStream.recycle(buffer); // Be sure to recycle so we can use another time
            buffer = temporaryBuffer;
        }

        return buffer;
    }

    /**
     * Write a message to the socket channel
     *
     * @param socketChannel        Socket Channel to write to
     * @param connectionProperties ConnectionProperties Buffer Pool
     * @param message              Serializable message
     * @since 1.2.0
     */
    protected void write(SocketChannel socketChannel, ConnectionProperties connectionProperties, Serializable message) {


        ByteBuffer buffer = BufferStream.allocate(connectionProperties.readApplicationData.capacity());
        buffer.position(1);

        try {
            buffer = serverSerializer.serialize((BufferStreamable) message, buffer);
        } catch (BufferingException exception) {
            if (message instanceof RequestToken) {
                RequestToken requestToken = ((RequestToken) message);
                if (!requestToken.reTry) {
                    requestToken.reTry = true;
                    requestToken.packet = new ServerWriteException(exception);
                    write(socketChannel, connectionProperties, requestToken);
                } else {
                    failure(requestToken, exception);
                }
            }
        }

        final ByteBuffer messageBuffer = buffer;

        connectionProperties.writeThread.execute(() -> {
            // Clear and add a placeholder byte for the packet type
            connectionProperties.writeApplicationData.clear();
            try {
                // Check to see if the message buffer is the write application buffer.  If it is not,
                // it indicates the original buffer was resized.  If that is the case, it is too large
                // to fit onto a single packet
                if (messageBuffer.limit() >= MAX_PACKET_SIZE) {

                    boolean firstPacket = true;
                    byte packetType = MULTI_PACKET_START;

                    messageBuffer.position(1);

                    while (messageBuffer.hasRemaining()) {
                        connectionProperties.writeApplicationData.clear();

                        // Identify if it is a leading or trailing packet
                        if (!firstPacket && messageBuffer.remaining() > MAX_PACKET_SIZE)
                            packetType = MULTI_PACKET_MIDDLE;
                        else if (!firstPacket)
                            packetType = MULTI_PACKET_STOP;

                        connectionProperties.writeApplicationData.put(packetType);

                        int remaining = messageBuffer.remaining();
                        for (int k = 0; k < remaining && k < MAX_PACKET_SIZE; k++)
                            connectionProperties.writeApplicationData.put(messageBuffer.get());

                        connectionProperties.writeApplicationData.flip();

                        // Write the app buffer as a packet
                        writePacket(socketChannel, connectionProperties);
                        firstPacket = false;
                    }
                }
                // It is small enough to fit onto a single buffer
                else {
                    BufferStream.recycle(connectionProperties.writeApplicationData);
                    connectionProperties.writeApplicationData = messageBuffer;
                    connectionProperties.writeApplicationData.put(SINGLE_PACKET); // Put packet type
                    connectionProperties.writeApplicationData.rewind();
                    writePacket(socketChannel, connectionProperties);
                }
            } catch (Exception exception) {
                if (message instanceof RequestToken) {
                    RequestToken requestToken = ((RequestToken) message);
                    if (!requestToken.reTry
                            && requestToken.packet != null
                            && !(exception instanceof ClosedChannelException)) {
                        requestToken.reTry = true;
                        requestToken.packet = new ServerWriteException(exception);
                        write(socketChannel, connectionProperties, requestToken);
                    } else {
                        exception = (exception instanceof ClosedChannelException) ? new InitializationException(InitializationException.CONNECTION_EXCEPTION) : new ServerWriteException(exception);
                        failure(requestToken, exception);
                    }
                }
            }
        });
    }

    /**
     * Perform SSL Handshake.  This will only be executed if it is using an SSLEngine.
     *
     * @param socketChannel        Socket Channel to perform handshake with
     * @param connectionProperties Buffer Pool tied to connectionProperties
     * @return Whether the handshake was successful
     * @throws IOException General IO Exception
     * @since 1.2.0
     */
    protected boolean doHandshake(SocketChannel socketChannel, ConnectionProperties connectionProperties) throws IOException {

        if (connectionProperties == null)
            return false;

        SSLEngineResult result;
        HandshakeStatus handshakeStatus;

        ByteBuffer writeHandshakeBuffer = BufferStream.allocate(connectionProperties.writeApplicationData.capacity());
        ByteBuffer writeHandshakeApplicationBuffer = BufferStream.allocate(connectionProperties.writeApplicationData.capacity());
        ByteBuffer readHandshakeData = BufferStream.allocate(connectionProperties.writeNetworkData.capacity());
        ByteBuffer readHandshakeApplicationData = BufferStream.allocate(connectionProperties.writeApplicationData.capacity());

        handshakeStatus = connectionProperties.packetTransportEngine.getHandshakeStatus();
        try {
            while (handshakeStatus != HandshakeStatus.FINISHED
                    && handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {
                switch (handshakeStatus) {
                    case NEED_UNWRAP:
                        if (socketChannel.read(readHandshakeData) < 0) {
                            if (connectionProperties.packetTransportEngine.isInboundDone() && connectionProperties.packetTransportEngine.isOutboundDone()) {
                                return false;
                            }
                            try {
                                connectionProperties.packetTransportEngine.closeInbound();
                            } catch (SSLException e) {
                                // Ignore
                            }
                            connectionProperties.packetTransportEngine.closeOutbound();
                            // After closeOutbound the packetTransportEngine will be set to WRAP state, in order to try to send a close message to the client.
                            handshakeStatus = connectionProperties.packetTransportEngine.getHandshakeStatus();
                            break;
                        }
                        readHandshakeData.flip();
                        try {
                            result = connectionProperties.packetTransportEngine.unwrap(readHandshakeData, readHandshakeApplicationData);
                            readHandshakeData.compact();
                            handshakeStatus = result.getHandshakeStatus();
                        } catch (SSLException sslException) {
                            connectionProperties.packetTransportEngine.closeOutbound();
                            handshakeStatus = connectionProperties.packetTransportEngine.getHandshakeStatus();
                            break;
                        }
                        switch (result.getStatus()) {
                            case OK:
                                break;
                            case BUFFER_OVERFLOW:
                                break;
                            case BUFFER_UNDERFLOW:
                                break;
                            case CLOSED:
                                if (connectionProperties.packetTransportEngine.isOutboundDone()) {
                                    return false;
                                } else {
                                    connectionProperties.packetTransportEngine.closeOutbound();
                                    handshakeStatus = connectionProperties.packetTransportEngine.getHandshakeStatus();
                                    break;
                                }
                            default:
                                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                        break;
                    case NEED_WRAP:
                        writeHandshakeBuffer.clear();
                        try {
                            result = connectionProperties.packetTransportEngine.wrap(writeHandshakeApplicationBuffer, writeHandshakeBuffer);
                            handshakeStatus = result.getHandshakeStatus();
                        } catch (SSLException sslException) {
                            connectionProperties.packetTransportEngine.closeOutbound();
                            handshakeStatus = connectionProperties.packetTransportEngine.getHandshakeStatus();
                            break;
                        }
                        switch (result.getStatus()) {
                            case OK:
                                writeHandshakeBuffer.flip();
                                while (writeHandshakeBuffer.hasRemaining()) {
                                    socketChannel.write(writeHandshakeBuffer);
                                }
                                break;
                            case BUFFER_OVERFLOW:
                                throw new SSLException("Buffer overflow occurred after a wrap during handshake.");
                            case BUFFER_UNDERFLOW:
                                throw new SSLException("Buffer underflow occurred after a wrap during handshake");
                            case CLOSED:
                                try {
                                    writeHandshakeBuffer.flip();
                                    while (writeHandshakeBuffer.hasRemaining()) {
                                        socketChannel.write(writeHandshakeBuffer);
                                    }
                                    // At this point the handshake status will probably be NEED_UNWRAP so we make sure that readNetworkData is clear to read.
                                    writeHandshakeBuffer.clear();
                                } catch (Exception e) {
                                    handshakeStatus = connectionProperties.packetTransportEngine.getHandshakeStatus();
                                }
                                break;
                            default:
                                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                        break;
                    case NEED_TASK:
                        Runnable task;
                        while ((task = connectionProperties.packetTransportEngine.getDelegatedTask()) != null) {
                            connectionProperties.writeThread.execute(task);
                        }
                        handshakeStatus = connectionProperties.packetTransportEngine.getHandshakeStatus();
                        break;
                    case FINISHED:
                        break;
                    case NOT_HANDSHAKING:
                        break;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + handshakeStatus);
                }
            }
        } finally {
            BufferStream.recycle(readHandshakeData);
            BufferStream.recycle(readHandshakeApplicationData);
            BufferStream.recycle(writeHandshakeBuffer);
            BufferStream.recycle(writeHandshakeApplicationBuffer);
        }

        return true;

    }

    /**
     * Abstract method for handling a message.  Overwrite this as needed.  Pre requirements are that
     * the message should be in a formed message that should deserialize into a token and are in a meaningful token object.
     *
     * @param packetType           Indicates if the packet can fit into 1 buffer or multiple
     * @param socketChannel        Socket Channel read from
     * @param connectionProperties ConnectionProperties information containing buffer and thread info
     * @param buffer               ByteBuffer containing message
     * @since 1.2.0
     */
    protected abstract void handleMessage(byte packetType, SocketChannel socketChannel, ConnectionProperties connectionProperties, ByteBuffer buffer);

    /**
     * Close ConnectionProperties
     *
     * @param socketChannel        Socket Channel to close
     * @param connectionProperties Buffer information.
     * @throws IOException General IO Exception
     */
    void closeConnection(SocketChannel socketChannel, ConnectionProperties connectionProperties) throws IOException {
        connectionProperties.packetTransportEngine.closeOutbound();
        socketChannel.close();
    }

    /**
     * Handle the end of a stream.  Handle it by closing the inbound and outbound connections
     *
     * @param socketChannel        Socket channel
     * @param connectionProperties Buffer information
     */
    protected void handleEndOfStream(SocketChannel socketChannel, ConnectionProperties connectionProperties) {
        try {
            connectionProperties.packetTransportEngine.closeInbound();
        } catch (Exception e) {
            // Ignore
        }
        try {
            closeConnection(socketChannel, connectionProperties);
        } catch (IOException ignore) {
        }
    }

    /**
     * Identify whether we should use SSL or not.  This is based on the ssl info being populated
     *
     * @return If the keystore file path is populated
     * @since 1.2.0
     */
    protected boolean useSSL() {
        return this.sslKeystoreFilePath != null && this.sslKeystoreFilePath.length() > 0;
    }

    /**
     * Exception handling.  Both the client and server need to override this so they can have their own custom handling.
     *
     * @param token Original request
     * @param e     The underlying exception
     */
    protected abstract void failure(RequestToken token, Exception e);
}
