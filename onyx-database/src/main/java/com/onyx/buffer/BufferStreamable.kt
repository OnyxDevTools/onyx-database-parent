package com.onyx.buffer

import com.onyx.exception.BufferingException
import com.onyx.extension.common.getFields
import com.onyx.extension.get
import com.onyx.extension.set
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext

import java.io.Serializable

/**
 * Created by tosborn1 on 7/30/16.
 *
 * This interface is intended to enable an object for expandableByteBuffer io.  It works much like the Externalizable interface
 * except without using input and output streams it uses a ByteBuffer.  The IO is wrapped within the BufferStream.
 */
interface BufferStreamable : Serializable {

    /**
     * Read from the expandableByteBuffer expandableByteBuffer to get the objects.
     *
     * @param buffer Buffer Stream to read from
     * @throws BufferingException Generic IO Exception from the expandableByteBuffer
     * @since 1.1.0
     */
    @Throws(BufferingException::class)
    fun read(buffer: BufferStream) {
        val fields = getFields()
        fields.forEach { it.field.set(this, buffer.`object`) }
    }

    /**
     * Write to the expandableByteBuffer expandableByteBuffer
     *
     * @param buffer Buffer IO Stream to write to
     * @throws BufferingException Generic IO Exception from the expandableByteBuffer
     * @since 1.1.0
     */
    @Throws(BufferingException::class)
    fun write(buffer: BufferStream) {
        val fields = getFields()
        fields.forEach { buffer.putObject(it.field.get(this)) }
    }

    @Throws(BufferingException::class)
    fun write(buffer: BufferStream, context: SchemaContext?) {
        write(buffer)
    }

    @Throws(BufferingException::class)
    fun read(buffer: BufferStream, context: SchemaContext?) {
        read(buffer)
    }
}
