package com.onyx.client.rmi;

import com.onyx.client.OnyxClient;
import com.onyx.client.CommunicationPeer;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tosborn1 on 6/27/16.
 * <p>
 * The purpose of this is to serve as a thin client for retrieving proxy remote instances from the server
 *
 * @since 1.2.0
 */
public class OnyxRMIClient extends CommunicationPeer implements OnyxClient {

    // Local Cache of Remote Objects
    private final Map<Short, Object> registeredObjects = new HashMap<>();

    /**
     * Get a Remote Proxy Object
     *
     * @param remoteId Instance name of the registered object
     * @param type     The class type of what you are trying to get
     * @return Instance of the remote proxy object
     * @since 1.2.0
     */
    public Object getRemoteObject(final short remoteId, Class type) {

        // Return the registered Object
        if (registeredObjects.containsKey(remoteId))
            return registeredObjects.get(remoteId);

        // Create an array to feed to the Proxy factory
        Class[] interfaces = new Class[1];
        interfaces[0] = type;

        // Instantiate the proxy object and set the request
        Object instance = Proxy.newProxyInstance(type.getClassLoader(), interfaces, (proxy, method, args) -> {
            if(method.getName().equals("toString"))
            {
                return "Proxy Instance";
            }
            RMIRequest request = new RMIRequest(remoteId, method.toString(), args);
            Object result = this.send(request);
            if (result instanceof Exception)
                throw (Exception) result;
            return result;
        });

        // Add it to the local cache
        registeredObjects.put(remoteId, instance);
        return instance;
    }

}
