package com.onyx.diskmap.impl.base.hashmap

import com.onyx.buffer.BufferPool
import com.onyx.diskmap.impl.DiskSkipListMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store
import com.onyx.extension.perform
import com.onyx.extension.withBuffer

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class is the base of a hash table.  It will allocate upon being instantiated if the header does not already exist
 * This allocates a fixed amount of space within the store and puts it aside to maintain a hash table.
 *
 * @since 1.2.0
 *
 * @param <K> Key
 * @param <V> Value
 */
abstract class AbstractHashMap<K, V>(fileStore: Store, header: Header, headless: Boolean, loadFactor: Int) : DiskSkipListMap<K, V>(fileStore, header, headless) {

    var mapCount = AtomicInteger(0) // Count of allocated hash table used
    private val referenceOffset: Int // Offset of the map references
    private val listReferenceOffset: Int // Offset of the iteration list reference

    init {
        this.loadFactor = loadFactor.toByte()
        var skipListReferenceAllocation = 1

        // Figure out how many bytes to allocate
        for (i in 0 until loadFactor)
            skipListReferenceAllocation *= 10

        // Find the offset
        val numberOfReferenceBytes = skipListReferenceAllocation * java.lang.Long.BYTES
        val numberOfListReferenceBytes = skipListReferenceAllocation * Integer.BYTES
        val countBytes = Integer.BYTES

        // Create the header if it does not exist.  Also allocate the hash table
        if (header.firstNode == 0L) {
            forceUpdateHeaderFirstNode(this.reference, fileStore.allocate(numberOfReferenceBytes + numberOfListReferenceBytes + countBytes))
            this.mapCount = AtomicInteger(0)
        } else {
            // It already exist.  Get the map count.  It is located within the first 4 bytes of the allocated hash table space.
            val position = header.firstNode
            val stream = fileStore.read(position, Integer.BYTES)
            stream.perform {
                mapCount = AtomicInteger(it!!.int)
            }
        }

        referenceOffset = countBytes
        listReferenceOffset = referenceOffset + numberOfReferenceBytes
    }

    /**
     * Insert nodeReference into the hash array.
     *
     * @param hash The maximum hash value can only contain as many digits as the size of the loadFactor
     * @param nodeReference Reference of the sub data structure to put it into.
     * @return The nodeReference that was inserted
     *
     * @since 1.2.0
     */
    protected open fun insertSkipListReference(hash: Int, nodeReference: Long): Long {
        val buffer = BufferPool.allocateAndLimit(java.lang.Long.BYTES)

        withBuffer(buffer) {
            // Update count of maps for iterating through each map
            val count = incrementMapCount()
            buffer.putInt(count)
            buffer.flip()
            fileStore.write(buffer, reference.firstNode)

            buffer.clear()
            buffer.putLong(nodeReference)
            buffer.flip()
            fileStore.write(buffer, reference.firstNode + referenceOffset.toLong() + (hash * java.lang.Long.BYTES).toLong())

            addSkipListIterationReference(buffer, hash, count - 1)
        }

        return nodeReference
    }

    /**
     * Add iteration list.  This method adds a reference so that the iterator knows what to iterate through without
     * guessing which element within the hash as a sub data structure reference.
     *
     * @param buffer Byte Buffer to add the hash id to.
     * @param hash Identifier of the sub data structure
     * @param count The current size of the hash table
     *
     * @since 1.2.0
     */
    open protected fun addSkipListIterationReference(buffer: ByteBuffer, hash: Int, count: Int) {
        // Add list reference for iterating
        buffer.clear()
        buffer.putInt(hash)
        buffer.flip()
        fileStore.write(buffer, reference.firstNode + listReferenceOffset.toLong() + (count * Integer.BYTES).toLong())
    }

    /**
     * Update the referenceNode of the hash.
     *
     * @param hash Identifier of the data structure
     * @param referenceNode Reference of the sub data structure to update to.
     * @since 1.2.0
     * @return The referenceNode that was sent in.
     */
    protected open fun updateSkipListReference(hash: Int, referenceNode: Long): Long {
        val position = reference.firstNode + referenceOffset.toLong() + (hash * java.lang.Long.BYTES).toLong()
        val buffer = BufferPool.allocateAndLimit(java.lang.Long.BYTES)
        withBuffer(buffer) {
            buffer.putLong(referenceNode)
            buffer.flip()
            fileStore.write(buffer, position)
        }
        return referenceNode
    }

    /**
     * Get Map ID within the iteration index
     * @param index Index within the list of maps
     * @return The hash identifier of the sub data structure
     * @since 1.2.0
     */
    protected open fun getSkipListIdentifier(index: Int): Int {
        val position = reference.firstNode + listReferenceOffset.toLong() + (index * Integer.BYTES).toLong()
        val stream = fileStore.read(position, Integer.BYTES)
        return stream.perform {
            return@perform it!!.int
        }
    }

    /**
     * Get the sub data structure reference for the hash id.
     * @param hash Identifier of the data structure
     * @return Location of the data structure within the volume/store
     *
     * @since 1.2.0
     */
    protected open fun getSkipListReference(hash: Int): Long {
        val position = (hash * java.lang.Long.BYTES).toLong() + referenceOffset.toLong() + reference.firstNode
        val stream = fileStore.read(position, java.lang.Long.BYTES)
        return stream.perform { return@perform it!!.long }
    }

    /**
     * Used to retrieve the amount of sub data structures
     *
     * @return atomic value of map count
     */
    protected fun getMapCount(): Int = mapCount.get()

    /**
     * Used to increment map count
     *
     * @return Map count value after incrementing
     */
    private fun incrementMapCount(): Int = mapCount.incrementAndGet()
}
