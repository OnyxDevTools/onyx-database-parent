package com.onyx.network

import com.onyx.application.OnyxServer
import com.onyx.buffer.NetworkBufferPool
import com.onyx.network.auth.impl.NetworkPeer
import com.onyx.network.transport.data.Message
import com.onyx.network.transport.engine.PacketTransportEngine
import com.onyx.network.transport.engine.impl.SecurePacketTransportEngine
import com.onyx.network.transport.engine.impl.UnsecuredPacketTransportEngine
import com.onyx.network.connection.ConnectionFactory
import com.onyx.exception.MethodInvocationException
import com.onyx.exception.ServerClosedException
import com.onyx.network.handlers.RequestHandler
import com.onyx.network.push.PushSubscriber
import com.onyx.network.push.PushPublisher
import com.onyx.network.transport.data.toRequest
import com.onyx.exception.InitializationException
import com.onyx.extension.common.async
import com.onyx.extension.common.catchAll
import com.onyx.extension.common.runJob
import com.onyx.interactors.encryption.impl.DefaultEncryptionInteractor
import com.onyx.interactors.encryption.EncryptionInteractor
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.network.connection.Connection
import com.onyx.network.transport.data.RequestToken

import javax.net.ssl.SSLContext
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.*
import java.nio.channels.spi.SelectorProvider
import java.util.HashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Tim Osborn 02/13/2016
 *
 * @since 1.2.0
 *
 *
 * The purpose of this class is to route connections and traffic.  It has been added
 * as a response to remove 3rd party dependencies and improve performance.  Also, to
 * simplify SSL communication.  All socket server communication must go through here.
 *
 *
 * This utilizes off heap buffering.  It sets up a buffer pool for how many active threads you can have.
 * Each connection buffer pool contains 2 allocated buffers.  The size is specified within the packet transport
 * engine.  Be wary on how much you allocate since they are allocated for each connection
 *
 * @since 2.0.0 Refactored to be in Kotlin.
 */
open class NetworkServer : NetworkPeer(), OnyxServer, PushPublisher {

    override var encryption: EncryptionInteractor = DefaultEncryptionInteractor
    protected var requestHandler: RequestHandler? = null // Handler for responding to requests

    private var sslContext: SSLContext? = null // SSL Context if used.  Otherwise this will be null
    private var selector: Selector? = null // Selector for inbound communication
    private var serverSocketChannel: ServerSocketChannel? = null

    // region Start / Stop Lifecycle

    /**
     * Start Server
     *
     * @since 1.2.0
     */
    override fun start() {

        // Create a dummy transport engine in order to see how much the buffer allocation should be
        val transportPacketTransportEngine: PacketTransportEngine = if (useSSL()) {
            val engine = sslContext!!.createSSLEngine()
            engine.useClientMode = false
            engine.beginHandshake()
            SecurePacketTransportEngine(engine)
        } else {
            UnsecuredPacketTransportEngine()
        }

        selector = SelectorProvider.provider().openSelector()
        serverSocketChannel = ServerSocketChannel.open()
        serverSocketChannel!!.apply {
            socket().reuseAddress = true
            socket().receiveBufferSize = transportPacketTransportEngine.packetSize
            socket().setPerformancePreferences(0,2,1)
            configureBlocking(false)
            bind(InetSocketAddress(port))
            register(selector, SelectionKey.OP_ACCEPT)
        }

        active = true
        startReadQueue()
    }

    /**
     * Stop Server
     *
     * @since 1.2.0
     */
    override fun stop() {
        active = false
        stopReadQueue()
        selector?.wakeup()
        serverSocketChannel?.socket()?.close()
        serverSocketChannel?.close()
    }

    /**
     * Join Server.  Have it pause on a daemon thread
     *
     * @since 1.2.0
     */
    override fun join() {
        readJob?.join()
    }

