package com.onyx.diskmap.data

import com.onyx.diskmap.store.Store
import java.nio.ByteBuffer

/**
 * In-memory representation and serializer for a B+ tree page.
 *
 * A tree begins with a 96-byte compact leaf root and promotes it to ordinary 4 KiB pages when it
 * outgrows that layout. Every persisted slot contains a 64-bit key token and a 40-bit pointer. In
 * a leaf the pointer identifies a stable [BTreeEntry]; in an internal page it identifies the child
 * to the right of the separator, while the first child is stored in the header. Leaf headers also
 * form a doubly linked list for ordered traversal.
 *
 * The in-memory arrays include one overflow slot so insertions can be split after they occur.
 * Parent links are deliberately not persisted; mutations retain the descent path instead.
 *
 * @property position byte offset of the page in the tree store
 * @property leaf whether slots contain entry pointers (`true`) or child pointers (`false`)
 * @property compact whether this page uses the small-root layout rather than a page-aligned 4 KiB
 * layout
 * @param cacheDecodedKeys whether to retain decoded, externally stored keys alongside their tokens
 */
class BTreePage private constructor(
    var position: Long,
    var leaf: Boolean,
    val compact: Boolean,
    cacheDecodedKeys: Boolean
) {
    /** Maximum number of keys that can be persisted in this page's selected layout. */
    val capacity: Int = if (compact) COMPACT_MAX_KEYS else MAX_KEYS
    private val pageSize: Int = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE

    /**
     * Number of active keys. This may temporarily equal `capacity + 1` after insertion, but the
     * page must be split or promoted before [write] is called.
     */
    var keyCount: Int = 0

    /** Previous leaf's page position, or `0` at the beginning of the leaf chain. */
    var previousLeaf: Long = 0L

    /** Next leaf's page position, or `0` at the end of the leaf chain. */
    var nextLeaf: Long = 0L

    /**
     * Slot-aligned file pointers.
     *
     * Leaf pages use indexes `0 until keyCount` for stable [BTreeEntry] positions. Internal pages
     * use indexes `0..keyCount` for children; [keys] at index `i` is the lower bound of child
     * `i + 1`.
     */
    val pointers = LongArray(capacity + 2)

    /**
     * Ordered key tokens for active slots plus one temporary overflow slot.
     *
     * A token is either the key encoded directly as a `Long` or the position of a serialized key,
     * as determined by the owning tree.
     */
    val keys = LongArray(capacity + 1)

    /** Lazily decoded non-primitive keys, indexed identically to [keys]. */
    val decodedKeys = DecodedKeyCache(capacity + 1, cacheDecodedKeys)

    /**
     * Lazily read value-record pointers for leaf slots, initialized to [UNLOADED_RECORD]. Internal
     * pages expose an empty array because their pointers address child pages instead.
     */
    val recordPointers = if (leaf) LongArray(capacity + 1) { UNLOADED_RECORD } else EMPTY_RECORD_POINTERS

    /**
     * Inserts a leaf slot at [index], shifting subsequent slots and their caches to the right.
     *
     * One insertion beyond [capacity] is supported in memory so the owning tree can choose a split
     * point. The page cannot be persisted in that state.
     *
     * @param key encoded key token
     * @param entry stable [BTreeEntry] position
     * @param record current value-record position, or [BTreeEntry.NULL_RECORD]
     */
    fun insertLeaf(index: Int, key: Long, entry: Long, record: Long) {
        check(leaf)
        require(index in 0..keyCount)
        val moved = keyCount - index
        if (moved > 0) {
            System.arraycopy(keys, index, keys, index + 1, moved)
            System.arraycopy(pointers, index, pointers, index + 1, moved)
            decodedKeys.move(index, index + 1, moved)
            System.arraycopy(recordPointers, index, recordPointers, index + 1, moved)
        }
        keys[index] = key
        pointers[index] = entry
        decodedKeys[index] = null
        recordPointers[index] = record
        keyCount++
    }

    /** Removes the leaf slot at [index], compacts all slot-aligned caches, and clears the old tail. */
    fun removeLeaf(index: Int) {
        check(leaf)
        require(index in 0 until keyCount)
        val moved = keyCount - index - 1
        if (moved > 0) {
            System.arraycopy(keys, index + 1, keys, index, moved)
            System.arraycopy(pointers, index + 1, pointers, index, moved)
            decodedKeys.move(index + 1, index, moved)
            System.arraycopy(recordPointers, index + 1, recordPointers, index, moved)
        }
        keyCount--
        keys[keyCount] = 0L
        pointers[keyCount] = 0L
        decodedKeys[keyCount] = null
        recordPointers[keyCount] = UNLOADED_RECORD
    }

    /**
     * Inserts a separator and its right child at [index].
     *
     * The existing child at `pointers[index]` remains to the separator's left and [rightChild] is
     * inserted at `pointers[index + 1]`. As with [insertLeaf], one in-memory overflow separator is
     * allowed for splitting.
     */
    fun insertInternal(index: Int, key: Long, rightChild: Long) {
        check(!leaf)
        require(index in 0..keyCount)
        val keysMoved = keyCount - index
        if (keysMoved > 0) {
            System.arraycopy(keys, index, keys, index + 1, keysMoved)
            decodedKeys.move(index, index + 1, keysMoved)
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

    /**
     * Removes separator [keyIndex] and the child immediately to its right, then compacts the
     * remaining keys, children, and decoded-key cache.
     */
    fun removeInternal(keyIndex: Int) {
        check(!leaf)
        require(keyIndex in 0 until keyCount)
        val keysMoved = keyCount - keyIndex - 1
        if (keysMoved > 0) {
            System.arraycopy(keys, keyIndex + 1, keys, keyIndex, keysMoved)
            decodedKeys.move(keyIndex + 1, keyIndex, keysMoved)
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

    /**
     * Writes the complete fixed-size page, including its header, active slots, and zero-filled
     * unused space.
     *
     * Newly created pages are not durable until this method is called.
     *
     * @throws IllegalArgumentException if [keyCount] is outside the persistable range
     */
    fun write(store: Store) {
        require(keyCount in 0..capacity) { "B-tree page contains too many keys: $keyCount" }
        val buffer = getPageBuffer(pageSize)
        writeHeader(buffer)
        repeat(capacity) { index ->
            buffer.putLong(if (index < keyCount) keys[index] else 0L)
            buffer.putPointer(if (index < keyCount) leafPointer(index) else 0L)
        }
        while (buffer.position() < pageSize) buffer.put(0)
        buffer.flip()
        store.write(buffer, position)
    }

    /**
     * Rewrites active slots in `[fromIndex, toIndexExclusive)` without touching the header.
     *
     * Callers must persist [keyCount] separately with [writeCount]. Removed trailing slots are not
     * cleared on disk because the persisted count makes them unreachable.
     */
    fun writeSlots(store: Store, fromIndex: Int, toIndexExclusive: Int = keyCount) {
        require(fromIndex in 0..keyCount)
        require(toIndexExclusive in fromIndex..keyCount)
        if (fromIndex == toIndexExclusive) return
        val length = (toIndexExclusive - fromIndex) * SLOT_SIZE
        val buffer = getPageBuffer(length)
        for (index in fromIndex until toIndexExclusive) {
            buffer.putLong(keys[index])
            buffer.putPointer(leafPointer(index))
        }
        buffer.flip()
        store.write(buffer, position + HEADER_SIZE + fromIndex.toLong() * SLOT_SIZE)
    }

    /** Rewrites only the key-count field in the persisted header. */
    fun writeCount(store: Store) {
        val buffer = getSmallBuffer()
        buffer.putShort(keyCount.toShort())
        buffer.flip()
        store.write(buffer, position + KEY_COUNT_OFFSET)
    }

    /**
     * Rewrites only the encoded key at [index], leaving the slot's page or entry pointer unchanged.
     */
    fun writeKey(store: Store, index: Int) {
        val buffer = getSmallBuffer()
        buffer.putLong(keys[index])
        buffer.flip()
        store.write(buffer, position + HEADER_SIZE + index.toLong() * SLOT_SIZE)
    }

    /** Rewrites only [previousLeaf] in the persisted header. */
    fun writePreviousLeaf(store: Store) = writeHeaderLong(store, PREVIOUS_OFFSET, previousLeaf)

    private fun writeHeaderLong(store: Store, offset: Int, value: Long) {
        val buffer = getSmallBuffer()
        buffer.putPointer(value)
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
        buffer.putPointer(previousLeaf)
        buffer.putPointer(nextLeaf)
        buffer.putPointer(if (leaf) 0L else pointers[0])
        while (buffer.position() < HEADER_SIZE) buffer.put(0)
    }

    private fun ByteBuffer.putPointer(value: Long) = putBigInt(value)

    companion object {
        /** Persisted-key capacity of a standard [PAGE_SIZE] page. */
        const val MAX_KEYS = 312

        /** Persisted-key capacity of the initial compact root page. */
        const val COMPACT_MAX_KEYS = 4

        /** Size and alignment, in bytes, of a standard page. */
        const val PAGE_SIZE = 4096

        /** Size, in bytes, of a compact root page. */
        const val COMPACT_PAGE_SIZE = 96

        /** File signature stored at the beginning of every page (`OBTR`). */
        const val MAGIC = 0x4f425452 // "OBTR"

        /** In-memory sentinel indicating that a leaf's value-record pointer has not been read. */
        const val UNLOADED_RECORD = Long.MIN_VALUE

        private const val FORMAT_VERSION: Byte = 3
        private const val LEAF_FLAG = 1
        private const val COMPACT_FLAG = 2
        private const val HEADER_SIZE = 32
        private const val SLOT_SIZE = 13
        private const val KEY_COUNT_OFFSET = 6L
        private const val PREVIOUS_OFFSET = 8
        private val EMPTY_RECORD_POINTERS = LongArray(0)

        /**
         * Allocates a page and returns its empty in-memory representation.
         *
         * Standard pages are aligned to [PAGE_SIZE]. Compact pages use only
         * [COMPACT_PAGE_SIZE] bytes and are intended for the initial leaf root. Allocation does not
         * write a page header; call [write] after initializing the page.
         */
        fun create(
            store: Store,
            leaf: Boolean,
            compact: Boolean = false,
            cacheDecodedKeys: Boolean = true
        ): BTreePage {
            val size = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE
            val position = if (compact) store.allocate(size) else store.allocateAligned(size, PAGE_SIZE)
            return BTreePage(
                position,
                leaf,
                compact,
                cacheDecodedKeys
            )
        }

        /**
         * Loads and validates the page at [position].
         *
         * The persisted magic, format version, and key count are checked before slots are exposed.
         * Value-record pointers are intentionally left as [UNLOADED_RECORD] for lazy loading.
         *
         * @param cacheDecodedKeys whether externally stored keys decoded by the tree should be
         * retained on this page
         * @throws IllegalArgumentException if the page signature, format version, or key count is
         * invalid
         */
        fun get(store: Store, position: Long, cacheDecodedKeys: Boolean = true): BTreePage {
            val header = getPageBuffer(HEADER_SIZE)
            store.read(header, position)
            header.flip()
            require(header.int == MAGIC) { "Invalid B-tree page at position $position" }
            val formatVersion = header.get()
            require(formatVersion == FORMAT_VERSION) {
                "Unsupported B-tree page version $formatVersion at position $position"
            }
            val flags = header.get().toInt()
            val count = header.short.toInt() and 0xffff
            val previous = header.bigInt
            val next = header.bigInt
            val firstPointer = header.bigInt
            val page = BTreePage(
                position = position,
                leaf = flags and LEAF_FLAG != 0,
                compact = flags and COMPACT_FLAG != 0,
                cacheDecodedKeys = cacheDecodedKeys
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
                val pointer = slots.bigInt
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

/**
 * Slot-aligned cache of decoded non-primitive keys for a [BTreePage].
 *
 * Disabled caches allocate no backing array. They still accept `null` writes so page mutation
 * code can clear and move slots without branching on the key representation.
 */
class DecodedKeyCache internal constructor(size: Int, val enabled: Boolean) {
    private val values: Array<Any?>? = if (enabled) arrayOfNulls(size) else null
    val size: Int = size

    operator fun get(index: Int): Any? {
        require(index in 0 until size)
        return values?.get(index)
    }

    operator fun set(index: Int, value: Any?) {
        require(index in 0 until size)
        if (values == null) {
            check(value == null) { "Decoded keys are disabled for this B-tree page" }
        } else {
            values[index] = value
        }
    }

    fun move(sourceIndex: Int, destinationIndex: Int, length: Int) {
        require(sourceIndex >= 0 && destinationIndex >= 0 && length >= 0)
        require(sourceIndex + length <= size && destinationIndex + length <= size)
        values?.let { System.arraycopy(it, sourceIndex, it, destinationIndex, length) }
    }

    fun copyTo(destination: DecodedKeyCache, sourceIndex: Int, destinationIndex: Int, length: Int) {
        require(sourceIndex >= 0 && destinationIndex >= 0 && length >= 0)
        require(sourceIndex + length <= size && destinationIndex + length <= destination.size)
        val sourceValues = values
        if (sourceValues == null) {
            if (destination.values == null) return
            repeat(length) { destination[destinationIndex + it] = null }
            return
        }
        val destinationValues = destination.values
        check(destinationValues != null) { "Cannot copy decoded keys into a disabled cache" }
        System.arraycopy(sourceValues, sourceIndex, destinationValues, destinationIndex, length)
    }
}
