package com.onyx.diskmap.data

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferPool.withLongBuffer
import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.store.Store
import com.onyx.exception.UnknownDatabaseException

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
        it.putLong(record)
        it.rewind()
        store.write(it, position + (java.lang.Long.BYTES * 5))
    }

    fun <T> getKey(store: Store):T = store.getObject(key)

    fun write(store: Store) = BufferPool.allocateAndLimit(SKIP_NODE_SIZE) {
        it.putLong(position)
        it.putLong(up)
        it.putLong(left)
        it.putLong(right)
        it.putLong(down)
        it.putLong(record)
        it.putLong(key)
        it.putShort(level)
        it.rewind()
        store.write(it, position)
    }

    fun read(store: Store):SkipNode {
        val buffer = BufferPool.allocateAndLimit(SKIP_NODE_SIZE)
        store.read(buffer, position)
        buffer.rewind()
        val storePosition = buffer.long
        up = buffer.long
        left = buffer.long
        right = buffer.long
        down = buffer.long
        record = buffer.long
        key = buffer.long
        level = buffer.short
        if (storePosition != position) {
            println(this)
            println("Store position $storePosition")
            throw UnknownDatabaseException()
        }

        return this
    }

    val isRecord:Boolean
        get() = key > 0

    companion object {
        val SKIP_NODE_SIZE = (java.lang.Long.BYTES * 7) + java.lang.Short.BYTES

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

    }
}