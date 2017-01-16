package com.onyx.server;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;

/**
 * Created by timothy.osborn on 4/23/15.
 */
public class DatabaseHandshakeHandler extends WebSocketProtocolHandshakeHandler {

    public DatabaseHandshakeHandler(WebSocketConnectionCallback callback)
    {
        super(callback);
    }

    public DatabaseHandshakeHandler(WebSocketConnectionCallback callback, HttpHandler next)
    {
        super(callback, next);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception
    {

        if (exchange.isInIoThread())
        {
            exchange.dispatch(this);
            return;
        }
        SessionManager sm = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);

        Session session = sm.getSession(exchange, sessionConfig);
        if (session == null)
            session = sm.createSession(exchange, sessionConfig);

        exchange.getResponseHeaders().add(HttpString.tryFromString("auth"), session.getId());

        super.handleRequest(exchange);

    }
}
