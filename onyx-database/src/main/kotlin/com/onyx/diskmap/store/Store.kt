package com.onyx.diskmap.store

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.extension.withBuffer
import com.onyx.persistence.context.SchemaContext
import java.nio.ByteBuffer

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
            return this.allocate(Integer.BYTES)
        } else {
            val stream = BufferStream()
            stream.putObject(value, context)
            stream.flip()
            return withBuffer(stream.byteBuffer) { valueBuffer ->
                val size = valueBuffer.limit()
                val position = this.allocate(size + Integer.BYTES)

                BufferPool.withIntBuffer {
                    it.putInt(size)
                    it.rewind()
                    this.write(it, position)
                }

                this.write(valueBuffer, position + Integer.BYTES)

                return@withBuffer position
            }
        }
    }

    fun readObject(position: Long, size: Int): BufferStream? =
            read(position, size)
}