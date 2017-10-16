package com.onyx.client

import com.onyx.client.auth.AuthenticationManager
import com.onyx.client.base.ConnectionProperties
import com.onyx.client.push.PushSubscriber
import com.onyx.client.push.PushConsumer
import com.onyx.client.base.RequestToken
import com.onyx.client.base.engine.PacketTransportEngine
import com.onyx.client.base.engine.impl.SecurePacketTransportEngine
import com.onyx.client.base.engine.impl.UnsecuredPacketTransportEngine
import com.onyx.client.exception.ConnectionFailedException
import com.onyx.client.exception.OnyxServerException
import com.onyx.client.exception.RequestTimeoutException
import com.onyx.client.push.PushRegistrar
import com.onyx.exception.InitializationException
import com.onyx.lang.map.OptimisticLockingMap

import javax.net.ssl.SSLContext
import java.io.IOException
import java.io.Serializable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.security.SecureRandom
import java.util.HashMap
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

/**
 * Tim Osborn 02/13/2017
 *
 *
 * This class' purpose is to handle all the communication with the server.  It is setup
 * to use either SSL TLS or un secure.
 *
 *
 * It was created in order to improve the performance and remove 3rd party libraries
 *
 * @since 1.2.0
 */
open class CommunicationPeer : AbstractCommunicationPeer(), OnyxClient, PushRegistrar {

    // Heartbeat and timeout
    override var timeout = 60 // 60 second timeout
    @Volatile private var needsToRunHeartbeat = true // If there was a response recently, there is no need to send a heartbeat
    private var heartBeatExecutor: ScheduledExecutorService? = null

    // Connection information
    private var connectionProperties: ConnectionProperties? = null
    private var socketChannel: SocketChannel? = null
    private val pendingRequests = ConcurrentHashMap<RequestToken, (Any?)->Unit>()
    private lateinit var host: String

    // User and authentication
    private var authenticationManager: AuthenticationManager? = null
    private var user: String? = null
    private var password: String? = null

    // Keeps track of a unique token.  There can only be about 64 K concurrent requests for a client.
    @Volatile private var tokenCount = (java.lang.Short.MIN_VALUE + 1).toShort() // +1 because Short.MIN_VALUE denotes a push event

    /**
     * Handle an response message
     *
     * @param packetType           Indicates if the packet can fit into 1 buffer or multiple
     * @param socketChannel        Socket Channel read from
     * @param connectionProperties ConnectionProperties information containing buffer and thread info
     * @param buffer               ByteBuffer containing message
     * @since 1.2.0
     */
    override fun handleMessage(packetType: Byte, socketChannel: SocketChannel, connectionProperties: ConnectionProperties, buffer: ByteBuffer) {
        var requestToken: RequestToken? = null
        try {
            requestToken = serverSerializer.deserialize(buffer, RequestToken()) as RequestToken

            // General unhandled exception that cannot be tied back to a request
            if (requestToken.token == java.lang.Short.MAX_VALUE) {
                (requestToken.packet as Exception).printStackTrace()
            } else if (requestToken.token == java.lang.Short.MIN_VALUE) {
                // This indicates a push request
                handlePushMessage(requestToken)
            }

            val consumer = pendingRequests.remove(requestToken)

            if (consumer != null) {
                consumer.invoke(requestToken.packet)
                needsToRunHeartbeat = false
            }

        } catch (e: Exception) {
            failure(requestToken!!, e)
        }

    }

    // Map of push consumers
    private val registeredPushConsumers = OptimisticLockingMap<Long, PushConsumer>(HashMap())

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
    override fun unrigister(subscriber: PushSubscriber) {
        registeredPushConsumers.remove(subscriber.pushObjectId)
        subscriber.setSubscriberEvent(2.toByte())
        send(subscriber)
    }

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
        val transportPacketTransportEngine: PacketTransportEngine

        // Setup SSL Settings
        if (useSSL()) {
            val context: SSLContext
            try {
                context = SSLContext.getInstance(protocol)
                context.init(createKeyManagers(sslKeystoreFilePath!!, sslStorePassword!!, sslKeystorePassword!!), createTrustManagers(sslTrustStoreFilePath!!, sslStorePassword!!), SecureRandom())
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            val engine = context.createSSLEngine(host, port)
            engine.useClientMode = true
            transportPacketTransportEngine = SecurePacketTransportEngine(engine)
        } else {
            transportPacketTransportEngine = UnsecuredPacketTransportEngine()
        }// Not SSL use un-secure transport engine

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
        this.connectionProperties = ConnectionProperties(transportPacketTransportEngine)
        if (!useSSL()) {
            (transportPacketTransportEngine as UnsecuredPacketTransportEngine).setSocketChannel(socketChannel)
        }

        try {

            socketChannel!!.configureBlocking(true)
            val connectTimeout = 5 * 1000
            socketChannel!!.socket().connect(InetSocketAddress(host, port), connectTimeout)
            while (!socketChannel!!.finishConnect()) {
                LockSupport.parkNanos(100)
            }
        } catch (e: IOException) {
            throw ConnectionFailedException()
        }

        try {
            // Perform Handshake.  If this is unsecured, it is just pass through
            transportPacketTransportEngine.beginHandshake()
            active = doHandshake(socketChannel!!, connectionProperties)

        } catch (e: IOException) {
            throw ConnectionFailedException()
        }

        connectionProperties!!.readThread.execute { this.pollForCommunication() }
        try {
            this.authenticationManager!!.verify(this.user, this.password)
            this.resumeHeartBeat()
        } catch (e: InitializationException) {

            // Authentication failed, disconnect
            this.close()
        } catch (e: RequestTimeoutException) {
            this.close()
            throw ConnectionFailedException(ConnectionFailedException.CONNECTION_TIMEOUT)
        }

    }

