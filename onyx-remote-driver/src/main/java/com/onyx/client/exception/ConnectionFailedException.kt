package com.onyx.client.exception

/**
 * Created by tosborn1 on 2/13/17.
 *
 * Client failed to connect
 */
class ConnectionFailedException(override val message: String? = "") : OnyxServerException(message) {

    companion object {
        @JvmField val CONNECTION_TIMEOUT = "Connection Timeout Occurred"
    }

}
