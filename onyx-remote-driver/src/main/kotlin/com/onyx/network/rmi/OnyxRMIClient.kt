package com.onyx.network.rmi

import com.onyx.exception.ConnectionFailedException
import com.onyx.exception.InitializationException
import com.onyx.exception.InitializationException.Companion.CONNECTION_EXCEPTION
import com.onyx.exception.OnyxServerException
import com.onyx.exception.RequestTimeoutException
import com.onyx.extension.common.DeferredValue
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.network.auth.AuthenticationManager
import com.onyx.network.push.PushConsumer
import com.onyx.network.push.PushRegistrar
import com.onyx.network.push.PushSubscriber
import com.onyx.network.rmi.data.RMIRequest
import com.onyx.network.serialization.ServerSerializer
import com.onyx.network.serialization.impl.DefaultServerSerializer
import com.onyx.network.transport.data.RequestToken
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds

/**
 * Created by Tim Osborn on 6/27/16.
 *
 *
 * The purpose of this is to serve as a thin client for retrieving proxy remote instances from the server
 *
 * @since 1.2.0
 */
class OnyxRMIClient : PushRegistrar {

    // Local Cache of Remote Objects
    private val registeredObjects = HashMap<String, Any>()
    private val serializer: ServerSerializer = DefaultServerSerializer()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timeout = 120 // 60 second timeout
    private var connectTimeout = 5
    var keepAlive = false

    var authenticationManager: AuthenticationManager? = null

    private var user: String = "admin"
    private var password: String = "admin"

    // Set Username and password
    fun setCredentials(user: String, password: String) {
        this.user = user
        this.password = password
    }

    /**
     * Get a Remote Proxy Object
     *
     * @param remoteId Instance name of the registered object
     * @param type     The class type of what you are trying to get
     * @return Instance of the remote proxy object
     * @since 1.2.0
     */
    fun getRemoteObject(remoteId: String, type: Class<*>): Any? {

        // Return the registered Object
        if (registeredObjects.containsKey(remoteId))
            return registeredObjects[remoteId]

        // Create an array to feed to the Proxy factory
        val interfaces = arrayOfNulls<Class<*>>(1)
        interfaces[0] = type

        val instance = Proxy.newProxyInstance(type.classLoader, interfaces, RMIClientInvocationHandler(type, remoteId))

        // Add it to the local cache
        registeredObjects[remoteId] = instance
        return instance
    }

