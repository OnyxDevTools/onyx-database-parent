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
    val valueKind: ValueKind,
    /** Width used by value tokens in this page's persisted format. */
    val valueTokenWidth: Int,
    private val signedValueToken: Boolean
) {
    private val pageSize: Int = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE
    private val slotSize: Int = valueTokenWidth + BIG_INT_SIZE + if (leaf) 0 else BIG_INT_SIZE

    /** Capacity follows the persisted slot width rather than a single tree-wide constant. */
    val capacity: Int = (pageSize - HEADER_SIZE) / slotSize

    var keyCount: Int = 0
    var previousLeaf: Long = 0L
    var nextLeaf: Long = 0L

    /** Encoded primitive value, date epoch, or object location. */
    val valueTokens = LongArray(capacity + 1)

    /** Entity record reference that forms the second component of every key. */
    val recordIds = LongArray(capacity + 1)

    /** Internal child 0 followed by one right child per separator. */
    val children: LongArray = if (leaf) EMPTY_LONGS else LongArray(capacity + 2)

    /** Lazily decoded object values, retained only for object-valued pages. */
    val decodedValues: Array<Any?> =
        if (valueKind == ValueKind.OBJECT) arrayOfNulls(capacity + 1) else EMPTY_DECODED_VALUES

    fun decodedValue(index: Int): Any? =
        if (decodedValues.isEmpty()) null else decodedValues[index]

    fun setDecodedValue(index: Int, value: Any?) {
        if (decodedValues.isNotEmpty()) decodedValues[index] = value
    }

    fun copyDecodedValues(
        sourceIndex: Int,
        destination: IndexPostingPage,
        destinationIndex: Int,
        count: Int
    ) {
        if (count == 0 || decodedValues.isEmpty()) return
        check(destination.decodedValues.isNotEmpty())
        System.arraycopy(decodedValues, sourceIndex, destination.decodedValues, destinationIndex, count)
    }

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
            if (decodedValues.isNotEmpty()) {
                System.arraycopy(decodedValues, index, decodedValues, index + 1, moved)
            }
        }
        valueTokens[index] = valueToken
        recordIds[index] = recordId
        setDecodedValue(index, decodedValue)
        keyCount++
    }

    fun removeLeaf(index: Int) {
        check(leaf)
        require(index in 0 until keyCount)
        val moved = keyCount - index - 1
        if (moved > 0) {
            System.arraycopy(valueTokens, index + 1, valueTokens, index, moved)
            System.arraycopy(recordIds, index + 1, recordIds, index, moved)
            if (decodedValues.isNotEmpty()) {
                System.arraycopy(decodedValues, index + 1, decodedValues, index, moved)
            }
        }
        keyCount--
        clearKey(keyCount)
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
            if (decodedValues.isNotEmpty()) {
                System.arraycopy(decodedValues, index, decodedValues, index + 1, keysMoved)
            }
        }
        val childrenMoved = keyCount - index
        if (childrenMoved > 0) {
            System.arraycopy(children, index + 1, children, index + 2, childrenMoved)
        }
        valueTokens[index] = valueToken
        recordIds[index] = recordId
        setDecodedValue(index, decodedValue)
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
            if (decodedValues.isNotEmpty()) {
                System.arraycopy(decodedValues, keyIndex + 1, decodedValues, keyIndex, keysMoved)
            }
        }
        val childrenMoved = keyCount - keyIndex - 1
        if (childrenMoved > 0) {
            System.arraycopy(children, keyIndex + 2, children, keyIndex + 1, childrenMoved)
        }
        keyCount--
        clearKey(keyCount)
        children[keyCount + 1] = 0L
    }

    fun write(store: Store) {
        require(keyCount in 0..capacity) { "Index posting page contains too many keys: $keyCount" }
        val buffer = getPageBuffer(pageSize)
        writeHeader(buffer)
        repeat(capacity) { index ->
            writeValueToken(buffer, if (index < keyCount) valueTokens[index] else 0L)
            writeRecordId(buffer, if (index < keyCount) recordIds[index] else 0L)
            if (!leaf) writeChild(buffer, if (index < keyCount) children[index + 1] else 0L)
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
            writeValueToken(buffer, valueTokens[index])
            writeRecordId(buffer, recordIds[index])
            if (!leaf) writeChild(buffer, children[index + 1])
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
        writeValueToken(buffer, valueTokens[index])
        writeRecordId(buffer, recordIds[index])
        buffer.flip()
        store.write(buffer, position + HEADER_SIZE + index.toLong() * slotSize)
    }

    fun writePreviousLeaf(store: Store) = writePosition(store, PREVIOUS_OFFSET, previousLeaf)

    fun writeFirstChild(store: Store) {
        check(!leaf)
        writePosition(store, FIRST_CHILD_OFFSET, children[0])
    }

    private fun writePosition(store: Store, offset: Int, value: Long) {
        val buffer = getSmallBuffer()
        buffer.putBigInt(value)
        buffer.flip()
        store.write(buffer, position + offset)
    }

    private fun writeHeader(buffer: ByteBuffer) {
        buffer.putInt(MAGIC)
        buffer.put(FORMAT_VERSION)
        var flags = valueKind.id shl VALUE_KIND_SHIFT
        if (leaf) flags = flags or LEAF_FLAG
        if (compact) flags = flags or COMPACT_FLAG
        flags = flags or (widthCode(valueTokenWidth) shl VALUE_WIDTH_SHIFT)
        if (signedValueToken) flags = flags or SIGNED_VALUE_FLAG
        buffer.put(flags.toByte())
        buffer.putShort(keyCount.toShort())
        buffer.putBigInt(previousLeaf)
        buffer.putBigInt(nextLeaf)
        buffer.putBigInt(if (leaf) 0L else children[0])
        while (buffer.position() < HEADER_SIZE) buffer.put(0)
    }

    private fun writeValueToken(buffer: ByteBuffer, value: Long) {
        when (valueTokenWidth) {
            Byte.SIZE_BYTES -> buffer.put(value.toByte())
            Short.SIZE_BYTES -> buffer.putShort(value.toShort())
            Int.SIZE_BYTES -> buffer.putInt(value.toInt())
            Long.SIZE_BYTES -> buffer.putLong(value)
            else -> error("Unsupported index posting value-token width $valueTokenWidth")
        }
    }

    private fun writeRecordId(buffer: ByteBuffer, value: Long) {
        buffer.putBigInt(value)
    }

    private fun writeChild(buffer: ByteBuffer, value: Long) {
        buffer.putBigInt(value)
    }

    private fun clearKey(index: Int) {
        valueTokens[index] = 0L
        recordIds[index] = 0L
        setDecodedValue(index, null)
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
        const val PAGE_SIZE = 4096
        const val COMPACT_PAGE_SIZE = 96
        const val MAGIC = 0x4f495054 // "OIPT"

        private const val FORMAT_VERSION: Byte = 1
        private const val LEAF_FLAG = 1
        private const val COMPACT_FLAG = 2
        private const val VALUE_KIND_SHIFT = 2
        private const val VALUE_KIND_MASK = 0x1c
        private const val VALUE_WIDTH_SHIFT = 5
        private const val VALUE_WIDTH_MASK = 0x60
        private const val SIGNED_VALUE_FLAG = 0x80
        private const val ALLOWED_FLAGS =
            LEAF_FLAG or COMPACT_FLAG or VALUE_KIND_MASK or VALUE_WIDTH_MASK or SIGNED_VALUE_FLAG
        private const val HEADER_SIZE = 32
        private const val BIG_INT_SIZE = 5
        private const val KEY_COUNT_OFFSET = 6L
        private const val PREVIOUS_OFFSET = 8
        private const val FIRST_CHILD_OFFSET = 18

        private val EMPTY_LONGS = LongArray(0)
        private val EMPTY_DECODED_VALUES = emptyArray<Any?>()

        fun create(
            store: Store,
            leaf: Boolean,
            compact: Boolean = false,
            valueKind: ValueKind,
            valueTokenWidth: Int,
            signedValueToken: Boolean
        ): IndexPostingPage {
            require(!compact || leaf) { "Only an index posting leaf can be compact" }
            require(valueTokenWidth in SUPPORTED_VALUE_WIDTHS) {
                "Unsupported index posting value-token width $valueTokenWidth"
            }
            val size = if (compact) COMPACT_PAGE_SIZE else PAGE_SIZE
            val position = if (compact) store.allocate(size) else store.allocateAligned(size, PAGE_SIZE)
            return IndexPostingPage(
                position,
                leaf,
                compact,
                valueKind,
                valueTokenWidth,
                signedValueToken
            )
        }

        fun createLike(
            store: Store,
            source: IndexPostingPage,
            leaf: Boolean,
            compact: Boolean = false
        ): IndexPostingPage = create(
            store,
            leaf,
            compact,
            source.valueKind,
            source.valueTokenWidth,
            source.signedValueToken
        )

        /** Reads the value width and signedness persisted in the page header. */
        fun get(store: Store, position: Long, valueKind: ValueKind): IndexPostingPage =
            read(store, position, valueKind, null, null)

        fun get(
            store: Store,
            position: Long,
            valueKind: ValueKind,
            expectedValueTokenWidth: Int,
            signedValueToken: Boolean
        ): IndexPostingPage = read(
            store,
            position,
            valueKind,
            expectedValueTokenWidth,
            signedValueToken
        )

        private fun read(
            store: Store,
            position: Long,
            valueKind: ValueKind,
            expectedValueTokenWidth: Int?,
            expectedSignedValueToken: Boolean?
        ): IndexPostingPage {
            val header = getPageBuffer(HEADER_SIZE)
            store.read(header, position)
            header.flip()
            require(header.int == MAGIC) { "Invalid index posting page at position $position" }
            val formatVersion = header.get()
            require(formatVersion == FORMAT_VERSION) {
                "Unsupported index posting page version $formatVersion at position $position"
            }
            val flags = header.get().toInt() and 0xff
            require(flags and ALLOWED_FLAGS.inv() == 0) {
                "Invalid index posting page flags 0x${flags.toString(16)} at position $position"
            }
            val storedValueKind = ValueKind.fromId((flags and VALUE_KIND_MASK) ushr VALUE_KIND_SHIFT)
            require(storedValueKind == valueKind) {
                "Index posting page at position $position stores $storedValueKind values, not $valueKind"
            }
            val valueTokenWidth = widthFromCode((flags and VALUE_WIDTH_MASK) ushr VALUE_WIDTH_SHIFT)
            if (expectedValueTokenWidth != null) {
                require(valueTokenWidth == expectedValueTokenWidth) {
                    "Index posting page at position $position uses $valueTokenWidth-byte values, " +
                        "not $expectedValueTokenWidth-byte values"
                }
            }
            val storedSignedValueToken = flags and SIGNED_VALUE_FLAG != 0
            if (expectedSignedValueToken != null) {
                require(storedSignedValueToken == expectedSignedValueToken) {
                    "Index posting page at position $position uses a " +
                        (if (storedSignedValueToken) "signed" else "unsigned") +
                        " value token, not a " +
                        (if (expectedSignedValueToken) "signed" else "unsigned") + " value token"
                }
            }
            val leaf = flags and LEAF_FLAG != 0
            val compact = flags and COMPACT_FLAG != 0
            require(!compact || leaf) { "Compact index posting page at position $position is not a leaf" }
            val count = header.short.toInt() and 0xffff
            val previous = header.bigInt
            val next = header.bigInt
            val firstChild = header.bigInt
            val page = IndexPostingPage(
                position,
                leaf,
                compact,
                storedValueKind,
                valueTokenWidth,
                storedSignedValueToken
            )
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
                val valueToken = page.readValueToken(slots)
                val recordId = page.readRecordId(slots)
                val rightChild = if (leaf) 0L else page.readChild(slots)
                if (index < count) {
                    page.valueTokens[index] = valueToken
                    page.recordIds[index] = recordId
                    if (!leaf) page.children[index + 1] = rightChild
                }
            }
            return page
        }

        private fun widthCode(width: Int): Int = when (width) {
            Byte.SIZE_BYTES -> 0
            Short.SIZE_BYTES -> 1
            Int.SIZE_BYTES -> 2
            Long.SIZE_BYTES -> 3
            else -> error("Unsupported index posting value-token width $width")
        }

        private fun widthFromCode(code: Int): Int = 1 shl code

        private val SUPPORTED_VALUE_WIDTHS = setOf(
            Byte.SIZE_BYTES,
            Short.SIZE_BYTES,
            Int.SIZE_BYTES,
            Long.SIZE_BYTES
        )

        private val pageBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(PAGE_SIZE) }
        private val smallBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(24) }

        private fun getPageBuffer(size: Int): ByteBuffer = pageBuffer.get().apply {
            clear()
            limit(size)
        }

        private fun getSmallBuffer(): ByteBuffer = smallBuffer.get().apply {
            clear()
            limit(capacity())
        }
    }

    private fun readValueToken(buffer: ByteBuffer): Long {
        return when (valueTokenWidth) {
            Byte.SIZE_BYTES -> if (signedValueToken) buffer.get().toLong() else buffer.get().toLong() and 0xffL
            Short.SIZE_BYTES -> if (signedValueToken) buffer.short.toLong() else buffer.short.toLong() and 0xffffL
            Int.SIZE_BYTES -> if (signedValueToken) buffer.int.toLong() else buffer.int.toLong() and 0xffff_ffffL
            Long.SIZE_BYTES -> buffer.long
            else -> error("Unsupported index posting value-token width $valueTokenWidth")
        }
    }

    private fun readRecordId(buffer: ByteBuffer): Long = buffer.bigInt

    private fun readChild(buffer: ByteBuffer): Long = buffer.bigInt
}
