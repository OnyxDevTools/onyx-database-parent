package com.onyx.diskmap.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException

/**
 * Created by tosborn1 on 1/9/17.
 *
 * This is a head of a skip list level.
 */
open class SkipListHeadNode : BufferStreamable {

    var level: Byte = 0
    var next: Long = 0
    var down: Long = 0
    var position: Long = 0

    constructor()

    constructor(level: Byte, next: Long, down: Long) {
        this.level = level
        this.next = next
        this.down = down
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        next = buffer.long
        down = buffer.long
        level = buffer.byte
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putLong(next)
        buffer.putLong(down)
        buffer.putByte(level)
    }

    override fun equals(other: Any?): Boolean = other is SkipListHeadNode && other.position == position
    override fun hashCode(): Int  = position.hashCode()

    companion object {

        val HEAD_SKIP_LIST_NODE_SIZE = java.lang.Long.BYTES * 2 + java.lang.Byte.BYTES
    }
}
