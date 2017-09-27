package com.onyx.buffer;

import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;

import java.io.Serializable;

/**
 * Created by tosborn1 on 7/30/16.
 *
 * This interface is intended to enable an object for expandableByteBuffer io.  It works much like the Externalizable interface
 * except without using input and output streams it uses a ByteBuffer.  The IO is wrapped within the BufferStream.
 */
public interface BufferStreamable extends Serializable {

    /**
     * Read from the expandableByteBuffer expandableByteBuffer to get the objects.
     *
     * @param buffer Buffer Stream to read from
     * @throws BufferingException Generic IO Exception from the expandableByteBuffer
     * @since 1.1.0
     */
    @SuppressWarnings("unused")
    void read(BufferStream buffer) throws BufferingException;

    /**
     * Write to the expandableByteBuffer expandableByteBuffer
     *
     * @param buffer Buffer IO Stream to write to
     * @throws BufferingException Generic IO Exception from the expandableByteBuffer
     * @since 1.1.0
     */
    @SuppressWarnings("unused")
    void write(BufferStream buffer) throws BufferingException;

    default void write(BufferStream buffer, SchemaContext context) throws BufferingException{
        write(buffer);
    }

    default void read(BufferStream buffer, SchemaContext context) throws BufferingException{
        read(buffer);
    }
}
