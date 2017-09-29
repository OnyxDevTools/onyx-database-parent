package com.onyx.buffer

import com.onyx.exception.BufferingException
import com.onyx.extension.common.getFields
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
     * Read from buffer stream.  By default this will use fields and read the buffer default object types
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
     * Write to a buffer stream.  By default this will iterate through an object's fields and
     * write each as an object
     *
     * @param buffer Buffer IO Stream to write to
     * @throws BufferingException Generic IO Exception from the expandableByteBuffer
     * @since 1.1.0
     * @since 2.0.0 Take advantage of default interface implementation
     */
    @Throws(BufferingException::class)
    fun write(buffer: BufferStream) {
        val fields = getFields()
        fields.forEach { buffer.putObject(it.field.get(this)) }
    }

    /**
     * Write to a buffer stream with a schema context.  This is used for persisting entities to a store.  The purpose
     * is to enable versioning of entities.  The schema context is used to find the SystemEntity version.
     *
     * @param buffer Buffer stream to write to
     * @param context Schema context used to pull system entity version
     *
     * @since 2.0.0
     */
    @Throws(BufferingException::class)
    fun write(buffer: BufferStream, context: SchemaContext?) = write(buffer)

    /**
     * Read from a buffer stream with a schema context.
     *
     * @param buffer Buffer stream to read from
     * @param context Schema context used to pull system entity version
     *
     * @since 2.0.0
     */
    @Throws(BufferingException::class)
    fun read(buffer: BufferStream, context: SchemaContext?) = read(buffer)

}
