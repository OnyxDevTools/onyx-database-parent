package com.onyx.diskmap.store

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.persistence.context.SchemaContext
import java.nio.ByteBuffer
import java.util.ArrayDeque

private const val MAX_SERIALIZATION_STREAMS_PER_THREAD = 2
private const val MAX_RETAINED_SERIALIZATION_BUFFER = 64 * 1024
private val serializationStreams = ThreadLocal.withInitial { ArrayDeque<BufferStream>(2) }

private fun borrowSerializationStream(): BufferStream =
    serializationStreams.get().pollFirst()?.also { it.clear() } ?: BufferStream()

private fun releaseSerializationStream(stream: BufferStream) {
    val pool = serializationStreams.get()
    if (stream.byteBuffer.capacity() <= MAX_RETAINED_SERIALIZATION_BUFFER &&
        pool.size < MAX_SERIALIZATION_STREAMS_PER_THREAD
    ) {
        stream.clear()
        pool.offerFirst(stream)
    } else {
        stream.recycle()
    }
}

/**
 * Created by Tim Osborn on 3/27/15.
 *
 * This declares the contract for the volume storage
 */
interface Store {

    /**
     * Getter for file path for store.  If this is in memory, this will be null
     *
     * @return File path
     */
    val filePath: String

    /**
     * Get the Database context for integration with Onyx Database components and serialization
     */
    val context:SchemaContext?

    /**
     * Write a serializable value to
     *
     * @param serializable Object serializable to write to store
     * @param position location to write to
     */
    fun write(serializable: BufferStreamable, position: Long): Int

    /**
     * Write an Byte Buffer to the store.  The buffer must be flipped or the position must be set prior to
     * sending to this method.
     *
     * @param buffer Bytes to write
     * @param position Position within the volume to write to.
     * @return How many bytes were written
     */
    fun write(buffer: ByteBuffer, position: Long): Int

    /**
     * Write a serializable value
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @return Object Buffer contains bytes read
     */
    fun read(position: Long, size: Int): BufferStream?

    /**
     * Read the file channel and put it into a buffer at a position
     *
     * @param buffer   Buffer to put into
     * @param position position in store to read
     */
    fun read(buffer: ByteBuffer, position: Long)

    /**
     * Read a serializable value
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializable value to read into
     * @return same value instance that was sent in.
     */
    fun read(position: Long, size: Int, serializable: BufferStreamable): Any?

    /**
     * Allocates a spot in the file
     *
     * @param size Allocate space within the store.
     * @return position of started allocated bytes
     */
    fun allocate(size: Int): Long

    /** Allocates a fixed-size slot; stores may reserve these in batches. */
    fun allocateSlot(size: Int): Long = allocate(size)

    /** Allocates serialized object bytes; stores may serve these from an extent. */
    fun allocateObject(size: Int): Long = allocate(size)

    /** Allocates a block at an alignment boundary when the store supports it. */
    fun allocateAligned(size: Int, alignment: Int): Long = allocate(size)

    /**
     * Getter for file longSize
     *
     * @return The self tracked size of the storage
     */
    fun getFileSize(): Long

    /**
     * Close file storage
     *
     * @return Whether the store was closed
     */
    fun close(): Boolean

    /**
     * Commit and flush Storage
     */
    fun commit()

    /**
     * Delete File
     *
     */
    fun delete()

    /**
     * Reset the storage so that it has a clean slate
     * and truncates all relative data.
     *
     * @since 1.3.0
     */
    fun reset()

    /**
     * Retrieve an object at position.  This will automatically determine its
     * size and de-serialize the object
     *
     * @param position Position in the store to retrieve object
     * @since 2.0.0
     */
    fun <T> getObject(position: Long):T

    /**
     * Write an object to the store.  First add its size and then the byte value
     * representation of the object.
     *
     * @param value Value to append to the store
     * @since 2.0.0
     */
    fun writeObject(value:Any?): Long {
        if (value == null) {
            val position = allocateObject(Integer.BYTES)
            BufferPool.withIntBuffer {
                it.clear()
                it.putInt(0)
                it.flip()
                write(it, position)
            }
            return position
        }

        val stream = borrowSerializationStream()
        try {
            // Leave room for the payload length so allocation and data use one write.
            stream.byteBuffer.position(Integer.BYTES)
            stream.putObject(value, context)
            stream.flip()
            val valueBuffer = stream.byteBuffer
            valueBuffer.putInt(0, valueBuffer.limit() - Integer.BYTES)
            val position = allocateObject(valueBuffer.remaining())
            write(valueBuffer, position)
            return position
        } finally {
            releaseSerializationStream(stream)
        }
    }

    fun readObject(position: Long, size: Int): BufferStream? =
            read(position, size)
}
