package com.onyx.server;

import com.onyx.client.DefaultDatabaseEndpoint;
import com.onyx.request.pojo.RequestToken;
import io.undertow.websockets.core.*;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by timothy.osborn on 5/11/15.
 */
public abstract class AbstractMessageListener extends AbstractReceiveListener {


    /**
     * On Full Binary Message - Main Entry point for RPC
     *
     * @param channel
     * @param message
     * @throws java.io.IOException
     */
    @Override
    final protected void onFullBinaryMessage(io.undertow.websockets.core.WebSocketChannel
                                                     channel, io.undertow.websockets.core.BufferedBinaryMessage message) throws java.io.IOException {

        try {

            ByteBuffer buffer = null;

            if (message.isComplete()) {
                // Get the message binary data by merging all the buffers used in the buffer pool
                buffer = WebSockets.mergeBuffers(message.getData().getResource());

                // De-serialize the request token
                while (buffer.position() < buffer.limit()) {
                    final RequestToken token = RequestToken.getToken(buffer);

                    // Handle the request
                    handleToken(token, channel, callCompletion);

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            message.getData().free();
        }
    }

    abstract void handleToken(RequestToken token, io.undertow.websockets.core.WebSocketChannel channel, WebSocketCallback<Void> callback);


    /**
     * Call completion handler
     */
    final WebSocketCallback<Void> callCompletion = new WebSocketCallback<Void>() {
        @Override
        public void complete(WebSocketChannel webSocketChannel, Void aVoid) {

        }

        @Override
        public void onError(WebSocketChannel webSocketChannel, Void aVoid, Throwable throwable) {
            // TODO: Log and or retry
        }
    };
}
