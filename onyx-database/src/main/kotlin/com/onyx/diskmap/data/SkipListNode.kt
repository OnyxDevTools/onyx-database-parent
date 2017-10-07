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
    var key: K = KEY as K

    constructor()

    constructor(key: K, position: Long, recordPosition: Long, level: Byte, next: Long, down: Long) {
        this.position = position
        this.recordPosition = recordPosition
        this.level = level
        this.next = next
        this.down = down
        this.key = key
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        super.read(buffer)
        recordPosition = buffer.long
        key = buffer.value as K
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        super.write(buffer)
        buffer.putLong(recordPosition)
        buffer.putObject(key)
    }

    companion object {
        val BASE_SKIP_LIST_NODE_SIZE = java.lang.Long.BYTES * 2 + java.lang.Byte.BYTES
        val SKIP_LIST_NODE_SIZE = java.lang.Long.BYTES * 3 + java.lang.Byte.BYTES
        object KEY
    }

}
