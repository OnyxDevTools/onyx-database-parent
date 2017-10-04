package com.onyx.diskmap.data

import com.onyx.buffer.BufferStream
import com.onyx.exception.BufferingException

/**
 * Created by tosborn1 on 1/6/17.
 *
 * This is a record pointer for a skip list.
 */
@Suppress("UNCHECKED_CAST")
class SkipListNode<K> : SkipListHeadNode {
    var recordPosition: Long = 0
    var recordId: Long = 0
    var recordSize: Int = 0
    var key: K = KEY as K

    constructor()

    constructor(key: K, position: Long, recordPosition: Long, level: Byte, next: Long, down: Long, recordSize: Int, recordId: Long) {
        this.position = position
        this.recordPosition = recordPosition
        this.level = level
        this.next = next
        this.down = down
        this.key = key
        this.recordSize = recordSize
        this.recordId = recordId
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        recordPosition = buffer.long
        recordSize = buffer.int
        super.read(buffer)
        recordId = buffer.long
        key = buffer.value as K
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putLong(recordPosition)
        buffer.putInt(recordSize)
        super.write(buffer)
        buffer.putLong(recordId)
        buffer.putObject(key)
    }

    companion object {
        val BASE_SKIP_LIST_NODE_SIZE = java.lang.Long.BYTES * 2 + java.lang.Byte.BYTES
        val SKIP_LIST_NODE_SIZE = java.lang.Long.BYTES * 4 + java.lang.Byte.BYTES + java.lang.Integer.BYTES
        object KEY
    }

}
