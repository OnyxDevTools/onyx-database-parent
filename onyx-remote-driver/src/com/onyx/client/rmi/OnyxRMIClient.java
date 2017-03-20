package com.onyx.client.rmi;

import com.onyx.client.CommunicationPeer;
import com.onyx.client.OnyxClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Created by tosborn1 on 6/27/16.
 * <p>
 * The purpose of this is to serve as a thin client for retrieving proxy remote instances from the server
 *
 * @since 1.2.0
 */
public class OnyxRMIClient extends CommunicationPeer implements OnyxClient {

    // Local Cache of Remote Objects
    private final Map<String, Object> registeredObjects = new HashMap<>();

    /**
     * Get a Remote Proxy Object
     *
     * @param remoteId Instance name of the registered object
     * @param type     The class type of what you are trying to get
     * @return Instance of the remote proxy object
     * @since 1.2.0
     */
    public Object getRemoteObject(final String remoteId, Class type) {

        // Return the registered Object
        if (registeredObjects.containsKey(remoteId))
            return registeredObjects.get(remoteId);

        // Create an array to feed to the Proxy factory
        Class[] interfaces = new Class[1];
        interfaces[0] = type;

        Object instance = Proxy.newProxyInstance(type.getClassLoader(), interfaces, new RMIClientInvocationHander(type, remoteId));

        // Add it to the local cache
        registeredObjects.put(remoteId, instance);
        return instance;
    }

    /**
     * This class is added in order to support tracking of methods.
     * Rather than sending in the string value of a method, this is optimized
     * to use the sorted index of a method so the packet is reduced from
     * a string to a single byte.
     *
     * @since 1.3.0
     */
    private class RMIClientInvocationHander implements InvocationHandler {
        List<Method> methods = new ArrayList<>();
        final String remoteId;

        @SuppressWarnings("unchecked")
        RMIClientInvocationHander(Class type, String remoteId) {
            Method[] methodArray = type.getDeclaredMethods();
            this.methods = Arrays.asList(methodArray);
            Collections.sort(this.methods, (o1, o2) -> o1.toString().compareTo(o2.toString()));
            this.remoteId = remoteId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            RMIRequest request = new RMIRequest(remoteId, (byte) methods.indexOf(method), args);
            Object result = send(request);
            if (result instanceof Exception)
                throw (Exception) result;
            return result;
        }
    }
}