    /**
     * Identify whether the application is running or not
     *
     * @return boolean value
     * @since 1.2.0
     */
    override val isRunning: Boolean
        get() = active

    // endregion

    // region Read Job

    /**
     * Poll the server connections for inbound communication
     *
     * @throws ServerClosedException Whoops, the server closed.  No need to be reading any more data
     * @since 1.2.0
     */
    @Throws(ServerClosedException::class)
    override fun startReadQueue() {
        readJob = runJob(100, TimeUnit.MICROSECONDS) {

            try { selector?.select() } catch (e: IOException) { throw ServerClosedException(e) }

            val selectedKeys = selector?.selectedKeys()?.iterator()

            // Iterate through all the selection keys that have pending reads
            while (selectedKeys?.hasNext()!!) {
                catchAll {
                    val key = selectedKeys.next()
                    selectedKeys.remove()
                    when {
                        !key.isValid || !key.channel().isOpen -> closeConnection(key.channel() as SocketChannel, key.attachment() as Connection)
                        key.isAcceptable -> try {
                            accept(key)
                        } catch (any: Exception) {
                            closeConnection(key.channel() as SocketChannel, key.attachment() as Connection)
                        }
                        key.isReadable -> {
                            read(key.channel() as SocketChannel, key.attachment() as Connection)
                        }
                    }
                }
            }
        }
    }

    // endregion

    // region Message Handlers

