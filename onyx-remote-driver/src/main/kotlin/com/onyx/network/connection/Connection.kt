package com.onyx.network.connection

import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

/**
 * Created by Tim Osborn on 2/12/17.
 *
 * This contains the connection information including the buffers and the thread pool
 * that each connection is assigned to.
 * @since 1.2.0
 */
class Connection(val socketChannel: ByteChannel) {
    var isAuthenticated = false
    var lastReadPacket:ByteBuffer? = null
}