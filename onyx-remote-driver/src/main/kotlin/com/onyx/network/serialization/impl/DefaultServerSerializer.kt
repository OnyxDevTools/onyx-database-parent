package com.onyx.network.serialization.impl

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.network.serialization.ServerSerializer
import com.onyx.exception.BufferingException

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
     * @param inputBuffer Buffer to put results in
     * @since 1.2.0
     */
    @Throws(BufferingException::class)
    override fun serialize(serializable: BufferStreamable, inputBuffer: ByteBuffer): ByteBuffer {
        var inputBuffer = inputBuffer
        val stream = BufferStream(inputBuffer)
        serializable.write(stream)

        inputBuffer = stream.byteBuffer
        inputBuffer.flip()
        return inputBuffer
    }

    /**
     * Deserialize the bytes from the buffer
     * @param buffer Buffer the object lives within
     * @param streamable Streamable object to read from buffer
     * @return The de-serialized object
     * @since 1.2.0
     */
    @Throws(BufferingException::class)
    override fun deserialize(buffer: ByteBuffer, streamable: BufferStreamable): BufferStreamable {
        val stream = BufferStream(buffer)
        streamable.read(stream)
        return streamable
    }
}