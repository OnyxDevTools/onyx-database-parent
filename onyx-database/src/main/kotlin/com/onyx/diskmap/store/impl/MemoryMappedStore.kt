package com.onyx.diskmap.store.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.async
import com.onyx.extension.common.catchAll
import com.onyx.extension.perform
import com.onyx.extension.withBuffer
import com.onyx.persistence.context.SchemaContext
import com.onyx.depricated.ReflectionUtil

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Created by tosborn on 3/27/15.
 *
 * This class uses buffers that are mapped to memory rather than a direct file channel
 */
open class MemoryMappedStore : FileChannelStore, Store {

    internal lateinit var slices: HashMap<Int, FileSlice>

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
    @Synchronized
    override fun open(filePath: String): Boolean {
        try {

            // Open the file Channel
            super.open(filePath)

            slices = HashMap()
            // Load the first chunk into memory
            val buffer = channel!!.map(FileChannel.MapMode.READ_WRITE, 0, bufferSliceSize.toLong())
            synchronized(slices) {
                slices.put(0, FileSlice(buffer))
            }

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
    @Synchronized override fun close(): Boolean {
        try {

            if (!deleteOnClose) {
                catchAll {
                    channel!!.truncate(getFileSize())
                }
            }

            async {
                synchronized(slices) {
                    slices.values.forEach { it.flush() }
                    slices.clear()
                }
                channel!!.close()
                if (deleteOnClose) {
                    delete()
                }
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

        val slice = getBuffer(position)

        val bufLocation = getBufferLocation(position)
        val endBufLocation = bufLocation + buffer.limit()

        // This occurs when we bridge from one slice to another
        if (endBufLocation > bufferSliceSize) {
            val overflowSlice = getBuffer(position + buffer.limit())

            synchronized(slice) {
                slice.buffer.position(bufLocation)
                for (i in 0 until bufferSliceSize - bufLocation)
                    slice.buffer.put(buffer.get())
            }
            synchronized(overflowSlice) {
                overflowSlice.buffer.position(0)
                for (i in 0 until endBufLocation - bufLocation - (bufferSliceSize - bufLocation))
                    overflowSlice.buffer.put(buffer.get())
            }
            return buffer.position()
        } else {
            return synchronized(slice) {
                slice.buffer.position(getBufferLocation(position))
                slice.buffer.put(buffer)
                return@synchronized buffer.position()
            }
        }
    }

    /**
     * Read a mem mapped file
     *
     * @param buffer Byte buffer to read
     * @param position within the store
     */
    override fun read(buffer: ByteBuffer, position: Long) {

        val slice = getBuffer(position)

        val bufLocation = getBufferLocation(position)
        val endBufLocation = bufLocation + buffer.limit()

        // This occurs when we bridge from one slice to another
        if (endBufLocation >= bufferSliceSize) {
            val overflowSlice = getBuffer(position + buffer.limit())

            synchronized(slice) {
                slice.buffer.position(bufLocation)
                for (i in 0 until bufferSliceSize - bufLocation)
                    buffer.put(slice.buffer.get())
            }
            synchronized(overflowSlice) {
                overflowSlice.buffer.position(0)
                for (i in 0 until endBufLocation - bufLocation - (bufferSliceSize - bufLocation))
                    buffer.put(overflowSlice.buffer.get())
            }
        } else {
            synchronized(slice) {
                slice.buffer.position(getBufferLocation(position))
                val bytesToRead = buffer.limit()
                for (i in 0 until bytesToRead)
                    buffer.put(slice.buffer.get())
            }
        }
    }

    /**
     * Write a serializable value
     *
     * @param position within the store
     * @param size Amount of bytes to read
     * @return Object buffer that was read
     */
    override fun read(position: Long, size: Int): BufferStream? {
        if (!validateFileSize(position))
            return null

        val buffer = BufferPool.allocateAndLimit(size)
        this.read(buffer, position)
        buffer.flip()

        return BufferStream(buffer)
    }

    /**
     * Get the associated buffer to the position of the file.  So if the position is 2G + it will get the prop
     * er "slice" of the file
     *
     * @param position position within memory mapped store
     * @return The corresponding slice that is at that position
     */
    protected open fun getBuffer(position: Long): FileSlice {

        var index = 0
        if (position > 0) {
            index = (position / bufferSliceSize).toInt()
        }

        val finalIndex = index

        return synchronized(slices) {
            slices.getOrPut(index) {
                val offset = bufferSliceSize.toLong() * finalIndex.toLong()
                val buffer = channel!!.map(FileChannel.MapMode.READ_WRITE, offset, bufferSliceSize.toLong())
                FileSlice(buffer!!)
            }
        }
    }

    /**
     * Write a serializable value to
     *
     * @param serializable Object serializable to write to store
     * @param position location to write to
     */
    override fun write(serializable: BufferStreamable, position: Long): Int {
        val stream = BufferStream()

        try {
            serializable.write(stream)
            stream.flip()
            return this.write(stream.byteBuffer, position)
        }
        finally {
            stream.recycle()
        }
    }

    /**
     * Read a serializable value
     *
     * @param position position within store
     * @param size size of value to read
     * @param type Type of value to assign value to
     * @return Instantiated value of type
     */
    override fun read(position: Long, size: Int, type: Class<*>): Any? {
        if (!validateFileSize(position))
            return null

        val buffer = BufferStream(size)

        return buffer.perform {
            this.read(buffer.byteBuffer, position)
            buffer.flip()

            return@perform when {
                BufferStreamable::class.java.isAssignableFrom(type) -> {
                    val streamable:BufferStreamable = ReflectionUtil.instantiate(type)
                    streamable.read(buffer, context)
                    streamable
                }
                else -> buffer.value
            }
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
     * Read a serializable value
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializable value to read into
     * @return same value instance that was sent in.
     */
    override fun read(position: Long, size: Int, serializable: BufferStreamable): Any? {
        if (!validateFileSize(position))
            return null

        val buffer = BufferPool.allocateAndLimit(size)
        return withBuffer(buffer) {
            read(buffer, position)
            buffer.flip()
            serializable.read(BufferStream(buffer))
            return@withBuffer serializable
        }
    }

    /**
     * File Slice
     *
     *
     * This contains the memory mapped segment as well as a lock for it
     */
    class FileSlice constructor(var buffer: ByteBuffer) {


        /* Hack to unmap MappedByteBuffer.
        * Unmap is necessary on Windows, otherwise file is locked until JVM exits or BB is GCed.
        * There is no public JVM API to unmap buffer, so this tries to use SUN proprietary API for unmap.
        * Any error is silently ignored (for example SUN API does not exist on Android).
        */
        fun flush() {

        }
    }

    /**
     * Commit storage
     */
    override fun commit() {
        if (!deleteOnClose) {
            synchronized(slices) {
                this.slices.values
                        .filter { it.buffer is MappedByteBuffer }
                        .forEach { catchAll { (it.buffer as MappedByteBuffer).force()  } }
            }
        }
    }
}

