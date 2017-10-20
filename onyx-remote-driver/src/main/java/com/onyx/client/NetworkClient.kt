package com.onyx.client

import com.onyx.buffer.NetworkBufferPool
import com.onyx.client.auth.AuthenticationManager
import com.onyx.client.base.Connection
import com.onyx.client.push.PushSubscriber
import com.onyx.client.push.PushConsumer
import com.onyx.client.base.RequestToken
import com.onyx.client.base.engine.PacketTransportEngine
import com.onyx.client.base.engine.impl.SecurePacketTransportEngine
import com.onyx.client.base.engine.impl.UnsecuredPacketTransportEngine
import com.onyx.client.connection.ConnectionFactory
import com.onyx.client.exception.ConnectionFailedException
import com.onyx.client.exception.OnyxServerException
import com.onyx.client.exception.RequestTimeoutException
import com.onyx.client.push.PushRegistrar
import com.onyx.exception.InitializationException
import com.onyx.extension.common.catchAll
import com.onyx.extension.common.runJob
import com.onyx.lang.map.OptimisticLockingMap
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.TimeoutException

import javax.net.ssl.SSLContext
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel
import java.security.SecureRandom
import java.util.HashMap
import java.util.concurrent.*

/**
 * Tim Osborn 02/13/2017
 *
 * This class' purpose is to handle all the communication with the server.  It is setup
 * to use either SSL TLS or un secure.
 *
 * It was created in order to improve the performance and remove 3rd party libraries
 *
 * @since 1.2.0
 * @since 2.0.0 Refactored from Communication Peer to NetworkClient.  As part of that make coroutine and non blocking
 */
open class NetworkClient : AbstractNetworkPeer(), OnyxClient, PushRegistrar {

    // region Variables

    override var timeout = 60 // 60 second timeout
    override var connectTimeout = 5 // 5 second connection timeout

    private lateinit var host: String

    // Heartbeat
    private var needsToRunHeartbeat = true // If there was a response recently, there is no need to send a heartbeat
    private var heartBeatJob: Job? = null

    // Connection information
    private var connection: Connection? = null
    private var socketChannel: SocketChannel? = null
    private val pendingRequests = ConcurrentHashMap<RequestToken, CompletableDeferred<Any?>>()

    // Map of push consumers
    private val registeredPushConsumers = OptimisticLockingMap<Long, PushConsumer>(HashMap())

    // Keeps track of a unique token.  There can only be about 64 K concurrent requests for a client.
    private var tokenCounter = (java.lang.Short.MIN_VALUE + 1).toShort() // +1 because Short.MIN_VALUE denotes a push event

    // endregion

    // region Authentication

    // User and authentication
    var authenticationManager: AuthenticationManager? = null
    private var user: String? = null
    private var password: String? = null

    // Set Username and password
    fun setCredentials(user: String, password: String) {
        this.user = user
        this.password = password
    }

    // endregion

    // region Connection

    override val isConnected: Boolean
        get() = socketChannel?.isConnected ?: false

    /**
     * Connect to a server with given host and port #
     *
     * @param host Host name or ip address
     * @param port Server port
     * @throws ConnectionFailedException Connection refused or server communication issue
     * @since 1.2.0
     */
    @Throws(ConnectionFailedException::class)
    override fun connect(host: String, port: Int) {
        this.port = port
        this.host = host
        val transportPacketTransportEngine: PacketTransportEngine = if (useSSL()) {
            val context = SSLContext.getInstance(protocol)
            context.init(createKeyManagers(sslKeystoreFilePath!!, sslStorePassword!!, sslKeystorePassword!!), createTrustManagers(sslTrustStoreFilePath!!, sslStorePassword!!), SecureRandom())
            val engine = context.createSSLEngine(host, port)
            engine.useClientMode = true
            SecurePacketTransportEngine(engine)
        } else {
            UnsecuredPacketTransportEngine()
        }

        NetworkBufferPool.init(transportPacketTransportEngine.packetSize)

        // Try to open the connection
        try {
            socketChannel = SocketChannel.open()
            socketChannel!!.socket().keepAlive = true
            socketChannel!!.socket().tcpNoDelay = true
            socketChannel!!.socket().reuseAddress = true
        } catch (e: IOException) {
            throw ConnectionFailedException()
        }

        // Create a buffer and set the transport wrapper
        this.connection = ConnectionFactory.create(transportPacketTransportEngine)

        if (transportPacketTransportEngine is UnsecuredPacketTransportEngine) {
            transportPacketTransportEngine.setSocketChannel(socketChannel)
        }

        try {
            socketChannel?.configureBlocking(true)
            socketChannel?.socket()?.connect(InetSocketAddress(host, port), connectTimeout * 1000)
            while (!socketChannel!!.finishConnect())
                runBlocking { delay(10, TimeUnit.MILLISECONDS) }
        } catch (e: IOException) {
            throw ConnectionFailedException()
        }

        try {
            // Perform Handshake.  If this is unsecured, it is just pass through
            transportPacketTransportEngine.beginHandshake()
            active = doHandshake(socketChannel!!, connection)
        } catch (e: IOException) {
            throw ConnectionFailedException()
        }

        startWriteQueue()
        startReadQueue()

        try {
            this.authenticationManager?.verify(this.user, this.password)
            this.resumeHeartBeat()
        } catch (e: InitializationException) {
            this.close()
        } catch (e: RequestTimeoutException) {
            this.close()
            throw ConnectionFailedException(ConnectionFailedException.CONNECTION_TIMEOUT)
        }
    }

