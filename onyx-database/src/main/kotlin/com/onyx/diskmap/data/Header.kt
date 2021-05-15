package com.onyx.diskmap.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by timothy.osborn on 3/21/15.
 *
 * This class is to represent the starting place for a structure implementation.  This is serialized first
 *
 */
class Header : BufferStreamable {

    var firstNode: Int = 0
    var position: Int = 0
    var recordCount: AtomicInteger = AtomicInteger(0)

    /**
     * Override equals key to compare all values
     *
     * @param other Object to compare against
     * @return Whether the header = the parameter value
     */
    override fun equals(other: Any?): Boolean = other is Header && position == other.position

    /**
     * Add hash code for use within maps to help identify
     *
     * @return hash code of the header position
     */
    override fun hashCode(): Int = position.hashCode()

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        firstNode = buffer.int
        recordCount = AtomicInteger(buffer.int)
        position = buffer.int
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putInt(firstNode)
        buffer.putInt(recordCount.get())
        buffer.putInt(position)
    }

    companion object {
        const val HEADER_SIZE = Integer.BYTES * 3
    }
}
