package com.onyx.network.auth.impl

import com.onyx.buffer.BufferPool
import com.onyx.exception.InitializationException
import com.onyx.network.connection.Connection
import com.onyx.network.transport.data.RequestToken
import com.onyx.network.serialization.impl.DefaultServerSerializer
import com.onyx.network.serialization.ServerSerializer
import com.onyx.extension.common.Job
import com.onyx.extension.common.async
import com.onyx.extension.common.catchAll
import com.onyx.extension.common.delay
import com.onyx.extension.withBuffer
import com.onyx.network.ssl.SSLPeer

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ByteChannel
import java.nio.channels.ClosedChannelException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

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
abstract class NetworkPeer : SSLPeer {

    override var protocol = "TLSv1.2"
    override var sslStorePassword: String? = null
    override var sslKeystoreFilePath: String? = null
    override var sslKeystorePassword: String? = null
    override var sslTrustStoreFilePath: String? = null
    override var sslTrustStorePassword: String? = null

    // Whether or not the i/o server is active
    @Volatile
    protected var active: Boolean = false

    // Listener or connection port
    var port = 8080

    // Serializer
    private val serverSerializer: ServerSerializer = DefaultServerSerializer()

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

    private val packetSizeBuffer = ByteBuffer.allocateDirect(Integer.BYTES).order(ByteOrder.BIG_ENDIAN)

    /**
     * Read from a socket channel.  This will read and interpret the packets in order to decipher a message.
     * This method is setup to use use buffers that are given to a specific connection.  There are pools of buffers
     * used that are given by a round robin.
     *
     * @param connection Buffer and connection information
     * @since 1.2.0
     */
    protected fun read(connection: Connection) {
        try {
            // Read an existing packet
            if(connection.lastReadPacket != null) {
                existingPacketLoop@while (connection.lastReadPacket != null && connection.lastReadPacket!!.hasRemaining()) {
                    val bytesRead = connection.socketChannel.read(connection.lastReadPacket)
                    when {
                        bytesRead < 0 -> {
                            closeConnection(connection); read@ return
                        }
                        bytesRead == 0 -> read@ return
                        else -> {
                            if (!connection.lastReadPacket!!.hasRemaining()) {

                                connection.lastReadPacket!!.flip()
                                val packet = connection.lastReadPacket!!
                                connection.lastReadPacket = null

                                async {
                                    withBuffer(packet) {
                                        var token: RequestToken? = null
                                        try {
                                            token = serverSerializer.deserialize(it)
                                            handleMessage(connection.socketChannel, connection, token)
                                        } catch (e: Exception) {
                                            failure(e, token)
                                        }
                                    }
                                }
                                break@existingPacketLoop
                            }
                        }
                    }
                }
            }

            // Start a read for a new packet
            while (true) {
                packetSizeBuffer.clear()
                var bytesRead = connection.socketChannel.read(packetSizeBuffer)
                when {
                    bytesRead < 0 -> {
                        closeConnection(connection); read@ return
                    }
                    bytesRead == 0 -> read@ return
                    bytesRead >= Integer.BYTES -> {
                        packetSizeBuffer.flip()
                        val packetSize = packetSizeBuffer.int
                        connection.lastReadPacket = BufferPool.allocateAndLimit(packetSize)
                        while (connection.lastReadPacket!!.hasRemaining()) {
                            bytesRead = connection.socketChannel.read(connection.lastReadPacket)
                            if (bytesRead < 0)
                                closeConnection(connection)
                            else if (bytesRead == 0) {
                                return
                            }
                        }
                        connection.lastReadPacket!!.flip()
                        val packet = connection.lastReadPacket!!
                        connection.lastReadPacket = null
                        async {
                            withBuffer(packet) {
                                var token: RequestToken? = null
                                try {
                                    token = serverSerializer.deserialize(it)
                                    handleMessage(connection.socketChannel, connection, token!!)
                                } catch (e: Exception) {
                                    failure(e, token)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            closeConnection(connection)
        }
    }

    // endregion

    // region Write I/O

    private val maxWriteIterations = 500000

    /**
     * Write data to a socket and handle write issues that could cause connection loss
     *
     * @param buffer Buffer to write
     * @param connection Socket connection
     */
    private fun writeToSocket(buffer: ByteBuffer, connection: Connection) {
        var iterations = 0
        while (buffer.hasRemaining()) {
            try {
                val bytesWritten = connection.socketChannel.write(buffer)
                if (bytesWritten < 0 || iterations >= maxWriteIterations) {
                    closeConnection(connection)
                    throw InitializationException(InitializationException.CONNECTION_EXCEPTION)
                }
                iterations++
            } catch (e: ClosedChannelException) {
                closeConnection(connection)
                throw InitializationException(InitializationException.CONNECTION_EXCEPTION)
            }
        }
    }

    /**
     * Write a message to the socket channel
     *
     * @param connection Connection Buffer Pool
     * @param request              Network request.
     * @since 1.2.0
     */
    protected fun write(connection: Connection, request: RequestToken) {
        val buffer = serverSerializer.serialize(request)
        val sizeBuffer = BufferPool.allocateAndLimit(Integer.BYTES)
        sizeBuffer.putInt(buffer.limit())
        sizeBuffer.flip()

        synchronized(connection) {
            withBuffer(sizeBuffer) { writeToSocket(sizeBuffer, connection) }
            withBuffer(buffer) { writeToSocket(buffer, connection) }
        }
    }


    // endregion

    // region Connection Termination

    /**
     * Close Connection
     *
     * @param connection Buffer information.
     * @throws IOException General IO Exception
     */
    protected fun closeConnection(connection: Connection) {
        connection.isAuthenticated = false
        catchAll { connection.socketChannel.close() }
    }

    // endregion

    // region Abstract Message Handling

    /**
     * Abstract method for handling a message.  Overwrite this as needed.  Pre requirements are that
     * the message should be in a formed message that should deserialize into a token and are in a meaningful token object.
     *
     * @param socketChannel        Socket Channel read from
     * @param connection Connection information containing buffer and thread info
     * @param message              Network message containing token information
     * @since 1.2.0
     */
    abstract protected fun handleMessage(socketChannel: ByteChannel, connection: Connection, message: RequestToken)

    /**
     * Exception handling.  Both the client and server need to override this so they can have their own custom handling.
     *
     * @param cause The underlying exception
     * @param token Message ID
     */
    abstract protected fun failure(cause: Exception, token: RequestToken? = null)

    // endregion

    /**
     * Identify whether we should use SSL or not.  This is based on the ssl info being populated
     *
     * @return If the keystore file path is populated
     * @since 1.2.0
     */
    protected fun useSSL(): Boolean = this.sslKeystoreFilePath != null && this.sslKeystoreFilePath!!.isNotEmpty()

    /**
     * Lazy getter for ssl context
     *
     * @since 2.0.0
     */
    protected val sslContext:SSLContext by lazy {
        val context = SSLContext.getInstance(protocol)
        context.init(createKeyManagers(sslKeystoreFilePath!!, sslStorePassword!!, sslKeystorePassword!!), createTrustManagers(sslTrustStoreFilePath!!, sslStorePassword!!), SecureRandom())
        return@lazy context
    }

    companion object {
        val UNEXPECTED_EXCEPTION: Short = java.lang.Short.MAX_VALUE
        val PUSH_NOTIFICATION: Short = java.lang.Short.MIN_VALUE
        val DEFAULT_SOCKET_BUFFER_SIZE = 1024*1024*120
    }
}
