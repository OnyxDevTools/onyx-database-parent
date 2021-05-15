package com.onyx.diskmap.data

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferPool.withIntBuffer
import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.OnyxThread
import com.onyx.extension.common.toType
import java.nio.ByteBuffer

data class SkipNode(
        var position:Int = 0,
        var up:Int = 0,
        var left:Int = 0,
        var right:Int = 0,
        var down:Int = 0,
        var record:Int = 0,
        var key:Long = 0L,
        var level:Short = 0
) : BufferStreamable {

    fun setTop(store:Store, top:Int) = withIntBuffer {
        this.up = top
        it.putInt(top)
        it.rewind()
        store.write(it, position)
    }

    fun setLeft(store:Store, left:Int) = withIntBuffer {
        this.left = left
        it.putInt(left)
        it.rewind()
        store.write(it, position + Integer.BYTES)
    }

    fun setRight(store:Store, right:Int) = withIntBuffer {
        this.right = right
        it.putInt(right)
        it.rewind()
        store.write(it, position + (Integer.BYTES * 2))
    }

    fun setBottom(store:Store, bottom:Int) = withIntBuffer {
        this.down = bottom
        it.putInt(bottom)
        it.rewind()
        store.write(it, position + (Integer.BYTES * 3))
    }

    fun setRecord(store:Store, record:Int) = withIntBuffer {
        this.record = record
        this.recordValue = null
        it.putInt(record)
        it.rewind()
        store.write(it, position + (Integer.BYTES * 4))
    }

    private var keyValue:Any? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getKey(store: Store, storedInNode:Boolean, type:Class<*>):T {
        if(keyValue != null) return keyValue as T

        if(storedInNode) {
            keyValue = key.toType(type)
            return keyValue as T
        }

        if(keyValue == null) {
            synchronized(this) {
                if(keyValue == null) {
                    keyValue = store.getObject(key.toInt())
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
        val buffer = getBuffer()
        try {
            buffer.putInt(up)
            buffer.putInt(left)
            buffer.putInt(right)
            buffer.putInt(down)
            buffer.putInt(record)
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
            up = buffer.int
            left = buffer.int
            right = buffer.int
            down = buffer.int
            record = buffer.int
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
        const val SKIP_NODE_SIZE = (Integer.BYTES * 5) + java.lang.Long.BYTES + java.lang.Short.BYTES

        fun create(store: Store, key:Long, value: Int, left:Int, right:Int, bottom: Int, level:Short):SkipNode {
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

        fun get(store: Store, position: Int) = SkipNode(position).read(store)

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