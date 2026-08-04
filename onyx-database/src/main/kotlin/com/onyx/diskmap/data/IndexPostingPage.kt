package com.onyx.diskmap.data

import com.onyx.diskmap.store.Store
import java.nio.ByteBuffer

/**
 * Persistent page for a secondary-index posting B+ tree.
 *
 * Leaf slots store the complete posting key as `[valueToken, recordId]`.
 * Internal slots add the right-child pointer and therefore store
 * `[valueToken, recordId, rightChild]`. The first child of an internal page
 * remains in the page header.
 */
class IndexPostingPage private constructor(
    var position: Long,
    var leaf: Boolean,
    val compact: Boolean,
    val valueKind: ValueKind
) {
    val capacity: Int = when {
        compact -> COMPACT_MAX_KEYS
        leaf -> LEAF_MAX_KEYS
        else -> INTERNAL_MAX_KEYS
    }

    private val pageSize: Int = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE
    private val slotSize: Int = if (leaf) LEAF_SLOT_SIZE else INTERNAL_SLOT_SIZE

    var keyCount: Int = 0
    var previousLeaf: Long = 0L
    var nextLeaf: Long = 0L

    /** Encoded primitive value, date epoch, or object location. */
    val valueTokens = LongArray(capacity + 1)

    /** Entity record reference that forms the second component of every key. */
    val recordIds = LongArray(capacity + 1)

    /** Internal child 0 followed by one right child per separator. */
    val children = LongArray(capacity + 2)

    /** Lazily decoded object values, retained with the cached page. */
    val decodedValues: Array<Any?> = arrayOfNulls(capacity + 1)

    fun insertLeaf(
        index: Int,
        valueToken: Long,
        recordId: Long,
        decodedValue: Any? = null
    ) {
        check(leaf)
        require(index in 0..keyCount)
        val moved = keyCount - index
        if (moved > 0) {
            System.arraycopy(valueTokens, index, valueTokens, index + 1, moved)
            System.arraycopy(recordIds, index, recordIds, index + 1, moved)
            System.arraycopy(decodedValues, index, decodedValues, index + 1, moved)
        }
        valueTokens[index] = valueToken
        recordIds[index] = recordId
        decodedValues[index] = decodedValue
        keyCount++
    }

    fun removeLeaf(index: Int) {
        check(leaf)
        require(index in 0 until keyCount)
        val moved = keyCount - index - 1
        if (moved > 0) {
            System.arraycopy(valueTokens, index + 1, valueTokens, index, moved)
            System.arraycopy(recordIds, index + 1, recordIds, index, moved)
            System.arraycopy(decodedValues, index + 1, decodedValues, index, moved)
        }
        keyCount--
        valueTokens[keyCount] = 0L
        recordIds[keyCount] = 0L
        decodedValues[keyCount] = null
    }

    /** Inserts a separator and its right child at [index]. */
    fun insertInternal(
        index: Int,
        valueToken: Long,
        recordId: Long,
        rightChild: Long,
        decodedValue: Any? = null
    ) {
        check(!leaf)
        require(index in 0..keyCount)
        val keysMoved = keyCount - index
        if (keysMoved > 0) {
            System.arraycopy(valueTokens, index, valueTokens, index + 1, keysMoved)
            System.arraycopy(recordIds, index, recordIds, index + 1, keysMoved)
            System.arraycopy(decodedValues, index, decodedValues, index + 1, keysMoved)
        }
        val childrenMoved = keyCount - index
        if (childrenMoved > 0) {
            System.arraycopy(children, index + 1, children, index + 2, childrenMoved)
        }
        valueTokens[index] = valueToken
        recordIds[index] = recordId
        decodedValues[index] = decodedValue
        children[index + 1] = rightChild
        keyCount++
    }

    /** Removes separator [keyIndex] and the child immediately to its right. */
    fun removeInternal(keyIndex: Int) {
        check(!leaf)
        require(keyIndex in 0 until keyCount)
        val keysMoved = keyCount - keyIndex - 1
        if (keysMoved > 0) {
            System.arraycopy(valueTokens, keyIndex + 1, valueTokens, keyIndex, keysMoved)
            System.arraycopy(recordIds, keyIndex + 1, recordIds, keyIndex, keysMoved)
            System.arraycopy(decodedValues, keyIndex + 1, decodedValues, keyIndex, keysMoved)
        }
        val childrenMoved = keyCount - keyIndex - 1
        if (childrenMoved > 0) {
            System.arraycopy(children, keyIndex + 2, children, keyIndex + 1, childrenMoved)
        }
        keyCount--
        valueTokens[keyCount] = 0L
        recordIds[keyCount] = 0L
        decodedValues[keyCount] = null
        children[keyCount + 1] = 0L
    }

    fun write(store: Store) {
        require(keyCount in 0..capacity) { "Index posting page contains too many keys: $keyCount" }
        val buffer = getPageBuffer(pageSize)
        writeHeader(buffer)
        repeat(capacity) { index ->
            buffer.putLong(if (index < keyCount) valueTokens[index] else 0L)
            buffer.putLong(if (index < keyCount) recordIds[index] else 0L)
            if (!leaf) buffer.putLong(if (index < keyCount) children[index + 1] else 0L)
        }
        while (buffer.position() < pageSize) buffer.put(0)
        buffer.flip()
        store.write(buffer, position)
    }

    /** Writes the changed slot tail without rewriting the whole page. */
    fun writeSlots(store: Store, fromIndex: Int) {
        if (fromIndex >= keyCount) return
        val length = (keyCount - fromIndex) * slotSize
        val buffer = getPageBuffer(length)
        for (index in fromIndex until keyCount) {
            buffer.putLong(valueTokens[index])
            buffer.putLong(recordIds[index])
            if (!leaf) buffer.putLong(children[index + 1])
        }
        buffer.flip()
        store.write(buffer, position + HEADER_SIZE + fromIndex.toLong() * slotSize)
    }

    fun writeCount(store: Store) {
        val buffer = getSmallBuffer()
        buffer.putShort(keyCount.toShort())
        buffer.flip()
        store.write(buffer, position + KEY_COUNT_OFFSET)
    }

    /** Writes only the two-component key, leaving the internal child unchanged. */
    fun writeKey(store: Store, index: Int) {
        require(index in 0 until keyCount)
        val buffer = getSmallBuffer()
        buffer.putLong(valueTokens[index])
        buffer.putLong(recordIds[index])
        buffer.flip()
        store.write(buffer, position + HEADER_SIZE + index.toLong() * slotSize)
    }

    fun writePreviousLeaf(store: Store) = writeHeaderLong(store, PREVIOUS_OFFSET, previousLeaf)

    private fun writeHeaderLong(store: Store, offset: Int, value: Long) {
        val buffer = getSmallBuffer()
        buffer.putLong(value)
        buffer.flip()
        store.write(buffer, position + offset)
    }

    private fun writeHeader(buffer: ByteBuffer) {
        buffer.putInt(MAGIC)
        buffer.put(FORMAT_VERSION)
        var flags = valueKind.id shl VALUE_KIND_SHIFT
        if (leaf) flags = flags or LEAF_FLAG
        if (compact) flags = flags or COMPACT_FLAG
        buffer.put(flags.toByte())
        buffer.putShort(keyCount.toShort())
        buffer.putLong(previousLeaf)
        buffer.putLong(nextLeaf)
        buffer.putLong(if (leaf) 0L else children[0])
    }

    enum class ValueKind(val id: Int) {
        INTEGRAL(0),
        FLOAT(1),
        DOUBLE(2),
        DATE(3),
        OBJECT(4);

        companion object {
            fun fromId(id: Int): ValueKind = entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown index posting value kind $id")
        }
    }

    companion object {
        const val LEAF_MAX_KEYS = 254
        const val INTERNAL_MAX_KEYS = 169
        const val COMPACT_MAX_KEYS = 4
        const val PAGE_SIZE = 4096
        const val COMPACT_PAGE_SIZE = 96
        const val MAGIC = 0x4f495054 // "OIPT"

        private const val FORMAT_VERSION: Byte = 1
        private const val LEAF_FLAG = 1
        private const val COMPACT_FLAG = 2
        private const val VALUE_KIND_SHIFT = 2
        private const val VALUE_KIND_MASK = 0x1c
        private const val ALLOWED_FLAGS = LEAF_FLAG or COMPACT_FLAG or VALUE_KIND_MASK
        private const val HEADER_SIZE = 32
        private const val LEAF_SLOT_SIZE = 16
        private const val INTERNAL_SLOT_SIZE = 24
        private const val KEY_COUNT_OFFSET = 6L
        private const val PREVIOUS_OFFSET = 8

        fun create(
            store: Store,
            leaf: Boolean,
            compact: Boolean = false,
            valueKind: ValueKind
        ): IndexPostingPage {
            require(!compact || leaf) { "Only an index posting leaf can be compact" }
            val size = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE
            val position = if (compact) store.allocate(size) else store.allocateAligned(size, PAGE_SIZE)
            return IndexPostingPage(position, leaf, compact, valueKind)
        }

        fun get(store: Store, position: Long, valueKind: ValueKind): IndexPostingPage {
            val header = getPageBuffer(HEADER_SIZE)
            store.read(header, position)
            header.flip()
            require(header.int == MAGIC) { "Invalid index posting page at position $position" }
            require(header.get() == FORMAT_VERSION) {
                "Unsupported index posting page version at position $position"
            }
            val flags = header.get().toInt() and 0xff
            require(flags and ALLOWED_FLAGS.inv() == 0) {
                "Invalid index posting page flags 0x${flags.toString(16)} at position $position"
            }
            val storedValueKind = ValueKind.fromId((flags and VALUE_KIND_MASK) ushr VALUE_KIND_SHIFT)
            require(storedValueKind == valueKind) {
                "Index posting page at position $position stores $storedValueKind values, not $valueKind"
            }
            val leaf = flags and LEAF_FLAG != 0
            val compact = flags and COMPACT_FLAG != 0
            require(!compact || leaf) { "Compact index posting page at position $position is not a leaf" }
            val count = header.short.toInt() and 0xffff
            val previous = header.long
            val next = header.long
            val firstChild = header.long
            val page = IndexPostingPage(position, leaf, compact, storedValueKind)
            require(count <= page.capacity) {
                "Invalid index posting key count $count at position $position"
            }
            page.keyCount = count
            page.previousLeaf = previous
            page.nextLeaf = next
            if (!leaf) page.children[0] = firstChild

            val slotsLength = page.capacity * page.slotSize
            val slots = getPageBuffer(slotsLength)
            store.read(slots, position + HEADER_SIZE)
            slots.flip()
            repeat(page.capacity) { index ->
                val valueToken = slots.long
                val recordId = slots.long
                val rightChild = if (leaf) 0L else slots.long
                if (index < count) {
                    page.valueTokens[index] = valueToken
                    page.recordIds[index] = recordId
                    if (!leaf) page.children[index + 1] = rightChild
                }
            }
            return page
        }

        private val pageBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(PAGE_SIZE) }
        private val smallBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(INTERNAL_SLOT_SIZE) }

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
