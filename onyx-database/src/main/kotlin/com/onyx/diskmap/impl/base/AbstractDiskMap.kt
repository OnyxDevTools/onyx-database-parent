package com.onyx.diskmap.impl.base

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferPool.withIntBuffer
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.canBeCastToPrimitive
import com.onyx.extension.perform
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Tim Osborn on 1/11/17.
 *
 * This class was intended to abstract some of the common actions and values that all disk data structures use.
 *
 * I kept running into various different areas of the application I would have to update if I had to modify say a
 * header class.  I would then have to make sure I modified several different implementations.  No longer!!!
 *
 * @since 1.2.0
 */
abstract class AbstractDiskMap<K, V> constructor(override val fileStore: Store, header: Header, val keyType:Class<*>, canStoreKeyWithinNode:Boolean) : DiskMap<K, V> {

    final override val reference: Header = Header()

    val storeKeyWithinNode:Boolean = canStoreKeyWithinNode && keyType.canBeCastToPrimitive()

    init {
        // Clone the header so that we do not have a cross reference
        // This was preventing WeakHashMaps from ejecting the entire map value
        this.reference.firstNode = header.firstNode
        this.reference.position = header.position
        this.reference.recordCount = AtomicInteger(header.recordCount.get())
    }

    /**
     * This method is intended to get a record key as a dictionary.  Note: This is only intended for ManagedEntities
     *
     * @param recordId Record reference to pull
     * @return Map of key key pairs
     *
     * @since 1.2.0
     */
    protected fun getRecordValueAsDictionary(recordId: Int): Map<String, Any?> {
        var size = 0
        BufferPool.withIntBuffer {
            fileStore.read(it, recordId)
            it.rewind()
            size = it.int
        }
        return fileStore.readObject(recordId + Integer.BYTES, size).perform { it!!.toMap(fileStore.context!!) }
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    protected fun updateHeaderRecordCount(count:Int) {
        withIntBuffer {
            it.putInt(count)
            it.rewind()
            fileStore.write(it, reference.position + java.lang.Integer.BYTES)
        }
    }

    /**
     * Only update the first position for a header
     *
     * @param header    Data structure header
     * @param firstNode First Node location
     */
    protected fun updateHeaderFirstNode(header: Header, firstNode: Int) {
        this.reference.firstNode = firstNode
        withIntBuffer {
            it.putInt(firstNode)
            it.rewind()
            fileStore.write(it, header.position)
        }
    }

    /**
     * Default hashing algorithm.
     *
     * @param key Key to get the hash of
     * @return The hash value of that key.
     */
    protected fun hash(key: Any?): Int = key?.hashCode() ?: 0

    /**
     * Whether or not the map is empty.
     *
     * @return True if the size is 0
     * @since 1.2.0
     */
    override fun isEmpty(): Boolean = this.size == 0

    /**
     * Increment the size of the map
     *
     * @since 1.2.0
     */
    protected fun incrementSize() {
        updateHeaderRecordCount(reference.recordCount.incrementAndGet())
    }

    /**
     * Decrement the size of the map
     *
     * @since 1.2.0
     */
    protected fun decrementSize() {
        updateHeaderRecordCount(reference.recordCount.decrementAndGet())
    }
}
