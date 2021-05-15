package com.onyx.interactors.record.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable

/**
 * Created by timothy.osborn on 3/5/15.
 *
 * This is a combined reference of the partition the entity is in and its location
 */
data class Reference @JvmOverloads constructor(var partition: Int = 0, var reference: Int = 0) : Comparable<Reference>, BufferStreamable {

    override fun compareTo(other: Reference): Int = when {
        this.partition < other.partition -> -1
        this.partition > other.partition -> 1
        this.reference < other.reference -> -1
        this.reference > other.reference -> 1
        else -> 0
    }

    override fun read(buffer: BufferStream) {
        partition = buffer.int
        reference = buffer.int
    }

    override fun write(buffer: BufferStream) {
        buffer.putInt(partition)
        buffer.putInt(reference)
    }

}
