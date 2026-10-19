package com.onyx.diskmap.data

import com.onyx.diskmap.store.Store
import java.nio.ByteBuffer

/**
 * Persistent indirection between a B+ tree leaf slot and its value record.
 *
 * A leaf stores this entry's [position] as the record ID exposed to callers. The six-byte entry
 * at that position contains [record], the current value location in the record store. Updating a
 * value rewrites that pointer in place, so moving its key during a page split or merge does not
 * invalidate existing record IDs.
 *
 * @property position byte offset of this entry in the tree's page store; this is the stable ID
 * exposed by the owning map
 * @property record byte offset of the serialized value in the record store, or [NULL_RECORD] for
 * a null value
 */
data class BTreeEntry(
    var position: Long = 0L,
    var record: Long = 0L
) {
    /**
     * Redirects this stable entry to [record] and persists the new 48-bit pointer in place.
     *
     * Assigning the current pointer is a no-op and performs no store write.
     *
     * @param store page store containing this entry at [position]
     * @throws IllegalArgumentException if [record] cannot be represented as an unsigned 48-bit
     * file position
     */
    fun setRecord(store: Store, record: Long) {
        if (this.record == record) return
        this.record = record
        val buffer = getBuffer()
        buffer.putUnsignedLong48(record)
        buffer.flip()
        store.write(buffer, position)
    }

    /**
     * Returns the value referenced by [record], decoding it from [store].
     *
     * [NULL_RECORD] is returned as `null`. The caller must request the type used to serialize the
     * value; this entry does not retain runtime type information.
     *
     * @param store record store containing the serialized value, not the page store containing
     * this entry
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getRecord(store: Store): T {
        if (record == NULL_RECORD) return null as T
        return store.getObject(record)
    }

    /**
     * Persists [record] at this entry's already allocated [position].
     *
     * This writes only the six-byte indirection record; it does not serialize the referenced value.
     */
    fun write(store: Store) {
        val buffer = getBuffer()
        buffer.putUnsignedLong48(record)
        buffer.flip()
        store.write(buffer, position)
    }

    /**
     * Reloads the 48-bit record pointer at [position] into this instance.
     *
     * @return this instance
     */
    fun read(store: Store): BTreeEntry {
        val buffer = getBuffer()
        store.read(buffer, position)
        buffer.flip()
        record = buffer.unsignedLong48
        return this
    }

    companion object {
        /** Number of bytes occupied by a persisted entry. */
        const val ENTRY_SIZE = 6

        /** Pointer value used to represent a null map value. */
        const val NULL_RECORD = 0L

        /** Allocates, writes, and returns a stable entry that points to [record]. */
        fun create(store: Store, record: Long): BTreeEntry =
            BTreeEntry(createPosition(store, record), record)

        /**
         * Allocates and writes an entry that points to [record], returning its stable file position.
         */
        fun createPosition(store: Store, record: Long): Long =
            store.allocateSlot(ENTRY_SIZE).also { writeRecord(store, it, record) }

        /** Loads the entry stored at [position]. */
        fun get(store: Store, position: Long): BTreeEntry =
            BTreeEntry(position = position).read(store)

        /** Reads only the 48-bit record pointer stored at [position]. */
        fun readRecord(store: Store, position: Long): Long {
            val buffer = getBuffer()
            store.read(buffer, position)
            buffer.flip()
            return buffer.unsignedLong48
        }

        /**
         * Rewrites only the 48-bit record pointer at [position].
         *
         * This allocation-free form is used on hot update paths where a [BTreeEntry] object is not
         * otherwise needed.
         */
        fun writeRecord(store: Store, position: Long, record: Long) {
            val buffer = getBuffer()
            buffer.putUnsignedLong48(record)
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
