package com.onyx.diskmap.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException

/**
 * Created by Tim Osborn on 2/16/17.
 *
 * This indicates a head of a data structure for a Hash table with a child index of a skip list.
 */
class CombinedIndexHashNode(var head: SkipNode, var mapId: Int) : BufferStreamable {

    override fun hashCode(): Int = mapId

    override fun equals(other: Any?): Boolean = other is CombinedIndexHashNode && other.mapId == mapId

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        head = buffer.value as SkipNode
        mapId = buffer.int
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putObject(head)
        buffer.putInt(mapId)
    }
}
