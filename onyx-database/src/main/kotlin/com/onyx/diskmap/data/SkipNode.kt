package com.onyx.diskmap.data

import com.onyx.buffer.BufferPool.withBigIntBuffer
import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.store.Store
import com.onyx.exception.BufferingException
import com.onyx.extension.common.toType
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

data class SkipNode(
    var position: Long = 0L,
    var left: Long = 0L,
    var right: Long = 0L,
    var down: Long = 0L,
    var record: Long = 0L,
    var key: Long = 0L,
    var level: UByte = 0.toUByte()
) : BufferStreamable {

    fun setLeft(store: Store, left: Long) = withBigIntBuffer {
        if (this.left == left) return@withBigIntBuffer 0
        this.left = left
        it.putBigInt(left)
        it.rewind()
        store.write(it, position)
    }

    fun setRight(store: Store, right: Long) = withBigIntBuffer {
        if (this.right == right) return@withBigIntBuffer 0
        this.right = right
        it.putBigInt(right)
        it.rewind()
        store.write(it, position + 5)
    }

    fun setBottom(store: Store, bottom: Long) = withBigIntBuffer {
        if (this.down == bottom) return@withBigIntBuffer 0
        this.down = bottom
        it.putBigInt(bottom)
        it.rewind()
        store.write(it, position + (5 * 2))
    }

    fun setRecord(store: Store, record: Long) = withBigIntBuffer {
        this.recordValue = null
        if (this.record == record) return@withBigIntBuffer 0
        if (this.level.toInt() != 0) return@withBigIntBuffer 0
        this.record = record
        it.putBigInt(record)
        it.rewind()
        store.write(it, position + (5 * 3))
    }

    private var keyValue: Any? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getKey(records: Store, storedInNode: Boolean, type: Class<*>): T {
        if (key == 0L && record == 0L && left == 0L) return null as T
        if (keyValue != null) return keyValue as T

        if (storedInNode) {
            keyValue = key.toType(type)
            return keyValue as T
        }

        @Suppress("KotlinConstantConditions") // Key value is mutable and getKey can have multiple access
        if (keyValue == null) {
            synchronized(this) {
                if (keyValue == null) {
                    keyValue = records.getObject(key)
                }
            }
        }
        return keyValue as T
    }

    private var recordValue: WeakReference<Any?>? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getRecord(store: Store): T {
        if (recordValue?.get() == null) {
            synchronized(this) {
                if (recordValue?.get() == null) {
                    recordValue = if (record == -1L) {
                        WeakReference(null)
                    } else {
                        WeakReference(store.getObject(record))
                    }
                }
            }
        }
        return recordValue!!.get() as T
    }

    fun write(store: Store) {
        val buffer = getBuffer()
        buffer.putBigInt(left)
        buffer.putBigInt(right)
        buffer.putBigInt(down)
        buffer.putBigInt(record)
        buffer.putLong(key)
        buffer.put(level.toByte())
        buffer.rewind()
        store.write(buffer, position)
    }

    fun read(store: Store): SkipNode {
        val buffer = getBuffer()
        store.read(buffer, position)
        buffer.rewind()
        left = buffer.bigInt
        right = buffer.bigInt
        down = buffer.bigInt
        record = buffer.bigInt
        key = buffer.long
        level = buffer.get().toUByte()
        recordValue = null
        return this
    }

    val isRecord: Boolean
        get() = record > 0

    companion object {

        const val SKIP_NODE_SIZE = (5 * 4) + java.lang.Long.BYTES + java.lang.Byte.BYTES

        fun create(
            store: Store,
            key: Long,
            value: Long,
            left: Long,
            right: Long,
            bottom: Long,
            level: UByte
        ): SkipNode {
            val node = SkipNode()
            node.key = key
            node.record = value
            node.left = left
            node.right = right
            node.down = bottom
            node.position = store.allocate(SKIP_NODE_SIZE)
            node.level = level
            node.write(store)
            return node
        }

        fun create(store: Store): SkipNode {
            val node = SkipNode()
            node.position = store.allocate(SKIP_NODE_SIZE)
            return node
        }

        fun get(store: Store, position: Long) = SkipNode(position).read(store)

        private val buffer: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
            ByteBuffer.allocate(SKIP_NODE_SIZE)
        }

        fun getBuffer(): ByteBuffer = buffer.get().let {
            it.rewind()
            it
        }
    }
}

/**
 * Sometimes you need an int but a long is too big.  Say for instance tracking a file position whereas
 * files on most operating systems can only grow to 2TB.  A long is a waste of space if a 5 byte int can
 * be used.
 *
 * @since 2.2.0
 * @param value long to write.
 */
fun ByteBuffer.putBigInt(value: Long) {
    this.put(
        byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte(),
            (value shr 32).toByte()
        )
    )
}


/**
 * Get 5 byte integer from the buffer
 *
 * @since  2.2.0
 * @return big int read from the buffer
 * @throws BufferingException Generic Buffer Exception
 */
val ByteBuffer.bigInt: Long
    @Throws(BufferingException::class)
    get() {
        val array = ByteArray(5)
        this.get(array)
        return ((array[4].toLong() and 0xff shl 32)
                or (array[3].toLong() and 0xff shl 24)
                or (array[2].toLong() and 0xff shl 16)
                or (array[1].toLong() and 0xff shl 8)
                or (array[0].toLong() and 0xff))
    }
