package com.onyx.diskmap.data

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferPool.withLongBuffer
import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.store.Store
import java.nio.ByteBuffer
import kotlin.concurrent.getOrSet

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

    fun setTop(store:Store, top:Long) = withLongBuffer {
        this.up = top
        it.putLong(top)
        it.rewind()
        store.write(it, position + java.lang.Long.BYTES)
    }

    fun setLeft(store:Store, left:Long) = withLongBuffer {
        this.left = left
        it.putLong(left)
        it.rewind()
        store.write(it, position + (java.lang.Long.BYTES * 2))
    }

    fun setRight(store:Store, right:Long) = withLongBuffer {
        this.right = right
        it.putLong(right)
        it.rewind()
        store.write(it, position + (java.lang.Long.BYTES * 3))
    }

    fun setBottom(store:Store, bottom:Long) = withLongBuffer {
        this.down = bottom
        it.putLong(bottom)
        it.rewind()
        store.write(it, position + (java.lang.Long.BYTES * 4))
    }

    fun setRecord(store:Store, record:Long) = withLongBuffer {
        this.record = record
        this.recordValue = null
        it.putLong(record)
        it.rewind()
        store.write(it, position + (java.lang.Long.BYTES * 5))
    }

    @Volatile
    private var keyValue:Any? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getKey(store: Store):T {
        if(keyValue == null) {
            synchronized(this) {
                if(keyValue == null) {
                    keyValue = store.getObject(key)
                }
            }
        }
        return keyValue as T
    }

    private var recordValue:Any? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getRecord(store: Store):T {
        if(recordValue == null) {
            synchronized(this) {
                if(recordValue == null) {
                    recordValue = store.getObject(record)
                }
            }
        }
        return recordValue as T
    }

    fun write(store: Store) {
        val buffer = skipNodeBuffer.getOrSet { BufferPool.allocateExact(SKIP_NODE_SIZE) }
        buffer.clear()
        buffer.putLong(position)
        buffer.putLong(up)
        buffer.putLong(left)
        buffer.putLong(right)
        buffer.putLong(down)
        buffer.putLong(record)
        buffer.putLong(key)
        buffer.putShort(level)
        buffer.flip()
        store.write(buffer, position)
    }

    fun read(store: Store):SkipNode {
        val buffer = skipNodeBuffer.getOrSet { BufferPool.allocateExact(SKIP_NODE_SIZE) }
        buffer.clear()
        store.read(buffer, position)
        buffer.rewind()
        buffer.long
        up = buffer.long
        left = buffer.long
        right = buffer.long
        down = buffer.long
        record = buffer.long
        key = buffer.long
        level = buffer.short
        recordValue = null
        return this
    }

    val isRecord:Boolean
        get() = record > 0

    companion object {
        val SKIP_NODE_SIZE = (java.lang.Long.BYTES * 7) + java.lang.Short.BYTES

        fun create(store: Store, key:Long, value: Long, left:Long, right:Long, bottom: Long, level:Short, keyValue:Any? = null):SkipNode {
            val node = SkipNode()
            node.key = key
            node.record = value
            node.left = left
            node.right = right
            node.down = bottom
            node.position = store.allocate(SKIP_NODE_SIZE)
            node.level = level
            node.keyValue = keyValue
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

        val skipNodeBuffer = ThreadLocal<ByteBuffer>()
    }
}