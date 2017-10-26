package com.onyx.exception

/**
 * Created by Tim Osborn on 2/13/17.
 *
 * Client failed to connect
 */
class ConnectionFailedException(override var message: String = "") : OnyxServerException(message) {

    companion object {
        @JvmField val CONNECTION_TIMEOUT = "Connection Timeout Occurred"
    }
}
