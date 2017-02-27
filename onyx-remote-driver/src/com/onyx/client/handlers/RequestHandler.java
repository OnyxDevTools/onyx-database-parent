package com.onyx.client.handlers;

import com.onyx.client.base.ConnectionProperties;

/**
 * Created by tosborn1 on 6/23/16.
 *
 * This class is used as a callback handler for message requests
 */
public interface RequestHandler
{

    /**
     * Invoked when a message is received from the connection
     * @param object Object sent to the handler
     * @param connectionProperties Connection properties in order to validate authentication
     * @return Result of the handlers processing
     *
     * @since 1.2.0
     */
    Object accept(ConnectionProperties connectionProperties, Object object);

}
