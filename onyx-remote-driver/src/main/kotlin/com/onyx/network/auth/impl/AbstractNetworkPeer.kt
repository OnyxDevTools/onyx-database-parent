package com.onyx.network.auth.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.NetworkBufferPool
import com.onyx.network.connection.Connection
import com.onyx.network.transport.data.RequestToken
import com.onyx.network.transport.engine.impl.UnsecuredPacketTransportEngine
import com.onyx.network.connection.ConnectionFactory
import com.onyx.network.transport.data.Message
import com.onyx.network.transport.data.Packet
import com.onyx.network.transport.data.toMessage
import com.onyx.network.serialization.impl.DefaultServerSerializer
import com.onyx.network.serialization.ServerSerializer
import com.onyx.network.ssl.impl.AbstractSSLPeer
import com.onyx.extension.common.Job
import com.onyx.extension.common.async
import com.onyx.extension.common.catchAll

import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

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
    @Volatile
    protected var active: Boolean = false

    // Listener or connection port
    var port = 8080

    // Serializer
    protected val serverSerializer: ServerSerializer = DefaultServerSerializer()

    // region Jobs

    protected var readJob:Job? = null

    /**
     * Start read queue.  This is to be overridden by extending class.  A client may want to implement a blocking queue
     * and a server would be a selection key.  That is why this is abstract.  The only requirement is that the daemon
     * is started and assigns the readJob.
     *
     * @since 2.0.0
     */
    abstract protected fun startReadQueue()

    /**
     * Stop the read daemon.
     */
    protected fun stopReadQueue() {
        catchAll { readJob?.cancel() }
        readJob = null
    }

    // endregion

    // region Read I/O

    /**
     * Read from a socket channel.  This will read and interpret the packets in order to decipher a message.
     * This method is setup to use use buffers that are given to a specific connection.  There are pools of buffers
     * used that are given by a round robin.
     *
     * @param socketChannel        Socket Channel to read data from.
     * @param connection Buffer and connection information
     * @since 1.2.0
     */
    protected fun read(socketChannel: SocketChannel, connection: Connection) {
        try {
            while(true) {
                val bytesRead = socketChannel.read(connection.readNetworkData)
                when {
                    bytesRead < 0 -> { closeConnection(socketChannel, connection); read@return }
                    bytesRead == 0 -> read@return
                    bytesRead > Message.PACKET_METADATA_SIZE -> {
                        connection.readNetworkData.flip()

                        loop@ while (true) {

                            val readApplicationData:ByteBuffer = NetworkBufferPool.allocate()
                            val result = connection.packetTransportEngine.unwrap(connection.readNetworkData, readApplicationData)
                            when (result.status) {
                                // Entire Packet
                                SSLEngineResult.Status.OK -> {
                                    readApplicationData.flip()
                                    readPacket(socketChannel, connection, readApplicationData)
                                }
                                // Partial Packet
                                SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                                    connection.readNetworkData.clear()
                                    NetworkBufferPool.recycle(readApplicationData)
                                    break@loop
                                }
                                // Packet with some change
                                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                                    connection.readNetworkData.clear()
                                    readApplicationData.flip()
                                    connection.readNetworkData.put(readApplicationData)
                                    NetworkBufferPool.recycle(readApplicationData)
                                    break@loop
                                }
                                SSLEngineResult.Status.CLOSED -> {
                                    closeConnection(socketChannel, connection)
                                    NetworkBufferPool.recycle(readApplicationData)
                                    break@loop
                                }
                                else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            closeConnection(socketChannel, connection)
        }
    }

    /**
     * Read a single packet and associate that to a message.  If the message aas the entire packet set start handling the
     * message
     *
     * @since 2.0.0 Changed to refactor out the serialization from the i/o layer
     */
    private fun readPacket(socketChannel: SocketChannel, connection: Connection, buffer: ByteBuffer) {
        val packet = Packet(buffer)
        // Find the message the packet belongs to
        val message = connection.messages.getOrPut(packet.messageId) {
            val message = Message(packet.messageId)
            message.numberOfPackets = buffer.short
            return@getOrPut message
        }
        message.packets.add(packet)
        packet.packetBuffer.rewind()

        // If all of the packets are accounted for, handle the message asynchronously
        if (message.numberOfPackets.toInt() == message.packets.count()) {
            connection.messages.remove(packet.messageId)
            async { try { handleMessage(socketChannel, connection, message) } catch (e:Exception) { failure(e) } }
        }
    }

    // endregion

    // region Write I/O

    /**
     * Write a message to the socket channel
     *
     * @param socketChannel        Socket Channel to write to
     * @param connection Connection Buffer Pool
     * @param request              Network request.
     * @since 1.2.0
     */
    protected fun write(socketChannel: SocketChannel, connection: Connection, request: RequestToken) {
        val buffer = serverSerializer.serialize(request, ByteBuffer.allocate(BufferPool.MEDIUM_BUFFER_SIZE))
        val message = buffer.toMessage(request)

        message.packets.forEach {
            writePacket(socketChannel, connection, it.packetBuffer)
        }
    }

    /**
     * Write a single packet to the socket channel.
     *
     *
     * This requires the packet to be less than 16kbs.  If it is larger, this will blow up.
     *
     * @param socketChannel        Socket Channel to write to
     * @param connection Socket Connection
     * @throws IOException Issue writing to the channel.
     * @since 1.2.0
     * @since 1.3.0 Altered to reuse write packets rather than application packet for performance
     */
    @Throws(IOException::class)
    private fun writePacket(socketChannel: SocketChannel, connection: Connection, packetBuffer: ByteBuffer?) {

        try {
            if (connection.packetTransportEngine is UnsecuredPacketTransportEngine) {
                while (packetBuffer!!.hasRemaining())
                    socketChannel.write(packetBuffer)
            } else {
                // My Net Data is guaranteed to only have 16k of data, so you should never get a UNDERFLOW or OVERFLOW
                connection.writeNetworkData.clear()

                // Wrap the data.  The wrapping and unwrapping determine how the packet is coded and how we know when the start
                // and stop of the packet.  The PacketTransportEngine encapsulates that information
                val result = connection.packetTransportEngine.wrap(packetBuffer!!, connection.writeNetworkData)

                // Handle Wrap response
                when (result.status) {
                    SSLEngineResult.Status.OK -> {
                        // Everything was ok.  We have a valid packet so, write it to the socket channel
                        connection.writeNetworkData.flip()
                        while (connection.writeNetworkData.hasRemaining()) {
                            val bytesWritten = socketChannel.write(connection.writeNetworkData)
                            if(bytesWritten < 0)
                                closeConnection(socketChannel, connection)
                        }
                    }
                    SSLEngineResult.Status.CLOSED -> {
                        closeConnection(socketChannel, connection)
                        return
                    }
                    else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                }
            }
        } finally {
            NetworkBufferPool.recycle(packetBuffer!!)
        }
    }

    // endregion

    // region Handshaking & SSL

    /**
     * Identify whether we should use SSL or not.  This is based on the ssl info being populated
     *
     * @return If the keystore file path is populated
     * @since 1.2.0
     */
    protected fun useSSL(): Boolean = this.sslKeystoreFilePath != null && this.sslKeystoreFilePath!!.isNotEmpty()

    /**
     * Perform SSL Handshake.  This will only be executed if it is using an SSLEngine.
     *
     * @param socketChannel        Socket Channel to perform handshake with
     * @param connection Buffer Pool tied to connection
     * @return Whether the handshake was successful
     * @throws IOException General IO Exception
     * @since 1.2.0
     */
    @Throws(IOException::class)
    protected fun doHandshake(socketChannel: SocketChannel, connection: Connection?): Boolean {
        if (connection == null)
            return false

        var result: SSLEngineResult
        var handshakeStatus: HandshakeStatus

        val writeHandshakeBuffer = NetworkBufferPool.allocate()
        val writeHandshakeApplicationBuffer = NetworkBufferPool.allocate()
        val readHandshakeData = NetworkBufferPool.allocate()
        val readHandshakeApplicationData = NetworkBufferPool.allocate()

        handshakeStatus = connection.packetTransportEngine.handshakeStatus
        try {
            loop@ while (handshakeStatus != HandshakeStatus.FINISHED && handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {
                when (handshakeStatus) {
                    SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                        if (socketChannel.read(readHandshakeData) < 0) {
                            if (connection.packetTransportEngine.isInboundDone && connection.packetTransportEngine.isOutboundDone)
                                return false
                            connection.packetTransportEngine.closeInbound()
                            connection.packetTransportEngine.closeOutbound()
                            connection.packetTransportEngine.handshakeStatus
                            break@loop
                        }
                        readHandshakeData.flip()
                        try {
                            result = connection.packetTransportEngine.unwrap(readHandshakeData, readHandshakeApplicationData)
                            readHandshakeData.compact()
                            handshakeStatus = result.handshakeStatus
                        } catch (sslException: SSLException) {
                            closeConnection(socketChannel, connection)
                            break@loop
                        }

                        when (result.status) {
                            SSLEngineResult.Status.OK -> { }
                            SSLEngineResult.Status.BUFFER_OVERFLOW -> { }
                            SSLEngineResult.Status.BUFFER_UNDERFLOW -> { }
                            SSLEngineResult.Status.CLOSED -> return if (connection.packetTransportEngine.isOutboundDone) {
                                false
                            } else {
                                closeConnection(socketChannel, connection)
                                break@loop
                            }
                            else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                        }
                    }
                    SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                        writeHandshakeBuffer.clear()
                        try {
                            result = connection.packetTransportEngine.wrap(writeHandshakeApplicationBuffer, writeHandshakeBuffer)
                            handshakeStatus = result.handshakeStatus
                        } catch (sslException: SSLException) {
                            closeConnection(socketChannel, connection)
                            break@loop
                        }

                        when (result.status) {
                            SSLEngineResult.Status.OK -> {
                                writeHandshakeBuffer.flip()
                                while (writeHandshakeBuffer.hasRemaining())
                                    socketChannel.write(writeHandshakeBuffer)
                            }
                            SSLEngineResult.Status.BUFFER_OVERFLOW -> throw SSLException("Buffer overflow occurred after a wrap during handshake.")
                            SSLEngineResult.Status.BUFFER_UNDERFLOW -> throw SSLException("Buffer underflow occurred after a wrap during handshake")
                            SSLEngineResult.Status.CLOSED -> try {
                                writeHandshakeBuffer.flip()
                                while (writeHandshakeBuffer.hasRemaining())
                                    socketChannel.write(writeHandshakeBuffer)
                                // At this point the handshake status will probably be NEED_UNWRAP so we make sure that readNetworkData is clear to read.
                                writeHandshakeBuffer.clear()
                            } catch (e: Exception) {
                                handshakeStatus = connection.packetTransportEngine.handshakeStatus
                            }

                            else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                        }
                    }
                    SSLEngineResult.HandshakeStatus.FINISHED -> { }
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> { }
                    else -> throw IllegalStateException("Invalid SSL status: " + handshakeStatus)
                }
            }
        } finally {
            NetworkBufferPool.recycle(readHandshakeData)
            NetworkBufferPool.recycle(readHandshakeApplicationData)
            NetworkBufferPool.recycle(writeHandshakeBuffer)
            NetworkBufferPool.recycle(writeHandshakeApplicationBuffer)
        }

        return true
    }

    // endregion

    // region Connection Termination

    /**
     * Close Connection
     *
     * @param socketChannel        Socket Channel to close
     * @param connection Buffer information.
     * @throws IOException General IO Exception
     */
    protected fun closeConnection(socketChannel: SocketChannel, connection: Connection) {
        connection.isAuthenticated = false
        catchAll { ConnectionFactory.recycle(connection) }
        catchAll { connection.packetTransportEngine.closeInbound() }
        catchAll { connection.packetTransportEngine.closeOutbound() }
        catchAll { socketChannel.close() }
    }

    // endregion

    // region Abstract Message Handling

    /**
     * Abstract method for handling a message.  Overwrite this as needed.  Pre requirements are that
     * the message should be in a formed message that should deserialize into a token and are in a meaningful token object.
     *
     * @param socketChannel        Socket Channel read from
     * @param connection Connection information containing buffer and thread info
     * @param message              Network message containing individual packets that make up the message
     * @since 1.2.0
     */
    abstract protected fun handleMessage(socketChannel: SocketChannel, connection: Connection, message: Message)

    /**
     * Exception handling.  Both the client and server need to override this so they can have their own custom handling.
     *
     * @param cause The underlying exception
     * @param token Message ID
     */
    abstract protected fun failure(cause: Exception, token: RequestToken? = null)

    // endregion

    companion object {
        val UNEXPECTED_EXCEPTION: Short = java.lang.Short.MAX_VALUE
        val PUSH_NOTIFICATION: Short = java.lang.Short.MIN_VALUE
    }
}
