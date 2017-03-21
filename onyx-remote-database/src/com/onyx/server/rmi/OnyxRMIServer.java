package com.onyx.server.rmi;

import com.onyx.client.auth.AuthenticationManager;
import com.onyx.client.base.ConnectionProperties;
import com.onyx.client.exception.MethodInvocationException;
import com.onyx.client.handlers.RequestHandler;
import com.onyx.client.rmi.RMIRequest;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.server.base.CommunicationServer;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.SynchronizedMap;

import javax.net.ssl.SSLException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by tosborn1 on 6/27/16.
 * <p>
 * This server extends the communication server.  It is so that we have the convenience
 * of using Remote method invocation without the overhead of JAVA RMI.  Java RMI makes
 * it so that we do cannot use connect with Android.
 * <p>
 * Another benefit is that the serialization is much more efficient and the buffering is using
 * off heap buffers.  It is for improved scalability.
 *
 * @since 1.2.0
 */
public class OnyxRMIServer extends CommunicationServer {
    // Local Cache of the registered objects
    private final Map<String, Object> registeredObjects = new ConcurrentHashMap<>();
    private final Map<String, Class> registeredInterfaces = new ConcurrentHashMap<>();

    /**
     * Constructor
     * <p>
     * Set the message handler to my own fancy RMI Proxy handler
     */
    public OnyxRMIServer() {
        super();
        /*
          RMI Request handler.  This will grab the correct proxy object,
          execute the method on the proxy object and return the result
         */
        this.requestHandler = new RequestHandler() {

            /**
             * Verify the session is active and valid.  If not, shut it down.
             * @param registeredObject The Proxy class
             * @param connectionProperties Connection information
             * @return Whether the session is valid
             * @since 1.2.0
             */
            @SuppressWarnings("BooleanMethodIsAlwaysInverted")
            private boolean verifySession(ConnectionProperties connectionProperties, Object registeredObject) {
                if (!AuthenticationManager.class.isAssignableFrom(registeredObject.getClass())
                        && !connectionProperties.isAuthenticated()) {
                    try {
                        connectionProperties.packetTransportEngine.closeOutbound();
                        connectionProperties.packetTransportEngine.closeInbound();
                    } catch (SSLException ignore) {
                    }

                    return false;
                }

                return true;
            }

            /**
             * Check to see if it is the authentication RPC.  If so, set the result onto the connection properties so
             * we can keep track of the session.
             *
             * @param registeredObject Proxy object
             * @param connectionProperties Connection information holding authentication information
             *
             * @since 1.2.0
             */
            private void checkForAuthentication(Object registeredObject, ConnectionProperties connectionProperties) {
                if (AuthenticationManager.class.isAssignableFrom(registeredObject.getClass())) {
                    connectionProperties.setAuthenticated(true);
                }
            }

            /**
             * Accept the message and process it.
             * @param object Request.  In this case a RMIRequest
             * @return the result
             */
            @Override
            public Object accept(ConnectionProperties connectionProperties, Object object) {

                if (object instanceof RMIRequest) {
                    final RMIRequest rmiRequest = (RMIRequest) object;
                    final Object registeredObject = registeredObjects.get(rmiRequest.getInstance());

                    if (!verifySession(connectionProperties, registeredObject))
                        return new InitializationException(InitializationException.CONNECTION_EXCEPTION);

                    final Class registeredInterface = registeredInterfaces.get(rmiRequest.getInstance());

                    // Get the registered object.  If it does not exist, return an exception
                    if (registeredObject == null) {
                        return new MethodInvocationException();
                    }

                    // Find the correct method.  If it doesn't exist, return an exception
                    final Method method;
                    try {
                        method = getCorrectMethod(registeredInterface, rmiRequest.getMethod());
                    } catch (Exception e) {
                        return new MethodInvocationException(MethodInvocationException.NO_SUCH_METHOD, e);
                    }

                    if (!method.isAccessible())
                        method.setAccessible(true);
                    try {
                        // Invoke the method
                        Object result = method.invoke(registeredObject, rmiRequest.getParams());
                        checkForAuthentication(registeredObject, connectionProperties);
                        return result;
                    } catch (Throwable t) {
                        // In some cases an entity exception is expected.  Return that
                        // as the cause rather than wrapping it.
                        if (t.getCause() instanceof EntityException) {
                            return t.getCause();
                        }
                        return new MethodInvocationException(MethodInvocationException.UNHANDLED_EXCEPTION, t);
                    }

                }
                return null;
            }

        };
    }

    private final CompatMap<Class, List<Method>> methodCache = new SynchronizedMap<>();

    /**
     * Find the corresponding method to the proxy object
     *
     * @param clazz  Class to get from
     * @param method Method.  This is a string value.  This can prolly be optimized in the future but, its better than
     *               iterating through all the properties and parameters.
     * @return Method if it exist
     * @since 1.2.0
     * @since 1.3.0 Changed to use the byte index of a method rather than its string value for
     *              better optimization.  Also added a map to track
     */
    private Method getCorrectMethod(Class clazz, byte method) {
        return methodCache.computeIfAbsent(clazz, aClass -> {
            Method[] methods = clazz.getDeclaredMethods();
            List<Method> methodList = Arrays.asList(methods);
            Collections.sort(methodList, (o1, o2) -> o1.toString().compareTo(o2.toString()));
            return methodList;
        }).get((int) method);
    }

    /**
     * Register an object within the server as a remote object that can be proxy'd by the clienst
     *
     * @param remoteId Instance name of the remote object
     * @param object   instance
     * @since 1.2.0
     */
    public void register(String remoteId, Object object, Class interfaceToRegister) {
        registeredObjects.put(remoteId, object);
        registeredInterfaces.put(remoteId, interfaceToRegister);
    }

    /**
     * Remove the registered proxy object
     *
     * @param name instance name
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    public void deregister(String name) {
        registeredObjects.remove(name);
        registeredInterfaces.remove(name);
    }

    /**
     * Get registered instance by key
     *
     * @param name key
     * @return The registered instance
     */
    @SuppressWarnings("unused")
    public Object getRegisteredInstance(String name) {
        return registeredObjects.get(name);
    }

}
