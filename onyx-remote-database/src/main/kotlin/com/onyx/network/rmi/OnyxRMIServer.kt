package com.onyx.network.rmi

import com.onyx.network.auth.AuthenticationManager
import com.onyx.network.connection.Connection
import com.onyx.exception.MethodInvocationException
import com.onyx.network.handlers.RequestHandler
import com.onyx.network.rmi.data.RMIRequest
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.network.push.PushPublisher
import com.onyx.network.push.PushSubscriber
import com.onyx.network.rmi.OnyxRMIClient.Companion.PUSH_NOTIFICATION
import com.onyx.network.serialization.ServerSerializer
import com.onyx.network.serialization.impl.DefaultServerSerializer
import com.onyx.network.transport.data.RequestToken
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.HashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Created by Tim Osborn on 6/27/16.
 *
 *
 * This server extends the communication server.  It is so that we have the convenience
 * of using Remote method invocation without the overhead of JAVA RMI.  Java RMI makes
 * it so that we do cannot use connect with Android.
 *
 *
 * Another benefit is that the serialization is much more efficient and the buffering is using
 * off heap buffers.  It is for improved scalability.
 *
 * @since 1.2.0
 */
class OnyxRMIServer : PushPublisher {

    // Local Cache of the registered objects
    private val registeredObjects = OptimisticLockingMap<String, Any>(HashMap())
    private val registeredInterfaces = OptimisticLockingMap<String, Class<*>>(HashMap())
    private val methodCache = OptimisticLockingMap<Class<*>, MutableList<Method>>(HashMap())

    private var requestHandler: RequestHandler
    private val connections = ConcurrentSet<Connection>()
    private val serializer: ServerSerializer = DefaultServerSerializer()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var port: Int = 8082

    /**
     * Constructor
     *
     *
     * Set the message handler to my own fancy RMI Proxy handler
     */
    init {
        /*
          RMI Request handler.  This will grab the correct proxy value,
          execute the method on the proxy value and return the result
         */
        this.requestHandler = object : RequestHandler {

            /**
             * Verify the session is active and valid.  If not, shut it down.
             * @param registeredObject The Proxy class
             * @param connection Connection information
             * @return Whether the session is valid
             * @since 1.2.0
             */
            private fun verifySession(connection: Connection, registeredObject: Any?): Boolean {
                if (!AuthenticationManager::class.java.isAssignableFrom(registeredObject!!.javaClass) && !connection.isAuthenticated) {
                    runBlocking {
                        connection.connection.close()
                    }
                    return false
                }
                return true
            }

            /**
             * Check to see if it is the authentication RPC.  If so, set the result onto the connection properties so
             * we can keep track of the session.
             *
             * @param registeredObject Proxy value
             * @param connection Connection information holding authentication information
             *
             * @since 1.2.0
             */
            private fun checkForAuthentication(registeredObject: Any?, connection: Connection) {
                if (AuthenticationManager::class.java.isAssignableFrom(registeredObject!!.javaClass)) {
                    connection.isAuthenticated = true
                }
            }

            /**
             * Accept the message and process it.
             * @param `request` Request.  In this case a RMIRequest
             * @return the result
             */
            override fun accept(connection: Connection, `object`: Any?): Any? {

                if (`object` is RMIRequest) {
                    val registeredObject = registeredObjects[`object`.instance]

                    if (!verifySession(connection, registeredObject))
                        return InitializationException(InitializationException.CONNECTION_EXCEPTION)

                    val registeredInterface = registeredInterfaces[`object`.instance]

                    // Get the registered value.  If it does not exist, return an exception
                    if (registeredObject == null) return MethodInvocationException()

                    // Find the correct method.  If it doesn't exist, return an exception
                    val method: Method = try {
                        getCorrectMethod(registeredInterface!!, `object`.method)
                    } catch (e: Exception) {
                        return MethodInvocationException(MethodInvocationException.NO_SUCH_METHOD, e)
                    }

                    method.isAccessible = true
                    return try {
                        // Invoke the method
                        val result = method.invoke(registeredObject, *`object`.params!!)
                        checkForAuthentication(registeredObject, connection)
                        result
                    } catch (t: Throwable) {
                        // In some cases an entity exception is expected.  Return that
                        // as the cause rather than wrapping it.
                        if (t.cause is OnyxException) {
                            t.cause
                        } else MethodInvocationException(MethodInvocationException.UNHANDLED_EXCEPTION, t)
                    }

                }
                return null
            }

        }
    }

    /**
     * Find the corresponding method to the proxy value
     *
     * @param clazz  Class to get from
     * @param method Method.  This is a byte index of the method in alphabetical order
     * iterating through all the properties and parameters.
     * @return Method if it exist
     * @since 1.2.0
     * @since 1.3.0 Changed to use the byte index of a method rather than its string value for
     * better optimization.  Also added a map to track
     */
    private fun getCorrectMethod(clazz: Class<*>, method: Byte): Method =
        methodCache.getOrPut(clazz) {
            val methods = clazz.declaredMethods.toMutableList()
            methods.sortBy { it.toString() }
            return@getOrPut methods
        }[method.toInt()]


