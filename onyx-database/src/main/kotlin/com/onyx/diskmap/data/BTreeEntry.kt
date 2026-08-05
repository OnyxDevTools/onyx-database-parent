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
    var record: Long = 0L,
    val recordSize: Int = LEGACY_ENTRY_SIZE
) {

    private var recordValue: WeakReference<Any?>? = null

    fun setRecord(store: Store, record: Long) {
        if (this.record == record) return
        this.record = record
        recordValue = null
        val buffer = getBuffer(recordSize)
        buffer.putRecord(record, recordSize)
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
        val buffer = getBuffer(recordSize)
        buffer.putRecord(record, recordSize)
        buffer.flip()
        store.write(buffer, position)
    }

    fun read(store: Store): BTreeEntry {
        val buffer = getBuffer(recordSize)
        store.read(buffer, position)
        buffer.flip()
        record = buffer.readRecord(recordSize)
        recordValue = null
        return this
    }

    companion object {
        /** Source-compatible legacy entry width. New version-four trees use [PACKED_ENTRY_SIZE]. */
        const val ENTRY_SIZE = java.lang.Long.BYTES
        const val LEGACY_ENTRY_SIZE = ENTRY_SIZE
        const val PACKED_ENTRY_SIZE = 6
        const val NULL_RECORD = 0L

        fun create(store: Store, record: Long, recordSize: Int = LEGACY_ENTRY_SIZE): BTreeEntry =
            BTreeEntry(createPosition(store, record, recordSize), record, recordSize)

        fun createPosition(store: Store, record: Long, recordSize: Int = LEGACY_ENTRY_SIZE): Long =
            store.allocateSlot(recordSize).also { writeRecord(store, it, record, recordSize) }

        fun get(store: Store, position: Long, recordSize: Int = LEGACY_ENTRY_SIZE): BTreeEntry =
            BTreeEntry(position = position, recordSize = recordSize).read(store)

        fun readRecord(store: Store, position: Long, recordSize: Int = LEGACY_ENTRY_SIZE): Long {
            val buffer = getBuffer(recordSize)
            store.read(buffer, position)
            buffer.flip()
            return buffer.readRecord(recordSize)
        }

        fun writeRecord(
            store: Store,
            position: Long,
            record: Long,
            recordSize: Int = LEGACY_ENTRY_SIZE
        ) {
            val buffer = getBuffer(recordSize)
            buffer.putRecord(record, recordSize)
            buffer.flip()
            store.write(buffer, position)
        }

        private val buffer = ThreadLocal.withInitial { ByteBuffer.allocate(ENTRY_SIZE) }

        private fun getBuffer(recordSize: Int = LEGACY_ENTRY_SIZE): ByteBuffer = buffer.get().apply {
            require(recordSize == LEGACY_ENTRY_SIZE || recordSize == PACKED_ENTRY_SIZE) {
                "Unsupported B-tree entry record width $recordSize"
            }
            clear()
            limit(recordSize)
        }

        private fun ByteBuffer.putRecord(record: Long, recordSize: Int) = when (recordSize) {
            PACKED_ENTRY_SIZE -> putUnsignedLong48(record)
            LEGACY_ENTRY_SIZE -> putLong(record)
            else -> error("Unsupported B-tree entry record width $recordSize")
        }

        private fun ByteBuffer.readRecord(recordSize: Int): Long = when (recordSize) {
            PACKED_ENTRY_SIZE -> unsignedLong48
            LEGACY_ENTRY_SIZE -> long
            else -> error("Unsupported B-tree entry record width $recordSize")
        }
    }
}
