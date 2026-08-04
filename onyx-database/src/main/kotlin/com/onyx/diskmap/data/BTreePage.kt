package com.onyx.diskmap.data

import com.onyx.diskmap.store.Store
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * Cache- and disk-friendly B+ tree page.
 *
 * A compact root avoids wasting 4 KiB for the many tiny maps used by indexes.
 * Once promoted, pages use aligned 64-bit key/pointer slots and 4 KiB blocks.
 * Parents are deliberately not persisted; mutations retain their descent path.
 */
class BTreePage private constructor(
    var position: Long,
    var leaf: Boolean,
    val compact: Boolean
) {
    val capacity: Int = if (compact) COMPACT_MAX_KEYS else MAX_KEYS
    private val pageSize: Int = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE
    var keyCount: Int = 0
    var previousLeaf: Long = 0L
    var nextLeaf: Long = 0L

    /** Leaf: stable entry IDs. Internal: child 0 followed by right children. */
    val pointers = LongArray(capacity + 2)
    val keys = LongArray(capacity + 1)

    /** Lazily decoded non-primitive keys, kept with the cached page. */
    val decodedKeys: Array<Any?> = arrayOfNulls(capacity + 1)

    /** Lazily read value pointers for leaf slots. */
    val recordPointers = LongArray(capacity + 1) { UNLOADED_RECORD }

    fun insertLeaf(index: Int, key: Long, entry: Long, record: Long) {
        check(leaf)
        require(index in 0..keyCount)
        val moved = keyCount - index
        if (moved > 0) {
            System.arraycopy(keys, index, keys, index + 1, moved)
            System.arraycopy(pointers, index, pointers, index + 1, moved)
            System.arraycopy(decodedKeys, index, decodedKeys, index + 1, moved)
            System.arraycopy(recordPointers, index, recordPointers, index + 1, moved)
        }
        keys[index] = key
        pointers[index] = entry
        decodedKeys[index] = null
        recordPointers[index] = record
        keyCount++
    }

    fun removeLeaf(index: Int) {
        check(leaf)
        require(index in 0 until keyCount)
        val moved = keyCount - index - 1
        if (moved > 0) {
            System.arraycopy(keys, index + 1, keys, index, moved)
            System.arraycopy(pointers, index + 1, pointers, index, moved)
            System.arraycopy(decodedKeys, index + 1, decodedKeys, index, moved)
            System.arraycopy(recordPointers, index + 1, recordPointers, index, moved)
        }
        keyCount--
        keys[keyCount] = 0L
        pointers[keyCount] = 0L
        decodedKeys[keyCount] = null
        recordPointers[keyCount] = UNLOADED_RECORD
    }

    /** Inserts a separator and its right child at [index]. */
    fun insertInternal(index: Int, key: Long, rightChild: Long) {
        check(!leaf)
        require(index in 0..keyCount)
        val keysMoved = keyCount - index
        if (keysMoved > 0) {
            System.arraycopy(keys, index, keys, index + 1, keysMoved)
            System.arraycopy(decodedKeys, index, decodedKeys, index + 1, keysMoved)
        }
        val pointersMoved = keyCount - index
        if (pointersMoved > 0) {
            System.arraycopy(pointers, index + 1, pointers, index + 2, pointersMoved)
        }
        keys[index] = key
        decodedKeys[index] = null
        pointers[index + 1] = rightChild
        keyCount++
    }

    /** Removes separator [keyIndex] and the child immediately to its right. */
    fun removeInternal(keyIndex: Int) {
        check(!leaf)
        require(keyIndex in 0 until keyCount)
        val keysMoved = keyCount - keyIndex - 1
        if (keysMoved > 0) {
            System.arraycopy(keys, keyIndex + 1, keys, keyIndex, keysMoved)
            System.arraycopy(decodedKeys, keyIndex + 1, decodedKeys, keyIndex, keysMoved)
        }
        val pointersMoved = keyCount - keyIndex - 1
        if (pointersMoved > 0) {
            System.arraycopy(pointers, keyIndex + 2, pointers, keyIndex + 1, pointersMoved)
        }
        keyCount--
        keys[keyCount] = 0L
        decodedKeys[keyCount] = null
        pointers[keyCount + 1] = 0L
    }

    fun write(store: Store) {
        require(keyCount in 0..capacity) { "B-tree page contains too many keys: $keyCount" }
        val buffer = getPageBuffer(pageSize)
        writeHeader(buffer)
        repeat(capacity) { index ->
            buffer.putLong(if (index < keyCount) keys[index] else 0L)
            buffer.putLong(if (index < keyCount) leafPointer(index) else 0L)
        }
        buffer.flip()
        store.write(buffer, position)
    }

    /** Writes the changed slot tail without rewriting the whole page. */
    fun writeSlots(store: Store, fromIndex: Int) {
        if (fromIndex >= keyCount) return
        val length = (keyCount - fromIndex) * SLOT_SIZE
        val buffer = getPageBuffer(length)
        for (index in fromIndex until keyCount) {
            buffer.putLong(keys[index])
            buffer.putLong(leafPointer(index))
        }
        buffer.flip()
        store.write(buffer, position + HEADER_SIZE + fromIndex.toLong() * SLOT_SIZE)
    }

    fun writeCount(store: Store) {
        val buffer = getSmallBuffer()
        buffer.putShort(keyCount.toShort())
        buffer.flip()
        store.write(buffer, position + KEY_COUNT_OFFSET)
    }

    fun writeKey(store: Store, index: Int) {
        val buffer = getSmallBuffer()
        buffer.putLong(keys[index])
        buffer.flip()
        store.write(buffer, position + HEADER_SIZE + index.toLong() * SLOT_SIZE)
    }

    fun writePreviousLeaf(store: Store) = writeHeaderLong(store, PREVIOUS_OFFSET, previousLeaf)

    private fun writeHeaderLong(store: Store, offset: Int, value: Long) {
        val buffer = getSmallBuffer()
        buffer.putLong(value)
        buffer.flip()
        store.write(buffer, position + offset)
    }

    private fun leafPointer(index: Int): Long = if (leaf) pointers[index] else pointers[index + 1]

    private fun writeHeader(buffer: ByteBuffer) {
        buffer.putInt(MAGIC)
        buffer.put(FORMAT_VERSION)
        var flags = if (leaf) LEAF_FLAG else 0
        if (compact) flags = flags or COMPACT_FLAG
        buffer.put(flags.toByte())
        buffer.putShort(keyCount.toShort())
        buffer.putLong(previousLeaf)
        buffer.putLong(nextLeaf)
        buffer.putLong(if (leaf) 0L else pointers[0])
    }

    companion object {
        const val MAX_KEYS = 254
        const val COMPACT_MAX_KEYS = 4
        const val PAGE_SIZE = 4096
        const val COMPACT_PAGE_SIZE = 96
        const val MAGIC = 0x4f425452 // "OBTR"
        const val UNLOADED_RECORD = Long.MIN_VALUE

        private const val FORMAT_VERSION: Byte = 2
        private const val LEAF_FLAG = 1
        private const val COMPACT_FLAG = 2
        private const val HEADER_SIZE = 32
        private const val SLOT_SIZE = 16
        private const val KEY_COUNT_OFFSET = 6L
        private const val PREVIOUS_OFFSET = 8

        fun create(store: Store, leaf: Boolean, compact: Boolean = false): BTreePage {
            val size = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE
            val position = if (compact) store.allocate(size) else store.allocateAligned(size, PAGE_SIZE)
            return BTreePage(position, leaf, compact)
        }

        fun get(store: Store, position: Long): BTreePage {
            val header = getPageBuffer(HEADER_SIZE)
            store.read(header, position)
            header.flip()
            require(header.int == MAGIC) { "Invalid B-tree page at position $position" }
            require(header.get() == FORMAT_VERSION) { "Unsupported B-tree page version at position $position" }
            val flags = header.get().toInt()
            val count = header.short.toInt() and 0xffff
            val previous = header.long
            val next = header.long
            val firstPointer = header.long
            val page = BTreePage(
                position = position,
                leaf = flags and LEAF_FLAG != 0,
                compact = flags and COMPACT_FLAG != 0
            )
            require(count <= page.capacity) { "Invalid B-tree key count $count at position $position" }
            page.keyCount = count
            page.previousLeaf = previous
            page.nextLeaf = next
            if (!page.leaf) page.pointers[0] = firstPointer

            val slotsLength = page.capacity * SLOT_SIZE
            val slots = getPageBuffer(slotsLength)
            store.read(slots, position + HEADER_SIZE)
            slots.flip()
            repeat(page.capacity) { index ->
                val key = slots.long
                val pointer = slots.long
                if (index < count) {
                    page.keys[index] = key
                    page.pointers[if (page.leaf) index else index + 1] = pointer
                }
            }
            return page
        }

        private val pageBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(PAGE_SIZE) }
        private val smallBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(SLOT_SIZE) }

        private fun getPageBuffer(size: Int): ByteBuffer = pageBuffer.get().apply {
            clear()
            limit(size)
        }

        private fun getSmallBuffer(): ByteBuffer = smallBuffer.get().apply {
            clear()
            limit(capacity())
        }
    }
}
