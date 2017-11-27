package com.onyx.network.serialization.impl

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.network.serialization.ServerSerializer

import java.nio.ByteBuffer

/**
 * Created by Tim Osborn on 7/1/16.
 *
 * The default serializer implementation will use basic java serialization and Externalize whenever possible.
 * @since 1.2.0
 */
class DefaultServerSerializer : ServerSerializer {

    /**
     * Serialize and put the bytes into the input buffer
     * @since 1.2.0
     */
    override fun serialize(serializable: BufferStreamable): ByteBuffer = BufferStream.toBuffer(serializable)

    /**
     * Deserialize the bytes from the buffer
     * @param buffer Buffer the object lives within
     * @return The de-serialized object
     * @since 1.2.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : BufferStreamable> deserialize(buffer: ByteBuffer): T = BufferStream.fromBuffer(buffer) as T
}