    /**
     * Register an value within the server as a remote value that can be proxy'd by the client
     *
     * @param remoteId Instance name of the remote value
     * @param object   instance
     * @since 1.2.0
     */
    fun register(remoteId: String, `object`: Any, interfaceToRegister: Class<*>) {
        registeredObjects[remoteId] = `object`
        registeredInterfaces[remoteId] = interfaceToRegister
    }

    /**
     * Remove the registered proxy value
     *
     * @param name instance name
     * @since 1.2.0
     */
    @Suppress("UNUSED")
    fun deregister(name: String) {
        registeredObjects.remove(name)
        registeredInterfaces.remove(name)
    }

    /**
     * Get registered instance by key
     *
     * @param name key
     * @return The registered instance
     */
    @Suppress("UNUSED")
    fun getRegisteredInstance(name: String): Any = registeredObjects[name]!!

    // region Push and Subscribers

    // Registered Push subscribers
    private val pushSubscribers = OptimisticLockingMap<PushSubscriber, PushSubscriber>(java.util.HashMap())

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
     * @param connection Connection information
     *
     * @since 1.3.0 Push notifications were introduced
     */
    private suspend fun handlePushSubscription(message: RequestToken, connection: Connection) {
        val subscriber = message.packet as PushSubscriber
        // Register subscriber
        if (subscriber.subscribeEvent == REMOVE_SUBSCRIBER_EVENT) {
            subscriber.connection = connection
            subscriber.setPushPublisher(this)
            subscriber.pushObjectId = pushSubscriberId.incrementAndGet()
            message.packet = subscriber.pushObjectId
            pushSubscribers[subscriber] = subscriber
        } else if (subscriber.subscribeEvent == REGISTER_SUBSCRIBER_EVENT) {
            // Remove subscriber
            pushSubscribers.remove(subscriber)
        }

        write(connection, message)
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
        serviceScope.launch {
            if (pushSubscriber.connection?.connection?.isActive == true) {
                pushSubscriber.packet = message
                write(pushSubscriber.connection!!, RequestToken(PUSH_NOTIFICATION, pushSubscriber))
            } else {
                deRegisterSubscriberIdentity(pushSubscriber) // Clean up non connected subscribers if not connected
            }
        }
    }

    /**`
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

    /**
     * Write a message to the socket channel
     *
     * @param connection Connection Buffer Pool
     * @param request              Network request.
     * @since 1.2.0
     */
    private suspend fun write(connection: Connection, request: RequestToken) {
        connection.connection.send(Frame.Binary(true, serializer.serialize(request)))
    }

    // region Server Communication

    private var server: EmbeddedServer<*, *>? = null

    /**
     * Start KTOR Server.
     *
     * @since 3.4.4 This was refactored to use KTOR and has a keepAlive feature which is enabled by default
     */
    fun start() {
        server = embeddedServer(Netty, port = this.port, host = "0.0.0.0") {
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            routing {
                webSocket("/") {
                    val connection = Connection(this)
                    connections.add(connection)

                    try {

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Binary -> {
                                    launch {
                                        val requestToken = serializer.deserialize<RequestToken>(ByteBuffer.wrap(frame.data))
                                        handleMessage(connection, requestToken)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    } catch (ignore: CancellationException){} finally {
                        connections.remove(connection)
                    }
                }
            }
        }.start(wait = false)
    }

    private var running = true

    /**
     * Stop server
     */
    fun stop() {
        running = false
        server?.stop()
    }

    /**
     * Join the server and wait until it is done running
     */
    fun join() {
        server?.addShutdownHook {
            running = false
        }
        while (running) {
            Thread.sleep(100)
        }
    }

    /**
     * Handle an inbound message
     *
     * @param connection Connection information containing buffer and thread info
     * @param message              Network message containing packet segments
     * @since 1.2.0
     */
    private suspend fun handleMessage(connection: Connection, message: RequestToken) {
        try {
            message.apply {
                when (packet) {
                    is PushSubscriber -> handlePushSubscription(this, connection)
                    else -> {
                        packet = try {
                            requestHandler.accept(connection, packet)
                        } catch (e: Exception) {
                            MethodInvocationException(MethodInvocationException.UNHANDLED_EXCEPTION, e)
                        }

                        write(connection, this)
                    }
                }
            }
        } catch (e:Exception) {
            failure(e)
        }
    }

    /**
     * Failure within the server.  This should be logged
     *
     * @param cause     The underlying exception
     * @since 1.2.0
     */
    private fun failure(cause: Exception) {
        if (cause !is InitializationException)
            cause.printStackTrace()
    }

    // endregion Ktor

    companion object {
        const val REMOVE_SUBSCRIBER_EVENT = 1.toByte()
        const val REGISTER_SUBSCRIBER_EVENT = 2.toByte()
    }
}
