package com.onyx.server;

import com.onyx.endpoint.*;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.request.pojo.*;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSockets;

import java.nio.ByteBuffer;

/**
 * Created by timothy.osborn on 4/22/15.
 */
public class DatabaseMessageListener extends AbstractMessageListener implements ServerEndpoint {

    // Embedded Persistence Manager
    final protected PersistenceManager persistenceManager;
    final protected SchemaContext context;

    protected final PersistenceEndpoint persistenceEndpoint;

    /**
     * Constructor with Persistence Manager
     *
     * @param persistenceManager
     */
    public DatabaseMessageListener(final PersistenceManager persistenceManager, final SchemaContext context)
    {
        this.persistenceManager = persistenceManager;
        this.context = context;

        persistenceEndpoint = new PersistenceEndpoint(persistenceManager);

    }

    /**
     * Handle the inbound message token and set the payload to the results
     *
     * @param token
     */
    public void handleToken(RequestToken token, io.undertow.websockets.core.WebSocketChannel channel, WebSocketCallback<Void> callback) {
        Runnable thread = () -> {

            try {

                RequestEndpoint endpoint = RequestEndpoint.values()[token.getEndpoint()];

                switch (endpoint) {
                    case PERSISTENCE:
                        persistenceEndpoint.handleToken(token, channel, callback);
                        break;
                }


                callback.complete(channel, null);

            }
            // Error Handling set payload to exception
            catch (Exception e) {
                token.setPayload(e);
                callback.onError(channel, null, e);
            }
            finally {
                // Send it on its way
                // Re-Serialize the token with the new payload
                try {
                    ByteBuffer returnBuffer = RequestToken.getPacket(token);
                    WebSockets.sendBinary(returnBuffer, channel, callCompletion);
                }
                // Catch all in order to complete the loop
                catch (Throwable e)
                {
                    ByteBuffer returnBuffer = ObjectBuffer.allocate(0);
                    WebSockets.sendBinary(returnBuffer, channel, callCompletion);
                }
            }


        };

        channel.getWorker().submit(thread);

    }
}