    /**
     * Close connection
     *
     * @since 1.2.0
     */
    override fun close() {
        active = false
        closeConnection(socketChannel!!, connection!!)
        needsToRunHeartbeat = false
        pendingRequests.clear()
        heartBeatJob?.cancel()
        stopReadQueue()
        stopWriteQueue()
    }

    /**
     * Verify the connection and attempt to re-connect if the connection is not valid
     */
    private fun verifyConnection() {
        if (!isConnected) {
            catchAll {
                this.connect(this.host, this.port)
            }
        }
    }

    // endregion

    // region Jobs

    /**
     * Poll for communication responses from the server
     *
     * @since 2.0.0 Refactored as a Job
     */
    override fun startReadQueue() {
        readJob = runJob("Client Read Job") {
            while (active) {
                catchAll {
                    read(socketChannel!!, connection!!)
                }
            }
        }
    }

    // endregion

    // region Send

    /**
     * Send a message to the server.  This is blocking and will wait for the response.
     *
     * @param packet Object to send to server
     * @return The response from the server
     * @throws OnyxServerException Error sending request
     * @since 1.2.0
     */
    @Throws(OnyxServerException::class)
    override fun send(packet: Any): Any? = send(packet, timeout * 1000)

    /**
     * Send a message to the server.  This is blocking and will wait for the response.
     *
     * @param packet  Object to send to server
     * @param timeout timeout in milliseconds
     * @return The response from the server
     * @since 1.2.0
     */
    private fun send(packet: Any?, timeout: Int): Any? {

        verifyConnection()
        val token = RequestToken(generateNewToken(), packet)
        val deferredValue = CompletableDeferred<Any?>()
        pendingRequests.put(token, deferredValue)

        try {
            write(socketChannel!!, connection!!, token)
            return runBlocking {
                withTimeout(timeout.toLong()) {
                    deferredValue.await()
                }
            }
        } catch (e:TimeoutException) {
            pendingRequests.remove(token)
            if (active)
                return RequestTimeoutException()
            return null
        }
    }

    /**
     * Generates a new token.  Resets it to 0 if we have reached the maximum
     *
     * @return New token id
     */
    @Synchronized
    private fun generateNewToken(): Short {
        if (tokenCounter >= java.lang.Short.MAX_VALUE - 1)
            // Short.MAX_VALUE is reserved for un-correlated error
            tokenCounter = (java.lang.Short.MIN_VALUE + 1).toShort()
        return tokenCounter++
    }

    // endregion

    // region Message Handlers

    /**
     * Handle an response message.  As a pre-requisite, this is invoked via an asynchronous thread in order to prevent
     * serialization on an i/o thread.
     *
     * @param socketChannel        Socket Channel read from
     * @param connection Connection information containing buffer and thread info
     * @param message              Message containing packet parts
     * @since 1.2.0
     */
    override fun handleMessage(socketChannel: SocketChannel, connection: Connection, message:Message) {
        val request = message.toRequest(serverSerializer)

        // General unhandled exception that cannot be tied back to a request
        when {
            request.token == UNEXPECTED_EXCEPTION -> (request.packet as Exception).printStackTrace()
            request.token == PUSH_NOTIFICATION -> handlePushMessage(request)
            else -> {
                val consumer = pendingRequests.remove(request)
                consumer?.complete(request.packet)
                needsToRunHeartbeat = false // Successful response.  No need to run heartbeat
            }
        }
    }

