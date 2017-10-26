package com.onyx.diskmap.data

import com.onyx.buffer.BufferStream
import com.onyx.exception.BufferingException

/**
 * Created by Tim Osborn on 1/6/17.
 *
 * This is a record pointer for a skip list.
 */
@Suppress("UNCHECKED_CAST")
class SkipListNode<K> : SkipListHeadNode {
    var recordPosition: Long = 0
    var recordId: Long = 0
    var key: K = KEY as K

    constructor()

    constructor(key: K, position: Long, recordPosition: Long, level: Byte, next: Long, down: Long, recordId:Long) {
        this.position = position
        this.recordPosition = recordPosition
        this.level = level
        this.next = next
        this.down = down
        this.key = key
        this.recordId = recordId
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        super.read(buffer)
        recordPosition = buffer.long
        recordId = buffer.long
        key = buffer.value as K
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        super.write(buffer)
        buffer.putLong(recordPosition)
        buffer.putLong(recordId)
        buffer.putObject(key)
    }

    companion object {
        val BASE_SKIP_LIST_NODE_SIZE = java.lang.Long.BYTES * 2 + java.lang.Byte.BYTES
        val SKIP_LIST_NODE_SIZE = java.lang.Long.BYTES * 4 + java.lang.Byte.BYTES
        object KEY
    }

}