    /**
     * Handle an inbound message
     *
     * @param socketChannel        Socket Channel read from
     * @param connection Connection information containing buffer and thread info
     * @param message              Network message containing packet segments
     * @since 1.2.0
     */
    override fun handleMessage(socketChannel: SocketChannel, connection: Connection, message: Message) {

        try {
            val request: RequestToken = message.toRequest(serverSerializer)

            request.apply {
                when (packet) {
                    is PushSubscriber -> handlePushSubscription(this, socketChannel, connection)
                    else -> {
                        packet = try {
                            requestHandler?.accept(connection, packet)
                        } catch (e: Exception) {
                            MethodInvocationException(MethodInvocationException.UNHANDLED_EXCEPTION, e)
                        }

                        write(socketChannel, connection, this)
                    }
                }
            }
        } catch (e:Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Failure within the server.  This should be logged
     *
     * @param token Original request
     * @param cause     The underlying exception
     * @since 1.2.0
     */
    override fun failure(cause: Exception, token: RequestToken?) {
        if (cause !is InitializationException)
            cause.printStackTrace() // TODO() Something better than printing a damn stacktrace
    }

    // endregion

    // region Push and Subscribers

    // Registered Push subscribers
    private val pushSubscribers = OptimisticLockingMap<PushSubscriber, PushSubscriber>(HashMap())

    // Counter for correlating push subscribers
    private val pushSubscriberId = AtomicLong(0)

    /**
     * Handle a push registration event.
     *
     * 1.  The registration process starts with a request with a subscriber object.
     * It is indicated as a push subscriber only because of the type of packet.
     * The packet will contain an subscriberEvent of 1 indicating it is a
     * request to register for push notifications
     * 2.  The subscriber object is assigned an identity
     * 3.  It exists and only gets cleared out if the connection is dropped
     * 4.  Client sends same request only containing a code of 2 indicating
     * it is a de-register event
     *
     * @param message Request information
     * @param socketChannel Socket to push notifications to
     * @param connection Connection information
     *
     * @since 1.3.0 Push notifications were introduced
     */
    private fun handlePushSubscription(message: RequestToken, socketChannel: SocketChannel, connection: Connection) {
        val subscriber = message.packet as PushSubscriber
        subscriber.channel = socketChannel
        // Register subscriber
        if (subscriber.subscribeEvent == REMOVE_SUBSCRIBER_EVENT) {
            subscriber.connection = connection
            subscriber.setPushPublisher(this)
            subscriber.pushObjectId = pushSubscriberId.incrementAndGet()
            message.packet = subscriber.pushObjectId
            pushSubscribers.put(subscriber, subscriber)
        } else if (subscriber.subscribeEvent == REGISTER_SUBSCRIBER_EVENT) {
            // Remove subscriber
            pushSubscribers.remove(subscriber)
        }

        async {
            write(socketChannel, connection, message)
        }
    }

    /**
     * Push an object to the client.  This does not wait for receipt nor a response
     *
     * @param pushSubscriber Push notification subscriber
     * @param message Message to send to client
     *
     * @since 1.3.0
     */
    override fun push(pushSubscriber: PushSubscriber, message: Any) {
        if (pushSubscriber.channel!!.isOpen && pushSubscriber.channel!!.isConnected) {
            pushSubscriber.packet = message
            async {
                write(pushSubscriber.channel!!, pushSubscriber.connection!!, RequestToken(PUSH_NOTIFICATION, pushSubscriber))
            }
        } else {
            deRegisterSubscriberIdentity(pushSubscriber) // Clean up non connected subscribers if not connected
        }
    }

    /**
     * Get the actual registered identity of the push subscriber.  This correlates references
     *
     * @param pushSubscriber Subscriber sent from push registration request
     * @return The actual reference of the subscriber
     *
     * @since 1.3.0
     */
    override fun getRegisteredSubscriberIdentity(pushSubscriber: PushSubscriber): PushSubscriber? = pushSubscribers[pushSubscriber]

    /**
     * Remove the subscriber
     *
     * @param pushSubscriber push subscriber to de-register
     *
     * @since 1.3.0
     */
    override fun deRegisterSubscriberIdentity(pushSubscriber: PushSubscriber) {
        pushSubscribers.remove(pushSubscriber)
    }

    // endregion

    // region In-Bound Connection Handler

    /**
     * Accept an inbound connection
     *
     * @param key Selection Key
     * @throws Exception Connection was not successful
     * @since 1.2.0
     */
    @Throws(Exception::class)
    private fun accept(key: SelectionKey) {

        val socketChannel = (key.channel() as ServerSocketChannel).accept()
        socketChannel.configureBlocking(false)

        val transportPacketTransportEngine: PacketTransportEngine = if (useSSL()) {
            val engine = sslContext!!.createSSLEngine()
            engine.useClientMode = false
            engine.beginHandshake()
            SecurePacketTransportEngine(engine)
        } else {
            UnsecuredPacketTransportEngine(socketChannel)
        }

        NetworkBufferPool.init(transportPacketTransportEngine.packetSize)
        socketChannel.socket().sendBufferSize = transportPacketTransportEngine.packetSize
        socketChannel.socket().receiveBufferSize = transportPacketTransportEngine.packetSize
        socketChannel.socket().tcpNoDelay = true
        socketChannel.socket().oobInline = false
        socketChannel.socket().setPerformancePreferences(0,2,1)

        // Send the buffer pool into the connectionProperties so that they may retain its references
        val connectionProperties = ConnectionFactory.create(transportPacketTransportEngine)

        // Perform handshake.  If this is secure SSL, this does something otherwise, it is just pass through
        if (doHandshake(socketChannel, connectionProperties)) {
            socketChannel.register(selector, SelectionKey.OP_READ, connectionProperties)
        } else {
            closeConnection(socketChannel, connectionProperties)
        }
    }

    // endregion

    /**
     * Credentials are not set here.  This is not to be used.  If you want it secure
     * setup a keystore and trust store.  If you do not choose to use SSL, auth is done
     * on an application level.
     *
     * @param user     Username
     * @param password Password
     * @since 1.2.0
     */
    override fun setCredentials(user: String, password: String) = Unit

    companion object {
        val REMOVE_SUBSCRIBER_EVENT = 1.toByte()
        val REGISTER_SUBSCRIBER_EVENT = 2.toByte()
    }

}
