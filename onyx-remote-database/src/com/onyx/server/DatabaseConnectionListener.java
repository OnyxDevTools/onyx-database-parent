package com.onyx.server;

import com.onyx.config.ContextFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * Created by timothy.osborn on 4/20/15.
 */
public class DatabaseConnectionListener implements WebSocketConnectionCallback {

    final private PersistenceManager persistenceManager;

    final private SchemaContext context;

    final private ContextFactory serverContextFactory;

    public DatabaseConnectionListener(final PersistenceManager persistenceManager, final SchemaContext context, final ContextFactory serverContextFactory)
    {
        this.persistenceManager = persistenceManager;
        this.context = context;
        this.serverContextFactory = serverContextFactory;
    }

    @Override
    public void onConnect(WebSocketHttpExchange webSocketHttpExchange, WebSocketChannel webSocketChannel)
    {
        webSocketChannel.getReceiveSetter().set(new DatabaseMessageListener(persistenceManager, context));
        webSocketChannel.resumeReceives();
    }

}