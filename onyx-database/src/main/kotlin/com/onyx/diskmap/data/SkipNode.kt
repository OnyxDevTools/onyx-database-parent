package com.onyx.diskmap.data

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferPool.withBigIntBuffer
import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.store.Store
import com.onyx.exception.BufferingException
import com.onyx.extension.common.OnyxThread
import com.onyx.extension.common.toType
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

data class SkipNode(
        var position:Long = 0L,
        var up:Long = 0L,
        var left:Long = 0L,
        var right:Long = 0L,
        var down:Long = 0L,
        var record:Long = 0L,
        var key:Long = 0L,
        var level:Short = 0
) : BufferStreamable {

    fun setTop(store:Store, top:Long) = withBigIntBuffer {
        this.up = top
        it.putBigInt(top)
        it.rewind()
        store.write(it, position)
    }

    fun setLeft(store:Store, left:Long) = withBigIntBuffer {
        this.left = left
        it.putBigInt(left)
        it.rewind()
        store.write(it, position + 5)
    }

    fun setRight(store:Store, right:Long) = withBigIntBuffer {
        this.right = right
        it.putBigInt(right)
        it.rewind()
        store.write(it, position + (5 * 2))
    }

    fun setBottom(store:Store, bottom:Long) = withBigIntBuffer {
        this.down = bottom
        it.putBigInt(bottom)
        it.rewind()
        store.write(it, position + (5 * 3))
    }

    fun setRecord(store:Store, record:Long) = withBigIntBuffer {
        this.record = record
        this.recordValue = null
        it.putBigInt(record)
        it.rewind()
        store.write(it, position + (5 * 4))
    }

    private var keyValue:Any? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getKey(store: Store, storedInNode:Boolean, type:Class<*>):T {
        if(keyValue != null) return keyValue as T

        if(storedInNode) {
            keyValue = key.toType(type)
            return keyValue as T
        }

        @Suppress("KotlinConstantConditions") // Key value is mutable and getKey can have multiple access
        if(keyValue == null) {
            synchronized(this) {
                if(keyValue == null) {
                    keyValue = store.getObject(key)
                }
            }
        }
        return keyValue as T
    }

    private var recordValue:WeakReference<Any?>? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getRecord(store: Store):T {
        if(recordValue?.get() == null) {
            synchronized(this) {
                if(recordValue?.get() == null) {
                    recordValue = WeakReference(store.getObject(record))
                }
            }
        }
        return recordValue!!.get() as T
    }

    fun write(store: Store) {
        val buffer = getBuffer()
        try {
            buffer.putBigInt(up)
            buffer.putBigInt(left)
            buffer.putBigInt(right)
            buffer.putBigInt(down)
            buffer.putBigInt(record)
            buffer.putLong(key)
            buffer.putShort(level)
            buffer.flip()
            store.write(buffer, position)
        } finally {
            recycleBuffer(buffer)
        }
    }

    fun read(store: Store):SkipNode {
        val buffer = getBuffer()
        try {
            store.read(buffer, position)
            buffer.rewind()
            up = buffer.bigInt
            left = buffer.bigInt
            right = buffer.bigInt
            down = buffer.bigInt
            record = buffer.bigInt
            key = buffer.long
            level = buffer.short
            recordValue = null
        } finally {
            recycleBuffer(buffer)
        }
        return this
    }

    val isRecord:Boolean
        get() = record > 0

    companion object {
        const val SKIP_NODE_SIZE = (java.lang.Long.BYTES * 6) + java.lang.Short.BYTES

        fun create(store: Store, key:Long, value: Long, left:Long, right:Long, bottom: Long, level:Short):SkipNode {
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

        fun create(store: Store):SkipNode {
            val node = SkipNode()
            node.position = store.allocate(SKIP_NODE_SIZE)
            node.write(store)
            return node
        }

        fun get(store: Store, position: Long) = SkipNode(position).read(store)

        fun getBuffer(): ByteBuffer {
            val thread = Thread.currentThread()
            return if(thread is OnyxThread) {
                thread.nodeBuffer.clear()
                thread.nodeBuffer
            } else {
                BufferPool.allocateAndLimit(SKIP_NODE_SIZE)
            }
        }

        fun recycleBuffer(buffer: ByteBuffer) {
            val thread = Thread.currentThread()
            if (thread !is OnyxThread) {
                BufferPool.recycle(buffer)
            }
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
    this.put(byteArrayOf(
        value.toByte(),
        (value shr 8).toByte(),
        (value shr 16).toByte(),
        (value shr 24).toByte(),
        (value shr 32).toByte()
    ))
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
