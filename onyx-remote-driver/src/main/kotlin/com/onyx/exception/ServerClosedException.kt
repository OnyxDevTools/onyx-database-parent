package com.onyx.exception

/**
 * Created by Tim Osborn on 6/25/16.
 *
 * This exception indicates the server has closed
 */
class ServerClosedException(cause: Throwable) : OnyxServerException(SERVER_CLOSED_MESSAGE, cause) {
    companion object {
        private val SERVER_CLOSED_MESSAGE = "Client connection closed prematurely."
    }
}
