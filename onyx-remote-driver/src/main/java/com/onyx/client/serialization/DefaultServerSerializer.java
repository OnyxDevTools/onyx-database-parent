package com.onyx.client.serialization;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;

import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 7/1/16.
 *
 * The default serializer implementation will use basic java serialization and Externalize whenever possible.
 * @since 1.2.0
 */
public class DefaultServerSerializer implements ServerSerializer
{

    /**
     * Serialize and put the bytes into the input buffer
     * @param inputBuffer Buffer to put results in
     * @since 1.2.0
     */
    public ByteBuffer serialize(BufferStreamable serializable, ByteBuffer inputBuffer) throws BufferingException
    {
        BufferStream stream = new BufferStream(inputBuffer);
        serializable.write(stream);

        inputBuffer = stream.getByteBuffer();
        inputBuffer.limit(inputBuffer.position());
        inputBuffer.rewind();
        return inputBuffer;
    }

    /**
     * Deserialize the bytes from the buffer
     * @param buffer Buffer the object lives within
     * @return The de-serialized object
     * @since 1.2.0
     */
    public BufferStreamable deserialize(ByteBuffer buffer, BufferStreamable token) throws BufferingException
    {
        BufferStream stream = new BufferStream(buffer);
        token.read(stream);
        return token;
    }
}
