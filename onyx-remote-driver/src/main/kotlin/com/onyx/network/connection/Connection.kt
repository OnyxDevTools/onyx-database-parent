package com.onyx.network.connection

import io.ktor.websocket.*


/**
 * Created by Tim Osborn on 2/12/17.
 *
 * This contains the connection information including the buffers and the thread pool
 * that each connection is assigned to.
 * @since 1.2.0
 */
data class Connection(
    val connection: DefaultWebSocketSession
) {
    var isAuthenticated = false
}