    /**
     * Message failure. Respond to the failure by sending the exception as the response.
     *
     * @param token Message ID
     * @param cause Exception that caused it to fail
     *
     * @since 1.2.0
     */
    override fun failure(token: RequestToken, cause: Exception) {
        try {
            val consumer = pendingRequests.remove(token)
            consumer?.complete(cause)
        } catch (ignore: Exception) {
        } finally {
            cause.printStackTrace() // TODO() Do something better than printing a stacktrace
        }
    }

    // endregion

    // region Push Methods

    /**
     * Respond to a push event.  The consumer will be invoked
     * @param requestToken Request token
     *
     * @since 1.3.0 Support push events
     */
    private fun handlePushMessage(requestToken: RequestToken) {
        val consumer = requestToken.packet as PushSubscriber
        val responder = registeredPushConsumers[consumer.pushObjectId]
        responder?.accept(consumer.packet)
    }

    /**
     * Register a push consumer with a subscriber.
     *
     * @param subscriber Object to send to the server to register the push subscription.
     * @param responder Local responder object that will handle the inbound push notifications
     *
     * @throws OnyxServerException Cannot communicate with server
     *
     * @since 1.3.0
     */
    @Throws(OnyxServerException::class)
    override fun register(subscriber: PushSubscriber, responder: PushConsumer) {
        subscriber.setSubscriberEvent(1.toByte())

        val pushId = send(subscriber) as Long
        subscriber.pushObjectId = pushId
        registeredPushConsumers.put(pushId, responder)

    }

    /**
     * De register a push subscriber.
     *
     * @param subscriber Subscriber associated to the push listener
     * @throws OnyxServerException Typically indicates cannot connect to server
     * @since 1.3.0
     */
    @Throws(OnyxServerException::class)
    override fun unregister(subscriber: PushSubscriber) {
        registeredPushConsumers.remove(subscriber.pushObjectId)
        subscriber.setSubscriberEvent(2.toByte())
        send(subscriber)
    }

    // endregion

    // region Heart Beat

    /**
     * Resume Heartbeat.  This is performed after successful authentication
     *
     * @since 1.2.0
     */
    private fun resumeHeartBeat() {
        heartBeatJob = runJob("Client Heart Beat Job") {
            while (true) {
                runHeartBeat()
                delay(HEART_BEAT_INTERVAL, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * Run a heartbeat pulse to ensure we ae still connected
     *
     * @since 2.0.0 Converted to a job and a method to make the entire server non blocking
     */
    private fun runHeartBeat() {
        var result: Any? = null
        try {
            // If there were no recent responses within the last 5 seconds, run a heartbeat
            if (needsToRunHeartbeat) {
                result = send(null, HEART_BEAT_INTERVAL.toInt())
            } else {
                needsToRunHeartbeat = true
            }
        } catch (e: Exception) {
            result = e
        }

        // If the connection is still active && there was an error during the heartbeat, try to re-connect
        // After re-connect, retry the pending requests
        if (active
                && result != null
                && result is Exception) {
            try {

                // Heartbeat failed, try to re-connect
                closeConnection(socketChannel!!, connection!!)
                connect(host, port)

                if (active
                        && socketChannel!!.isConnected
                        && socketChannel!!.isOpen) {

                    // If there are more than 5 requests, fail the requests and flush the queue
                    if (pendingRequests.size > 5) {
                        pendingRequests.forEach { it.value.complete(InitializationException(InitializationException.CONNECTION_EXCEPTION)) }
                        pendingRequests.clear()
                    }
                    // Re-send all failed packets
                    pendingRequests.forEach { requestToken, _ ->
                        // Ignore heartbeat packets
                        if (requestToken.packet != null) {
                            write(socketChannel!!, connection!!, requestToken)
                        }
                    }
                }
            } catch (ignore: ConnectionFailedException) {
                // If there are more than 20 requests, fail the requests and flush the queue
                pendingRequests.forEach { it.value.complete(InitializationException(InitializationException.CONNECTION_EXCEPTION)) }
                pendingRequests.clear()
            }
        }
    }

    // endregion

    companion object {
        val HEART_BEAT_INTERVAL = 10L
    }

}
