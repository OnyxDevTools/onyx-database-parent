package com.onyx.client

import com.onyx.buffer.BufferPool
import com.onyx.buffer.NetworkBufferPool
import com.onyx.client.base.ConnectionProperties
import com.onyx.client.base.RequestToken
import com.onyx.client.connection.ConnectionFactory
import com.onyx.client.serialization.DefaultServerSerializer
import com.onyx.client.serialization.ServerSerializer
import com.onyx.extension.common.catchAll
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel

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
    @Volatile protected var active: Boolean = false

    // Listener or connection port
    var port = 8080

    // Serializer
    protected val serverSerializer: ServerSerializer = DefaultServerSerializer()

    private var writeQueue:Channel<() -> Unit>? = null
    private var writeJob:Deferred<Unit>? = null
    private var readQueue:Channel<() -> Unit>? = null
    protected var readJob:Deferred<Unit>? = null

    protected fun startWriteQueue() {
        writeQueue = Channel()
        writeJob = async {
            while (active) {
                writeQueue?.receive()?.invoke()
            }
        }
    }

    protected fun stopWriteQueue() {
        catchAll { writeJob?.cancel() }
        catchAll { writeQueue?.close() }
        writeQueue = null
        writeJob = null
    }

    abstract protected fun startReadQueue()

    protected fun stopReadQueue() {
        catchAll { readJob?.cancel() }
        readJob = null
    }

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
        try {
            if(!socketChannel.isConnected || !socketChannel.isOpen)
                return
                val bytesRead = socketChannel.read(connectionProperties.readNetworkData)
                when {
                    bytesRead < 0 -> { closeConnection(socketChannel, connectionProperties); read@return }
                    bytesRead > Message.PACKET_METADATA_SIZE -> {
                        connectionProperties.readNetworkData.flip()

                        loop@ while (socketChannel.isConnected && socketChannel.isOpen && bytesRead > 0) {
                            if(!socketChannel.isConnected || !socketChannel.isOpen)
                                return

                            val readApplicationData:ByteBuffer = NetworkBufferPool.allocate()
                            val result = connectionProperties.packetTransportEngine.unwrap(connectionProperties.readNetworkData, readApplicationData)
                            when (result.status) {
                                // Entire Packet
                                SSLEngineResult.Status.OK -> {
                                    readApplicationData.flip()
                                    readPacket(socketChannel, connectionProperties, readApplicationData)
                                }
                                // Partial Packet
                                SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                                    connectionProperties.readNetworkData.clear()
                                    NetworkBufferPool.recycle(readApplicationData)
                                    break@loop
                                }
                                // Packet with some change
                                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                                    connectionProperties.readNetworkData.clear()
                                    readApplicationData.flip()
                                    connectionProperties.readNetworkData.put(readApplicationData)
                                    NetworkBufferPool.recycle(readApplicationData)
                                    break@loop
                                }
                                SSLEngineResult.Status.CLOSED -> {
                                    closeConnection(socketChannel, connectionProperties)
                                    NetworkBufferPool.recycle(readApplicationData)
                                    break@loop
                                }
                                else -> throw IllegalStateException("Invalid SSL status: " + result.status)
                            }
                        }
                    }
                }
        } catch (e: IOException) {
            closeConnection(socketChannel, connectionProperties)
        }
    }

    private fun readPacket(socketChannel: SocketChannel, connectionProperties: ConnectionProperties, buffer: ByteBuffer) {
        val packet = Packet(buffer)
        val message = connectionProperties.messages.getOrPut(packet.messageId) {
            val message = Message(packet.messageId)
            message.numberOfPackets = buffer.short
            return@getOrPut message
        }
        message.packets.add(packet)
        packet.packetBuffer.rewind()

        if (message.numberOfPackets.toInt() == message.packets.count()) {
            connectionProperties.messages.remove(packet.messageId)
            async { handleMessage(socketChannel, connectionProperties, message) }
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
    private fun writePacket(socketChannel: SocketChannel, connectionProperties: ConnectionProperties, packetBuffer: ByteBuffer?) {

        // My Net Data is guaranteed to only have 16k of data, so you should never get a UNDERFLOW or OVERFLOW
        connectionProperties.writeNetworkData.clear()

        // Wrap the data.  The wrapping and unwrapping determine how the packet is coded and how we know when the start
        // and stop of the packet.  The PacketTransportEngine encapsulates that information
        val result = connectionProperties.packetTransportEngine.wrap(packetBuffer, connectionProperties.writeNetworkData)

        // Handle Wrap response
        when (result.status) {
            SSLEngineResult.Status.OK -> {
                // Everything was ok.  We have a valid packet so, write it to the socket channel
                connectionProperties.writeNetworkData.flip()
                while (connectionProperties.writeNetworkData.hasRemaining())
                    socketChannel.write(connectionProperties.writeNetworkData)
            }
            SSLEngineResult.Status.CLOSED -> {
                closeConnection(socketChannel, connectionProperties)
                return
            }
            else -> throw IllegalStateException("Invalid SSL status: " + result.status)
        }
    }

    /**
     * Write a message to the socket channel
     *
     * @param socketChannel        Socket Channel to write to
     * @param connectionProperties ConnectionProperties Buffer Pool
     * @param message              Serializable message
     * @since 1.2.0
     */
    protected fun write(socketChannel: SocketChannel, connectionProperties: ConnectionProperties, obj: RequestToken) {
        async {
            val buffer = serverSerializer.serialize(obj, ByteBuffer.allocate(BufferPool.MEDIUM_BUFFER_SIZE))
            val message = buffer.toMessage(obj)

            writeQueue?.send {
                if(socketChannel.isConnected && socketChannel.isOpen) {
                    message.packets.forEach {
                        if(socketChannel.isConnected && socketChannel.isOpen) {
                            writePacket(socketChannel, connectionProperties, it.packetBuffer)
                        }
                        NetworkBufferPool.recycle(it.packetBuffer)
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

        val writeHandshakeBuffer = NetworkBufferPool.allocate()
        val writeHandshakeApplicationBuffer = NetworkBufferPool.allocate()
        val readHandshakeData = NetworkBufferPool.allocate()
        val readHandshakeApplicationData = NetworkBufferPool.allocate()

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
    abstract protected fun handleMessage(socketChannel: SocketChannel, connectionProperties: ConnectionProperties, message: Message)

    /**
     * Close ConnectionProperties
     *
     * @param socketChannel        Socket Channel to close
     * @param connectionProperties Buffer information.
     * @throws IOException General IO Exception
     */
    protected fun closeConnection(socketChannel: SocketChannel, connectionProperties: ConnectionProperties) {
        connectionProperties.isAuthenticated = false
        catchAll { ConnectionFactory.recycle(connectionProperties) }
        catchAll { connectionProperties.packetTransportEngine.closeInbound() }
        catchAll { connectionProperties.packetTransportEngine.closeOutbound() }
        catchAll { socketChannel.close() }
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
        val UNEXPECTED_EXCEPTION: Short = java.lang.Short.MAX_VALUE
        val PUSH_NOTIFICATION: Short = java.lang.Short.MIN_VALUE
    }
}
