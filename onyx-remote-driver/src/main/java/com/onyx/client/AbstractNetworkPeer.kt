package com.onyx.client

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStreamable
import com.onyx.client.base.ConnectionProperties
import com.onyx.client.base.RequestToken
import com.onyx.client.exception.SerializationException
import com.onyx.client.exception.ServerClosedException
import com.onyx.client.exception.ServerWriteException
import com.onyx.client.serialization.DefaultServerSerializer
import com.onyx.client.serialization.ServerSerializer
import com.onyx.exception.BufferingException
import com.onyx.exception.InitializationException
import com.onyx.extension.common.catchAll
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay

import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLException
import java.io.IOException
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

/**
 * Created by Tim Osborn 02/13/2017
 *
 *
 * This class contains the base responsibility of the network communication for the
 * server and the client
 *
 * @since 1.2.0
 *
 *
 * It has been in order to remove the dependency on 3rd party libraries and improve performance.
 */
abstract class AbstractNetworkPeer : AbstractSSLPeer() {

    // Whether or not the i/o server is active
    @Volatile protected var active: Boolean = false

    // Listener or connection port
    var port = 8080

    // Serializer
    protected val serverSerializer: ServerSerializer = DefaultServerSerializer()

    /**
     * Read from a socket channel.  This will read and interpret the packets in order to decipher a message.
     * This method is setup to use use buffers that are given to a specific connectionProperties.  There are pools of buffers
     * used that are given by a round robin.
     *
     * @param socketChannel        Socket Channel to read data from.
     * @param connectionProperties Buffer and connectionProperties information
     * @since 1.2.0
     */
    protected fun read(socketChannel: SocketChannel, connectionProperties: ConnectionProperties) {
        connectionProperties.isReading = true
        var readIterations = 0
        try {
            var exitReadLoop = false

            // If the data is in multiple packets, this is used to combine the values
            var readMultiPacketData: ByteBuffer? = null

            while (!exitReadLoop && active) {
                connectionProperties.readNetworkData.clear()

                // Handle the remainder of the buffer.  This is to be used in the next cycle of reading for messages
                connectionProperties.handleConnectionRemainder()

                try {
                    // Read from the socket channel
                    val bytesRead: Int
                    try {
                        if (socketChannel.socket() == null) {
                            closeConnection(socketChannel, connectionProperties)
                            return
                        }
                        bytesRead = socketChannel.read(connectionProperties.readNetworkData)
                        if (bytesRead > 0) {
                            readIterations = 0
                            // Iterate through the network data
                            connectionProperties.readNetworkData.flip()
                            while (connectionProperties.readNetworkData.hasRemaining() && active) {

                                // Attempt Unwrap the packet. This can be done via a SSLEngineImpl or an unsecured packetTransportEngine.
                                // Don't let the SSLEngineResult fool you.  This was used as a convenience because SSL already had
                                // this feature built out.
                                connectionProperties.readApplicationData.clear()
                                val result = connectionProperties.packetTransportEngine.unwrap(connectionProperties.readNetworkData, connectionProperties.readApplicationData)
                                when (result.status) {
                                // Packet was read successfully
                                    SSLEngineResult.Status.OK -> {
                                        connectionProperties.readApplicationData.flip()

                                        // Handle a packet
                                        val packetType = connectionProperties.readApplicationData.get()

                                        // It is a single packet.  Yay, we got what we were looking for and it was small enough to fit into a single pack
                                        when (packetType) {
                                            SINGLE_PACKET -> {
                                                handleMessage(packetType, socketChannel, connectionProperties, connectionProperties.readApplicationData)
                                                exitReadLoop = true
                                            }
                                            MULTI_PACKET_START -> {
                                                readMultiPacketData = BufferPool.allocate(MULTI_PACKET_BUFFER_ALLOCATION) // Allocate using the Buffer Stream since it encapsulates the endian and potential use of recycled buffers
                                                readMultiPacketData.put(connectionProperties.readApplicationData)
                                            }
                                            MULTI_PACKET_MIDDLE -> {
                                                // Check to see if the buffer is large enough
                                                readMultiPacketData = ensureBufferCapacity(readMultiPacketData, connectionProperties.readApplicationData.limit())
                                                readMultiPacketData.put(connectionProperties.readApplicationData)
                                            }
                                            MULTI_PACKET_STOP -> {
                                                readMultiPacketData = ensureBufferCapacity(readMultiPacketData, connectionProperties.readApplicationData.limit())

                                                // Handle the message buffer and send process it
                                                readMultiPacketData.put(connectionProperties.readApplicationData)
                                                readMultiPacketData.flip()
                                                handleMessage(packetType, socketChannel, connectionProperties, readMultiPacketData)
                                                exitReadLoop = true
                                                readMultiPacketData = null
                                            }
                                        }
                                    }
                                    SSLEngineResult.Status.BUFFER_OVERFLOW -> throw IllegalStateException("Invalid SSL status: " + result.status)
                                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> connectionProperties.readOverflowData.put(connectionProperties.readNetworkData)
                                    SSLEngineResult.Status.CLOSED -> {
                                        closeConnection(socketChannel, connectionProperties)
                                        return
                                    }
                                    else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                                }
                            }
                        } else if (bytesRead < 0) {
                            handleEndOfStream(socketChannel, connectionProperties)
                            return
                        } else {
                            readIterations++
                            if (readIterations > MAX_READ_ITERATIONS_BEFORE_GIVING_UP) {
                                connectionProperties.readOverflowData.clear()
                                connectionProperties.readApplicationData.clear()
                                connectionProperties.readNetworkData.clear()
                                exitReadLoop = true
                            }
                        }
                    } catch (closed: ClosedChannelException) {
                        handleEndOfStream(socketChannel, connectionProperties)
                    }

                    // Added a wait so that we can hold off to see the rest
                    // of the packet loaded

                    if (!exitReadLoop) {
                        LockSupport.parkNanos(100000)
                    }

                } catch (exception: IOException) {
                    exception.printStackTrace()
                    closeConnection(socketChannel, connectionProperties)
                }

            }
        } finally {
            connectionProperties.isReading = false
        }
    }

