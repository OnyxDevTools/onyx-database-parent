package com.onyx.diskmap.impl.base

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferPool.withBigIntBuffer
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.putBigInt
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.canBeCastToPrimitive
import com.onyx.extension.perform
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

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
abstract class AbstractDiskMap<K, V> constructor(protected val store: WeakReference<Store>, protected val recordStore: WeakReference<Store>, header: Header, val keyType:Class<*>) : DiskMap<K, V> {

    final override val reference: Header = Header()

    override val fileStore: Store
        get() = store.get()!!

    override val records: Store
        get() = recordStore.get()!!

    val storeKeyWithinNode:Boolean = keyType.canBeCastToPrimitive()

    init {
        // Clone the header so that we do not have a cross reference
        // This was preventing WeakHashMaps from ejecting the entire map value
        this.reference.firstNode = header.firstNode
        this.reference.position = header.position
        this.reference.recordCount = AtomicLong(header.recordCount.get())
    }

    /**
     * This method is intended to get a record key as a dictionary.  Note: This is only intended for ManagedEntities
     *
     * @param recordId Record reference to pull
     * @return Map of key key pairs
     *
     * @since 1.2.0
     */
    open protected fun getRecordValueAsDictionary(recordId: Long): Map<String, Any?> {
        var size = 0
        BufferPool.withIntBuffer {
            records.read(it, recordId)
            it.rewind()
            size = it.int
        }
        return records.readObject(recordId + Integer.BYTES, size).perform { it!!.toMap(records.context!!) }
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    open protected fun updateHeaderRecordCount(count:Long) {
        withBigIntBuffer {
            it.putBigInt(count)
            it.rewind()
            fileStore.write(it, reference.position + 5)
        }
    }

    /**
     * Only update the first position for a header
     *
     * @param header    Data structure header
     * @param firstNode First Node location
     */
    open protected fun updateHeaderFirstNode(header: Header, firstNode: Long) {
        if (this.reference.firstNode == firstNode) return
        this.reference.firstNode = firstNode
        withBigIntBuffer {
            it.putBigInt(firstNode)
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
    open protected fun hash(key: Any?): Int = key?.hashCode() ?: 0

    /**
     * Whether or not the map is empty.
     *
     * @return True if the size is 0
     * @since 1.2.0
     */
    override fun isEmpty(): Boolean = longSize() == 0L

    /**
     * The size in a long value
     *
     * @return The size of the map as a long
     * @since 1.2.0
     */
    override fun longSize(): Long = reference.recordCount.get()

    /**
     * Increment the size of the map
     *
     * @since 1.2.0
     */
    open protected fun incrementSize() {
        updateHeaderRecordCount(reference.recordCount.incrementAndGet())
    }

    /**
     * Decrement the size of the map
     *
     * @since 1.2.0
     */
    open protected fun decrementSize() {
        updateHeaderRecordCount(reference.recordCount.decrementAndGet())
    }
}
