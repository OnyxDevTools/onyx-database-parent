package com.onyx.diskmap.data

import com.onyx.buffer.BufferObjectType
import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * This is a data for sifting through a hash matrix to find the end of the chain that points to a skip list data
 */
class HashMatrixNode : BufferStreamable {

    var next: LongArray
    var position: Long = 0

    init {
        next = LongArray(DEFAULT_BITMAP_ITERATIONS)
    }

    override fun hashCode(): Int = position.hashCode()

    override fun equals(other: Any?): Boolean = other is HashMatrixNode && other.position == position

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        this.position = buffer.long
        this.next = buffer.getArray(BufferObjectType.LONG_ARRAY) as LongArray
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putLong(position)
        buffer.putArray(next)
    }

    companion object {
        val DEFAULT_BITMAP_ITERATIONS = 10
    }
}