    /**
     * This class is added in order to support tracking of methods.
     * Rather than sending in the string value of a method, this is optimized
     * to use the sorted index of a method so the packet is reduced from
     * a string to a single byte.
     *
     * @since 1.3.0
     */
    private inner class RMIClientInvocationHandler(type: Class<*>, val remoteId: String) : InvocationHandler {
        var methods: MutableList<Method> = ArrayList()

        init {
            val methodArray = type.declaredMethods
            this.methods = listOf(*methodArray).toMutableList()
            this.methods.sortBy { it.toString() }
        }

        @Throws(Throwable::class)
        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val request = RMIRequest(remoteId, methods.indexOf(method).toByte(), args)
            val result = send(request)
            if (result is Exception)
                throw result
            return result
        }
    }

    // region Push Methods

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
     * Handle a response message.  As a pre-requisite, this is invoked via an asynchronous thread in order to prevent
     * serialization on an i/o thread.
     *
     * @param message              Message containing packet parts
     * @since 1.2.0
     */
    private fun handleMessage(message: RequestToken) {
        // General unhandled exception that cannot be tied back to a request
        when (message.token) {
            UNEXPECTED_EXCEPTION -> (message.packet as Exception).printStackTrace()
            PUSH_NOTIFICATION -> handlePushMessage(message)
            else -> {
                val consumer = pendingRequests.remove(message)
                consumer?.complete(message.packet)
            }
        }
    }

    /**
     * Register a push consumer with a subscriber.
     *
     * @param consumer Object to send to the server to register the push subscription.
     * @param responder Local responder object that will handle the inbound push notifications
     *
     * @throws OnyxServerException Cannot communicate with server
     *
     * @since 1.3.0
     */
    @Throws(OnyxServerException::class)
    override fun register(consumer: PushSubscriber, responder: PushConsumer) {
        consumer.setSubscriberEvent(1.toByte())

        val pushId = send(consumer) as Long
        consumer.pushObjectId = pushId
        registeredPushConsumers[pushId] = responder

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

    private var tokenCounter =
        (java.lang.Short.MIN_VALUE + 1).toShort() // +1 because Short.MIN_VALUE denotes a push event

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

    private val pendingRequests = ConcurrentHashMap<RequestToken, DeferredValue<Any?>>()

    /**
     * Send a generic packet and await the response
     *
     * @param packet Any non null request
     * @return The server response to that packet
     *
     * @since 3.4.4 Refactored to use Ktor
     */
    fun send(packet: Any): Any? {
        return runBlocking {
            if (connection == null) {
                return@runBlocking ConnectionFailedException(CONNECTION_EXCEPTION)
            }
            val token = RequestToken(generateNewToken(), packet)

            val future = DeferredValue<Any?>()
            pendingRequests[token] = future

            try {
                connection?.send(Frame.Binary(true, serializer.serialize(token)))
                future.get(timeout.toLong(), TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                pendingRequests.remove(token)
                if (active)
                    RequestTimeoutException()
                else null
            }
        }
    }

    private val client = HttpClient {
        install(WebSockets)
    }

    private var connection: DefaultClientWebSocketSession? = null
    private var active = false

    /**
     * Connect to the onyx rmi server
     *
     * @param host Host to connect to
     * @param port Server port
     */
    fun connect(host: String, port: Int) {
        active = true
        val deferred = CompletableDeferred<Boolean>()
        serviceScope.launch {
            try {
                while (active ) {
                    try {
                        client.webSocket(host = host, port = port, path = "/") {
                            connection = this

                            if (deferred.isCompleted) {
                                launch {
                                    try {
                                        authenticationManager?.verify(user, password)
                                    } catch (e: InitializationException) {
                                        close()
                                        if (e.message == InitializationException.INVALID_CREDENTIALS)
                                            throw e
                                    } catch (e: RequestTimeoutException) {
                                        close()
                                        throw ConnectionFailedException(ConnectionFailedException.CONNECTION_TIMEOUT)
                                    }
                                }
                            }
                            deferred.complete(true)

                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Binary -> {
                                        launch {
                                            val token =
                                                serializer.deserialize<RequestToken>(ByteBuffer.wrap(frame.data))
                                            handleMessage(token)
                                        }
                                    }

                                    else -> Unit
                                }
                            }
                        }
                        connection = null
                    }
                    catch (e: Exception) {
                        connection = null
                        delay(200)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            if (keepAlive) {
                runBlocking {
                    deferred.await()
                }
            } else {
                runBlocking {
                    withTimeout(connectTimeout.seconds) {
                        deferred.await()
                    }
                }
            }
            this.authenticationManager?.verify(this.user, this.password)
        } catch (e: InitializationException) {
            this.close()
            if (e.message == InitializationException.INVALID_CREDENTIALS)
                throw e
        } catch (e: TimeoutCancellationException) {
            throw ConnectionFailedException(ConnectionFailedException.CONNECTION_TIMEOUT)
        } catch (e: RequestTimeoutException) {
            this.close()
            throw ConnectionFailedException(ConnectionFailedException.CONNECTION_TIMEOUT)
        }
    }

    fun close() {
        pendingRequests.forEach { it.value.complete(InitializationException(CONNECTION_EXCEPTION)) }
        pendingRequests.clear()
        active = false
        client.close()
    }

    companion object {
        const val UNEXPECTED_EXCEPTION: Short = java.lang.Short.MAX_VALUE
        const val PUSH_NOTIFICATION: Short = java.lang.Short.MIN_VALUE
    }
}