    /**
     * Write a single packet to the socket channel.
     *
     *
     * This requires the packet to be less than 16kbs.  If it is larger, this will blow up.
     *
     * @param socketChannel        Socket Channel to write to
     * @param connectionProperties Socket ConnectionProperties
     * @throws IOException Issue writing to the channel.
     * @since 1.2.0
     * @since 1.3.0 Altered to reuse write packets rather than application packet for performance
     */

    @Throws(IOException::class)
    private fun writePacket(socketChannel: SocketChannel, connectionProperties: ConnectionProperties, overrideBuffer: ByteBuffer?) {

        val bufferToWrite = overrideBuffer ?: connectionProperties.writeApplicationData
        while (bufferToWrite.hasRemaining()) {

            // My Net Data is guaranteed to only have 16k of data, so you should never get a UNDERFLOW or OVERFLOW
            connectionProperties.writeNetworkData.clear()

            // Wrap the data.  The wrapping and unwrapping determine how the packet is coded and how we know when the start
            // and stop of the packet.  The PacketTransportEngine encapsulates that information
            val result = connectionProperties.packetTransportEngine.wrap(bufferToWrite, connectionProperties.writeNetworkData)

            // Handle Wrap response
            when (result.status) {
                SSLEngineResult.Status.OK -> {
                    // Everything was ok.  We have a valid packet so, write it to the socket channel
                    connectionProperties.writeNetworkData.flip()
                    while (connectionProperties.writeNetworkData.hasRemaining())
                        socketChannel.write(connectionProperties.writeNetworkData)
                }
                SSLEngineResult.Status.BUFFER_OVERFLOW -> throw SSLException("Socket Channel Buffer Overflow.  You are trying to attempt to write more tha 16kb to the socket")
                SSLEngineResult.Status.BUFFER_UNDERFLOW -> throw SSLException("Socket Channel Buffer Underflow.  Network buffer was not large enough.")
                SSLEngineResult.Status.CLOSED -> {
                    closeConnection(socketChannel, connectionProperties)
                    return
                }
                else -> throw IllegalStateException("Invalid SSL status: " + result.status)
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
    private fun ensureBufferCapacity(buffer: ByteBuffer?, additional: Int): ByteBuffer {
        var returnValue = buffer
        if (returnValue!!.capacity() < returnValue.position() + additional) {
            val temporaryBuffer = BufferPool.allocate(returnValue.capacity() + additional)
            returnValue.flip()
            temporaryBuffer.put(returnValue)
            BufferPool.recycle(returnValue) // Be sure to recycle so we can use another time
            returnValue = temporaryBuffer
        }

        return returnValue
    }

    /**
     * Write a message to the socket channel
     *
     * @param socketChannel        Socket Channel to write to
     * @param connectionProperties ConnectionProperties Buffer Pool
     * @param message              Serializable message
     * @since 1.2.0
     */
    protected fun write(socketChannel: SocketChannel, connectionProperties: ConnectionProperties, message: Serializable) {

        var buffer = BufferPool.allocate(SERIALIZATION_BUFFER_SIZE)
        buffer.position(1)
        buffer = serverSerializer.serialize(message as BufferStreamable, buffer)

        val messageBuffer = buffer

        async(connectionProperties.writeThread) {
            // Clear and add a placeholder byte for the packet type
            try {
                // Check to see if the message buffer is the write application buffer.  If it is not,
                // it indicates the original buffer was resized.  If that is the case, it is too large
                // to fit onto a single packet
                if (messageBuffer.limit() >= MAX_PACKET_SIZE) {

                    connectionProperties.writeApplicationData.clear()

                    var firstPacket = true
                    var packetType = MULTI_PACKET_START

                    messageBuffer.position(1)

                    while (messageBuffer.hasRemaining()) {
                        connectionProperties.writeApplicationData.clear()

                        // Identify if it is a leading or trailing packet
                        if (!firstPacket && messageBuffer.remaining() > MAX_PACKET_SIZE)
                            packetType = MULTI_PACKET_MIDDLE
                        else if (!firstPacket)
                            packetType = MULTI_PACKET_STOP

                        connectionProperties.writeApplicationData.put(packetType)

                        val remaining = messageBuffer.remaining()
                        var k = 0
                        while (k < remaining && k < MAX_PACKET_SIZE) {
                            connectionProperties.writeApplicationData.put(messageBuffer.get())
                            k++
                        }

                        connectionProperties.writeApplicationData.flip()

                        // Write the app buffer as a packet
                        writePacket(socketChannel, connectionProperties, null)
                        firstPacket = false
                    }
                } else {
                    messageBuffer.put(SINGLE_PACKET) // Put packet type
                    messageBuffer.rewind()
                    writePacket(socketChannel, connectionProperties, messageBuffer)
                    BufferPool.recycle(messageBuffer)
                }
            } catch (exception: Exception) {
                if (message is RequestToken) {
                    if (!message.reTry
                            && message.packet != null
                            && exception !is ClosedChannelException
                            && exception !is ServerClosedException) {
                        message.reTry = true
                        message.packet = ServerWriteException(exception)
                        write(socketChannel, connectionProperties, message)
                    } else {
                        failure(message, if (exception is ClosedChannelException) InitializationException(InitializationException.CONNECTION_EXCEPTION) else ServerWriteException(exception))
                    }
                }
            }
        }
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
    @Throws(IOException::class)
    protected fun doHandshake(socketChannel: SocketChannel, connectionProperties: ConnectionProperties?): Boolean {
        if (connectionProperties == null)
            return false

        var result: SSLEngineResult
        var handshakeStatus: HandshakeStatus

        val writeHandshakeBuffer            = BufferPool.allocate(connectionProperties.writeApplicationData.capacity())
        val writeHandshakeApplicationBuffer = BufferPool.allocate(connectionProperties.writeApplicationData.capacity())
        val readHandshakeData               = BufferPool.allocate(connectionProperties.writeNetworkData.capacity())
        val readHandshakeApplicationData    = BufferPool.allocate(connectionProperties.writeApplicationData.capacity())

        handshakeStatus = connectionProperties.packetTransportEngine.handshakeStatus
        try {
            loop@ while (handshakeStatus != HandshakeStatus.FINISHED && handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {
                when (handshakeStatus) {
                    SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                        if (socketChannel.read(readHandshakeData) < 0) {
                            if (connectionProperties.packetTransportEngine.isInboundDone && connectionProperties.packetTransportEngine.isOutboundDone)
                                return false
                            connectionProperties.packetTransportEngine.closeInbound()
                            connectionProperties.packetTransportEngine.closeOutbound()
                            connectionProperties.packetTransportEngine.handshakeStatus
                            break@loop
                        }
                        readHandshakeData.flip()
                        try {
                            result = connectionProperties.packetTransportEngine.unwrap(readHandshakeData, readHandshakeApplicationData)
                            readHandshakeData.compact()
                            handshakeStatus = result.handshakeStatus
                        } catch (sslException: SSLException) {
                            connectionProperties.packetTransportEngine.closeOutbound()
                            connectionProperties.packetTransportEngine.handshakeStatus
                            break@loop
                        }

                        when (result.status) {
                            SSLEngineResult.Status.OK -> { }
                            SSLEngineResult.Status.BUFFER_OVERFLOW -> { }
                            SSLEngineResult.Status.BUFFER_UNDERFLOW -> { }
                            SSLEngineResult.Status.CLOSED -> if (connectionProperties.packetTransportEngine.isOutboundDone) {
                                return false
                            } else {
                                connectionProperties.packetTransportEngine.closeOutbound()
                                connectionProperties.packetTransportEngine.handshakeStatus
                                break@loop
                            }
                            else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                        }
                    }
                    SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                        writeHandshakeBuffer.clear()
                        try {
                            result = connectionProperties.packetTransportEngine.wrap(writeHandshakeApplicationBuffer, writeHandshakeBuffer)
                            handshakeStatus = result.handshakeStatus
                        } catch (sslException: SSLException) {
                            connectionProperties.packetTransportEngine.closeOutbound()
                            connectionProperties.packetTransportEngine.handshakeStatus
                            break@loop
                        }

                        when (result.status) {
                            SSLEngineResult.Status.OK -> {
                                writeHandshakeBuffer.flip()
                                while (writeHandshakeBuffer.hasRemaining()) {
                                    socketChannel.write(writeHandshakeBuffer)
                                }
                            }
                            SSLEngineResult.Status.BUFFER_OVERFLOW -> throw SSLException("Buffer overflow occurred after a wrap during handshake.")
                            SSLEngineResult.Status.BUFFER_UNDERFLOW -> throw SSLException("Buffer underflow occurred after a wrap during handshake")
                            SSLEngineResult.Status.CLOSED -> try {
                                writeHandshakeBuffer.flip()
                                while (writeHandshakeBuffer.hasRemaining()) {
                                    socketChannel.write(writeHandshakeBuffer)
                                }
                                // At this point the handshake status will probably be NEED_UNWRAP so we make sure that readNetworkData is clear to read.
                                writeHandshakeBuffer.clear()
                            } catch (e: Exception) {
                                handshakeStatus = connectionProperties.packetTransportEngine.handshakeStatus
                            }

                            else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                        }
                    }
                    SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                        while(true) {
                            val task: Runnable? = connectionProperties.packetTransportEngine.delegatedTask ?: break
                            async(connectionProperties.writeThread) { task!!.run() }
                        }
                        handshakeStatus = connectionProperties.packetTransportEngine.handshakeStatus
                    }
                    SSLEngineResult.HandshakeStatus.FINISHED -> { }
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> { }
                    else -> throw IllegalStateException("Invalid SSL status: " + handshakeStatus)
                }
            }
        } finally {
            BufferPool.recycle(readHandshakeData)
            BufferPool.recycle(readHandshakeApplicationData)
            BufferPool.recycle(writeHandshakeBuffer)
            BufferPool.recycle(writeHandshakeApplicationBuffer)
        }

        return true
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
    protected abstract fun handleMessage(packetType: Byte, socketChannel: SocketChannel, connectionProperties: ConnectionProperties, buffer: ByteBuffer)

    /**
     * Close ConnectionProperties
     *
     * @param socketChannel        Socket Channel to close
     * @param connectionProperties Buffer information.
     * @throws IOException General IO Exception
     */
    protected fun closeConnection(socketChannel: SocketChannel, connectionProperties: ConnectionProperties) {
        catchAll {
            connectionProperties.packetTransportEngine.closeOutbound()
            socketChannel.close()
        }
    }

    /**
     * Handle the end of a stream.  Handle it by closing the inbound and outbound connections
     *
     * @param socketChannel        Socket channel
     * @param connectionProperties Buffer information
     */
    protected fun handleEndOfStream(socketChannel: SocketChannel, connectionProperties: ConnectionProperties) {
        catchAll {
            connectionProperties.packetTransportEngine.closeInbound()
        }
        closeConnection(socketChannel, connectionProperties)
    }

    /**
     * Identify whether we should use SSL or not.  This is based on the ssl info being populated
     *
     * @return If the keystore file path is populated
     * @since 1.2.0
     */
    protected fun useSSL(): Boolean = this.sslKeystoreFilePath != null && this.sslKeystoreFilePath!!.isNotEmpty()

    /**
     * Exception handling.  Both the client and server need to override this so they can have their own custom handling.
     *
     * @param token Original request
     * @param e     The underlying exception
     */
    protected abstract fun failure(token: RequestToken, e: Exception)

    companion object {

        // Packet indicators
        val SINGLE_PACKET = 0.toByte()
        private val MULTI_PACKET_START = 1.toByte()
        private val MULTI_PACKET_MIDDLE = 2.toByte()
        private val MULTI_PACKET_STOP = 3.toByte()

        val MAX_PACKET_SIZE = 16000
        val SERIALIZATION_BUFFER_SIZE = 256
        private val MULTI_PACKET_BUFFER_ALLOCATION = MAX_PACKET_SIZE * 3 //50 KB
        private val MAX_READ_ITERATIONS_BEFORE_GIVING_UP = 200


        val UNEXPECTED_EXCEPTION:Short = java.lang.Short.MAX_VALUE
        val PUSH_NOTIFICATION:Short = java.lang.Short.MIN_VALUE
    }
}
