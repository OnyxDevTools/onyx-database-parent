package com.onyx.server.rmi

import com.onyx.client.SSLPeer
import com.onyx.client.auth.AuthenticationManager
import com.onyx.client.base.ConnectionProperties
import com.onyx.client.exception.MethodInvocationException
import com.onyx.client.handlers.RequestHandler
import com.onyx.client.rmi.RMIRequest
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.server.base.CommunicationServer

import java.lang.reflect.Method
import kotlin.collections.HashMap

/**
 * Created by tosborn1 on 6/27/16.
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
class OnyxRMIServer : CommunicationServer(), SSLPeer {

    // Local Cache of the registered objects
    private val registeredObjects = OptimisticLockingMap<String, Any>(HashMap())
    private val registeredInterfaces = OptimisticLockingMap<String, Class<*>>(HashMap())
    private val methodCache = OptimisticLockingMap<Class<*>, MutableList<Method>>(HashMap())

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
             * @param connectionProperties Connection information
             * @return Whether the session is valid
             * @since 1.2.0
             */
            private fun verifySession(connectionProperties: ConnectionProperties, registeredObject: Any?): Boolean {
                if (!AuthenticationManager::class.java.isAssignableFrom(registeredObject!!.javaClass) && !connectionProperties.isAuthenticated) {
                    connectionProperties.packetTransportEngine.closeOutbound()
                    connectionProperties.packetTransportEngine.closeInbound()
                    return false
                }
                return true
            }

            /**
             * Check to see if it is the authentication RPC.  If so, set the result onto the connection properties so
             * we can keep track of the session.
             *
             * @param registeredObject Proxy value
             * @param connectionProperties Connection information holding authentication information
             *
             * @since 1.2.0
             */
            private fun checkForAuthentication(registeredObject: Any?, connectionProperties: ConnectionProperties) {
                if (AuthenticationManager::class.java.isAssignableFrom(registeredObject!!.javaClass)) {
                    connectionProperties.isAuthenticated = true
                }
            }

            /**
             * Accept the message and process it.
             * @param `request` Request.  In this case a RMIRequest
             * @return the result
             */
            override fun accept(connectionProperties: ConnectionProperties, request: Any): Any? {

                if (request is RMIRequest) {
                    val registeredObject = registeredObjects[request.instance]

                    if (!verifySession(connectionProperties, registeredObject))
                        return InitializationException(InitializationException.CONNECTION_EXCEPTION)

                    val registeredInterface = registeredInterfaces[request.instance]

                    // Get the registered value.  If it does not exist, return an exception
                    if (registeredObject == null) return MethodInvocationException()

                    // Find the correct method.  If it doesn't exist, return an exception
                    val method: Method
                    try {
                        method = getCorrectMethod(registeredInterface!!, request.method)
                    } catch (e: Exception) {
                        return MethodInvocationException(MethodInvocationException.NO_SUCH_METHOD, e)
                    }

                    if (!method.isAccessible)
                        method.isAccessible = true
                    return try {
                        // Invoke the method
                        val result = method.invoke(registeredObject, *request.params)
                        checkForAuthentication(registeredObject, connectionProperties)
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
     * @param method Method.  This is a string value.  This can prolly be optimized in the future but, its better than
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
     * Register an value within the server as a remote value that can be proxy'd by the clienst
     *
     * @param remoteId Instance name of the remote value
     * @param object   instance
     * @since 1.2.0
     */
    fun register(remoteId: String, `object`: Any, interfaceToRegister: Class<*>) {
        registeredObjects.put(remoteId, `object`)
        registeredInterfaces.put(remoteId, interfaceToRegister)
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

}
