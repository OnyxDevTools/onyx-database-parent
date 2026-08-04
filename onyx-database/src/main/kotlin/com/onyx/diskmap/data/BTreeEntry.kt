package com.onyx.diskmap.data

import com.onyx.diskmap.store.Store
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

/**
 * Stable value handle exposed as a disk-map record ID.
 *
 * Keys live in B-tree pages. Moving a key during a split therefore leaves this
 * handle, and every reference to it, unchanged.
 */
data class BTreeEntry(
    var position: Long = 0L,
    var record: Long = 0L
) {

    private var recordValue: WeakReference<Any?>? = null

    fun setRecord(store: Store, record: Long) {
        if (this.record == record) return
        this.record = record
        recordValue = null
        val buffer = getBuffer()
        buffer.putLong(record)
        buffer.flip()
        store.write(buffer, position)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getRecord(store: Store): T {
        if (record == NULL_RECORD) return null as T
        recordValue?.get()?.let { return it as T }
        synchronized(this) {
            recordValue?.get()?.let { return it as T }
            val value = store.getObject<T>(record)
            recordValue = WeakReference(value)
            return value
        }
    }

    fun write(store: Store) {
        val buffer = getBuffer()
        buffer.putLong(record)
        buffer.flip()
        store.write(buffer, position)
    }

    fun read(store: Store): BTreeEntry {
        val buffer = getBuffer()
        store.read(buffer, position)
        buffer.flip()
        record = buffer.long
        recordValue = null
        return this
    }

    companion object {
        const val ENTRY_SIZE = java.lang.Long.BYTES
        const val NULL_RECORD = 0L

        fun create(store: Store, record: Long): BTreeEntry =
            BTreeEntry(createPosition(store, record), record)

        fun createPosition(store: Store, record: Long): Long =
            store.allocateSlot(ENTRY_SIZE).also { writeRecord(store, it, record) }

        fun get(store: Store, position: Long): BTreeEntry =
            BTreeEntry(position = position).read(store)

        fun readRecord(store: Store, position: Long): Long {
            val buffer = getBuffer()
            store.read(buffer, position)
            buffer.flip()
            return buffer.long
        }

        fun writeRecord(store: Store, position: Long, record: Long) {
            val buffer = getBuffer()
            buffer.putLong(record)
            buffer.flip()
            store.write(buffer, position)
        }

        private val buffer = ThreadLocal.withInitial { ByteBuffer.allocate(ENTRY_SIZE) }

        private fun getBuffer(): ByteBuffer = buffer.get().apply {
            clear()
            limit(ENTRY_SIZE)
        }
    }
}
