package com.onyx.network.serialization

import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException

import java.nio.ByteBuffer

/**
 * Created by Tim Osborn on 7/1/16.
 *
 * This class is the basic interface of serializer that is used by the server.
 */
interface ServerSerializer {

    /**
     * Serialize and put the bytes into the input buffer
     * @param serializable Object to serialize
     */
    @Throws(BufferingException::class)
    fun serialize(serializable: BufferStreamable): ByteBuffer

    /**
     * Deserialize the bytes from the buffer
     * @param buffer buffer to read
     * @return Object that was de-serialized
     *
     */
    fun <T : BufferStreamable> deserialize(buffer: ByteBuffer): T
}
