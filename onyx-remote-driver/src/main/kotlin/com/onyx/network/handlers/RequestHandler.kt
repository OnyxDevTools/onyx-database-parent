package com.onyx.network.handlers

import com.onyx.network.connection.Connection

/**
 * Created by Tim Osborn on 6/23/16.
 *
 * This class is used as a callback handler for message requests
 */
interface RequestHandler {

    /**
     * Invoked when a message is received from the connection
     * @param object Object sent to the handler
     * @param connection Connection properties in order to validate authentication
     * @return Result of the handlers processing
     *
     * @since 1.2.0
     */
    fun accept(connection: Connection, `object`: Any?): Any?

}
