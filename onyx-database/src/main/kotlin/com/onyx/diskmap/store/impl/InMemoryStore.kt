package com.onyx.diskmap.store.impl

import com.onyx.buffer.BufferPool
import com.onyx.diskmap.store.Store
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.context.SchemaContext

/**
 * Created by Tim Osborn on 3/27/15.
 *
 * Rather than writing to a file, this writes to memory.
 */
class InMemoryStore (context: SchemaContext?, storeId: String) : MemoryMappedStore(), Store {

    init {
        this.contextId = context?.contextId
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
        val buffer = BufferPool.allocateAndLimit(bufferSliceSize)
        slices[0] = FileSlice(buffer)
        return true
    }

    /**
     * Get the associated buffer to the position of the file.  So if the position is 2G + it will get the prop
     * er "slice" of the file
     *
     * @param position The position within the combined FileSlice buffers
     * @return The file slice located at the position specified.
     */
    override fun getBuffer(position: Int): FileSlice {

        var index = 0
        if (position > 0) {
            index = (position / bufferSliceSize)
        }

        return slices.getOrPut(index) {
            FileSlice(BufferPool.allocateAndLimit(bufferSliceSize))
        }
    }

    @Suppress("UseExpressionBody")
    override fun delete() {  }

    /**
     * Close the data file
     *
     * @return Whether the in memory buffers were cleared
     */
    override fun close(): Boolean {
        slices.values.forEach { it.buffer.clear() }
        slices.clear()
        return true
    }
}

