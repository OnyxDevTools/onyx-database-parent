package com.onyx.server;

import com.onyx.endpoint.ServerEndpoint;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * Created by timothy.osborn on 4/20/15.
 */
public class DefaultConnectionListener implements WebSocketConnectionCallback {

    private final ServerEndpoint serverEndpoint;

    public DefaultConnectionListener(ServerEndpoint serverEndpoint)
    {
        this.serverEndpoint = serverEndpoint;
    }

    @Override
    public void onConnect(WebSocketHttpExchange webSocketHttpExchange, WebSocketChannel webSocketChannel)
    {
        webSocketChannel.getReceiveSetter().set(new DefaultMessageListener(serverEndpoint));
        webSocketChannel.resumeReceives();
    }
}