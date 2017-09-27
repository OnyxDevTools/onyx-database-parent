package com.onyx.diskmap.store

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.persistence.context.SchemaContext
import java.nio.ByteBuffer

/**
 * Created by tosborn on 3/27/15.
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
     * Write a serializable object to
     *
     * @param serializable Object serializable to write to store
     * @param position location to write to
     */
    fun write(serializable: BufferStreamable, position: Long): Int

    /**
     * Write a serializable object
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
     * Write an Object Buffer
     *
     * @param buffer Byte buffer to write
     * @param position Position within the volume to write to.
     * @return How many bytes were written
     */
    fun write(buffer: ByteBuffer, position: Long): Int

    /**
     * Read a serializable object from the store
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param type class type
     * @return The object that was read from the store
     */
    fun read(position: Long, size: Int, type: Class<*>): Any?

    /**
     * Read a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializable object to read into
     * @return same object instance that was sent in.
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

}