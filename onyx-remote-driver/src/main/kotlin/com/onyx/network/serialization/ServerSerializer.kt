package com.onyx.network.serialization

import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException

import java.nio.ByteBuffer

/**
 * Created by tosborn1 on 7/1/16.
 *
 * This class is the basic interface of serializer that is used by the server.
 */
interface ServerSerializer {

    /**
     * Serialize and put the bytes into the input buffer
     * @param serializable Object to serialize
     * @param inputBuffer Buffer to put results in
     *
     * @throws BufferingException Problem while reading from input stream
     */
    @Throws(BufferingException::class)
    fun serialize(serializable: BufferStreamable, inputBuffer: ByteBuffer): ByteBuffer

    /**
     * Deserialize the bytes from the buffer
     * @param buffer buffer to read
     * @param streamable Object to apply deserialization to
     * @return Object that was de-serialized
     *
     * @throws BufferingException Problem while writing to output stream
     */
    @Throws(BufferingException::class)
    fun deserialize(buffer: ByteBuffer, streamable: BufferStreamable): BufferStreamable
}