    /**
     * Verify the connection and attempt to re-connect if the connection is not valid
     */
    private fun verifyConnection() {
        if (!isConnected) {
            try {
                this.connect(this.host, this.port)
            } catch (ignore: ConnectionFailedException) {
            }

        }
    }

    /**
     * Resume Heartbeat.  This is performed after successful authentication
     *
     * @since 1.2.0
     */
    private fun resumeHeartBeat() {
        if (this.heartBeatExecutor == null) {
            this.heartBeatExecutor = Executors.newSingleThreadScheduledExecutor { r ->
                val t = Executors.defaultThreadFactory().newThread(r)
                t.isDaemon = true
                t
            }

            val HEART_BEAT_INTERVAL = 10 * 1000
            heartBeatExecutor!!.scheduleWithFixedDelay(RetryHeartbeatTask(), HEART_BEAT_INTERVAL.toLong(), HEART_BEAT_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Poll for communication responses from the server
     *
     * @since 1.2.0
     */
    private fun pollForCommunication() {
        while (active) {
            try {
                read(socketChannel!!, connectionProperties!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    /**
     * Send an object, wrap it with a request, and fire it off to the server.
     *
     *
     * This is non-blocking and will invoke the consumer upon response.
     *
     * @param packet   Object to send to server
     * @param consumer Consumer for the results
     * @throws OnyxServerException Error sending request
     * @since 1.2.0
     */
    @Throws(OnyxServerException::class)
    override fun send(packet: Any, consumer: (Any?)->Unit) {
        verifyConnection()
        val token = RequestToken(generateNewToken(), packet as Serializable)
        pendingRequests.put(token, consumer)
        write(socketChannel!!, connectionProperties!!, token)
    }

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

        val countDownLatch = CountDownLatch(1)
        val results = AtomicReference<Any>()

        // Release the thread lock
        val consumer:((Any?) -> Unit) = { o ->
            results.set(o)
            countDownLatch.countDown()
        }

        val token = RequestToken(generateNewToken(), packet as Serializable?)
        pendingRequests.put(token, consumer)

        write(socketChannel!!, connectionProperties!!, token)

        val successResponse: Boolean
        try {
            successResponse = countDownLatch.await(timeout.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            return RequestTimeoutException()
        }

        if (!successResponse) {
            pendingRequests.remove(token)
            if (active)
                return RequestTimeoutException()
        }
        return results.get()
    }

    /**
     * Generates a new token.  Resets it to 0 if we have reached the maximum
     *
     * @return New token id
     */
    @Synchronized private fun generateNewToken(): Short {
        if (tokenCount >= java.lang.Short.MAX_VALUE - 1)
        // Short.MAX_VALUE is reserved for un-correlated error
            tokenCount = (java.lang.Short.MIN_VALUE + 1).toShort()
        return tokenCount++
    }

    /**
     * Close connection
     *
     * @since 1.2.0
     */
    override fun close() {
        active = false
        connectionProperties!!.readThread.shutdown()
        closeConnection(socketChannel!!, connectionProperties!!)

        needsToRunHeartbeat = false
        pendingRequests.clear()
        if (this.heartBeatExecutor != null) {
            this.heartBeatExecutor!!.shutdown()
        }
    }

    override val isConnected: Boolean
        get() = socketChannel?.isConnected ?: false

    fun setAuthenticationManager(authenticationManager: AuthenticationManager) {
        this.authenticationManager = authenticationManager
    }

    /**
     * Set User credentials for persistant authentication
     * @param user Username
     * @param password Password
     *
     * @since 1.2.0
     */
    fun setCredentials(user: String, password: String) {
        this.user = user
        this.password = password
    }

    /**
     * Message failure. Respond to the failure by sending the exception as the response.
     *
     * @param token Message ID
     * @param e Exception that caused it to fail
     *
     * @since 1.2.0
     */
    override fun failure(token: RequestToken, e: Exception) {
        try {
            val consumer = pendingRequests.remove(token)
            consumer?.invoke(e)
        } catch (ignore: Exception) {
        } finally {
            e.printStackTrace()
        }
    }

    private inner class RetryHeartbeatTask : Runnable {
        /**
         * Run timer task to execute a heartbeat
         */
        override fun run() {
            var result: Any? = null
            try {
                // If there were no recent responses within the last 5 seconds, run a heartbeat
                if (needsToRunHeartbeat) {
                    val heartBeatTimeout = 1000 * 10
                    result = send(null, heartBeatTimeout)
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
                    try {
                        socketChannel!!.close()
                    } catch (ignore: IOException) {
                    }

                    connect(host, port)

                    if (active
                            && socketChannel!!.isConnected
                            && socketChannel!!.isOpen) {

                        // If there are more than 20 requests, fail the requests and flush the queue
                        if (pendingRequests.size > 5) {
                            pendingRequests.forEach { _, consumer -> consumer.invoke(InitializationException(InitializationException.CONNECTION_EXCEPTION)) }
                            pendingRequests.clear()
                        }
                        // Re-send all failed packets
                        pendingRequests.forEach { requestToken, _ ->
                            // Ignore heartbeat packets
                            if (requestToken.packet != null) {
                                write(socketChannel!!, connectionProperties!!, requestToken)
                            }
                        }
                    }
                } catch (ignore: ConnectionFailedException) {
                    // If there are more than 20 requests, fail the requests and flush the queue
                    pendingRequests.forEach { _, consumer -> consumer.invoke(InitializationException(InitializationException.CONNECTION_EXCEPTION)) }
                    pendingRequests.clear()
                }

            }
        }
    }

}
