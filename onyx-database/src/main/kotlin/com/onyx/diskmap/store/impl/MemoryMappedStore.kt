package com.onyx.diskmap.store.impl

import com.onyx.diskmap.store.Store
import com.onyx.exception.InitializationException
import com.onyx.extension.common.catchAll
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.context.SchemaContext
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

/**
 * Created by Tim Osborn on 3/27/15.
 *
 * This class uses buffers that are mapped to memory rather than a direct file channel
 */
open class MemoryMappedStore : FileChannelStore, Store {

    internal lateinit var slices: OptimisticLockingMap<Int, FileSlice>

    constructor()

    /**
     * Constructor open file
     *
     * @param filePath File location for the store
     */
    constructor(filePath: String, context: SchemaContext?, deleteOnClose: Boolean) : super(filePath, context, deleteOnClose)

    /**
     * Open the data file
     *
     * @param filePath File location for the store
     * @return Whether the file was opened and the first file slice was allocated
     */
    override fun open(filePath: String): Boolean {
        try {

            // Open the file Channel
            super.open(filePath)

            slices = OptimisticLockingMap(WeakHashMap())
            // Load the first chunk into memory
            val buffer = channel!!.map(FileChannel.MapMode.READ_WRITE, 0, bufferSliceSize.toLong())
            slices[0] = FileSlice(buffer)

        } catch (e: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            return false
        }

        return channel!!.isOpen
    }

    /**
     * Close the data file
     *
     * @return  Close the memory mapped file and truncate to get rid of the remainder of allocated space for the last
     * file slice
     */
    override fun close(): Boolean {
        try {
            this.slices.clear()

            if (!deleteOnClose) {
                catchAll {
                    ensureOpen()
                    channel!!.truncate(getFileSize())
                }
            }
            super.close()

            if (deleteOnClose) {
                delete()
            }
            return !this.channel!!.isOpen
        } catch (e: Exception) {
            return false
        }

    }

    /**
     * Write a buffer.  This is a helper function to work with a buffer rather than a FileChannel
     *
     * @param buffer Byte buffer to write
     * @param position position within store to write to
     * @return how many bytes were written
     */
    override fun write(buffer: ByteBuffer, position: Long): Int {

        var currentIndex = position

        while (buffer.hasRemaining()) {

            val bufLocation = getBufferLocation(currentIndex)
            val slice = getBuffer(currentIndex)
            val bufferToWriteTo = slice.buffer

            synchronized(slice) {
                bufferToWriteTo.position(bufLocation)
                while(buffer.hasRemaining() && bufferToWriteTo.hasRemaining()) {
                    bufferToWriteTo.put(buffer.get())
                    currentIndex++
                }
            }
        }

        return (currentIndex - position).toInt()

    }

    /**
     * Read a mem mapped file
     *
     * @param buffer Byte buffer to read
     * @param position within the store
     */
    override fun read(buffer: ByteBuffer, position: Long) {
        var currentIndex = position

        while (buffer.hasRemaining()) {

            val bufLocation = getBufferLocation(currentIndex)
            val slice = getBuffer(currentIndex)
            val bufferToReadFrom = slice.buffer

            synchronized(slice) {
                bufferToReadFrom.position(bufLocation)
                while(buffer.hasRemaining() && bufferToReadFrom.hasRemaining()) {
                    buffer.put(bufferToReadFrom.get())
                    currentIndex++
                }
            }
        }

    }

    /**
     * Get the associated buffer to the position of the file.  So if the position is 2G + it will get the prop
     * er "slice" of the file
     *
     * @param position position within memory mapped store
     * @return The corresponding slice that is at that position
     */
    protected open fun getBuffer(position: Long): FileSlice {

        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)

        var index = 0
        if (position > 0) {
            index = (position / bufferSliceSize).toInt()
        }

        val finalIndex = index

        return slices.getOrPut(index) {
            val offset = bufferSliceSize.toLong() * finalIndex.toLong()
            ensureOpen()
            val buffer = channel!!.map(FileChannel.MapMode.READ_WRITE, offset, bufferSliceSize.toLong())
            FileSlice(buffer!!)
        }
    }

    /**
     * Get the location within the buffer slice
     *
     * @param position Position within the store
     * @return file slice id
     */
    private fun getBufferLocation(position: Long): Int {
        var index = 0
        if (position > 0) {
            index = (position % bufferSliceSize).toInt()
        }
        return index
    }

    /**
     * File Slice
     *
     *
     * This contains the memory mapped segment as well as a lock for it
     */
    class FileSlice(var buffer: ByteBuffer)

    /**
     * Commit storage
     */
    override fun commit() {
        if (!deleteOnClose) {
            super.commit()
        }
    }

    private fun ensureOpen() {
        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
    }
}
