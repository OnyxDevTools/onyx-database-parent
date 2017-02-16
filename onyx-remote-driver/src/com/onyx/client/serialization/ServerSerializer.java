package com.onyx.client.serialization;

import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 7/1/16.
 *
 * This class is the basic interface of serializer that is used by the server.
 */
public interface ServerSerializer
{

    /**
     * Serialize and put the bytes into the input buffer
     * @param serializable Object to serialize
     * @param inputBuffer Buffer to put results in
     *
     * @throws IOException Problem while reading from input stream
     */
    ByteBuffer serialize(BufferStreamable serializable, ByteBuffer inputBuffer) throws BufferingException;

    /**
     * Deserialize the bytes from the buffer
     * @param buffer buffer to read
     * @param streamable Object to apply deserialization to
     * @return Object that was de-serialized
     *
     * @throws IOException Problem while writing to output stream
     */
    BufferStreamable deserialize(ByteBuffer buffer, BufferStreamable streamable) throws BufferingException;
}
