package com.onyx.endpoint;

import com.onyx.request.pojo.RequestToken;
import io.undertow.websockets.core.WebSocketCallback;

/**
 * Created by timothy.osborn on 5/13/15.
 */
public interface ServerEndpoint
{

    /**
     * Handle Token
     *
     * @param token
     */
    public void handleToken(RequestToken token, io.undertow.websockets.core.WebSocketChannel channel, WebSocketCallback<Void> callback) throws Exception;

}
