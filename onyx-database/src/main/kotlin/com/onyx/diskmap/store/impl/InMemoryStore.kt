package com.onyx.diskmap.store.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.copy
import com.onyx.diskmap.store.Store
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

/**
 * Created by Tim Osborn on 3/27/15.
 *
 * Rather than writing to a file, this writes to memory.
 */
class InMemoryStore(context: SchemaContext?, storeId: String) : MemoryMappedStore(), Store {

    private var slices: MutableMap<Int, ByteBuffer> = OptimisticLockingMap(HashMap())

    init {
        this.contextId = context?.contextId
        this.contextReference = contextId?.let { WeakReference(Contexts.get(it)) }
        open(storeId)
        this.determineSize()
    }

    /**
     * Open the data file
     *
     * @param filePath  Ignored.  There is no file to open.  Should be blank
     * @return Always true
     */
    override fun open(filePath: String): Boolean {

        this.filePath = filePath
        slices = OptimisticLockingMap(HashMap())

        // Lets open the memory mapped files in 2Gig increments since on 32 bit machines the max is I think 2G.  Also buffers are limited by
        // using an int for position.  We are gonna bust that.
        slices[0] = BufferPool.allocateAndLimit(bufferSliceSize)
        return true
    }

    /**
     * Calculates the location within a buffer slice for a given absolute file position.
     * @param position The absolute file position.
     * @return The relative position within a buffer slice.
     */
    private fun getBufferLocation(position: Long) = (position % bufferSliceSize).toInt()

    /**
     * Writes data from the source buffer to the store at the specified position.
     * @param buffer The buffer containing the data to write.
     * @param position The position in the store to write to.
     * @return The number of bytes written.
     */
    override fun write(buffer: ByteBuffer, position: Long): Int {
        var current = position
        while (buffer.hasRemaining()) {
            val destination = getBuffer(current)
            synchronized(destination) {
                destination.position(getBufferLocation(current))
                current += copy(buffer, destination)
            }
        }
        return (current - position).toInt()
    }

    /**
     * Reads data from the store at the specified position into the destination buffer.
     * @param buffer The buffer to read data into.
     * @param position The position in the store to read from.
     */
    override fun read(buffer: ByteBuffer, position: Long) {
        var current = position
        while (buffer.hasRemaining()) {
            val source = getBuffer(current)
            synchronized(source) {
                source.position(getBufferLocation(current))
                current += copy(source, buffer)
            }
        }
    }

    /**
     * Get the associated buffer to the position of the file.  So if the position is 2G + it will get the prop
     * er "slice" of the file
     *
     * @param position The position within the combined FileSlice buffers
     * @return The file slice located at the position specified.
     */
    private fun getBuffer(position: Long): ByteBuffer {

        var index = 0
        if (position > 0) {
            index = (position / bufferSliceSize).toInt()
        }

        return slices.getOrPut(index) {
            BufferPool.allocateAndLimit(bufferSliceSize)
        }
    }

    @Suppress("UseExpressionBody")
    override fun delete() {
    }

    /**
     * Close the data file
     *
     * @return Whether the in memory buffers were cleared
     */
    override fun close(): Boolean {
        slices.values.forEach { it.clear() }
        slices.clear()
        return true
    }

    override fun ensureOpen() = Unit
}
