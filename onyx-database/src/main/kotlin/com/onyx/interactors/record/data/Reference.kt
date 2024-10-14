package com.onyx.interactors.record.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException
import com.onyx.persistence.context.SchemaContext

/**
 * Created by timothy.osborn on 3/5/15.
 *
 * This is a combined reference of the partition the entity is in and its location
 */
data class Reference @JvmOverloads constructor(var partition: Long = 0L, var reference: Long = 0L) : Comparable<Reference>, BufferStreamable {

    override fun compareTo(other: Reference): Int = when {
        this.partition < other.partition -> -1
        this.partition > other.partition -> 1
        this.reference < other.reference -> -1
        this.reference > other.reference -> 1
        else -> 0
    }

    override fun read(buffer: BufferStream) {
        partition = buffer.long
        reference = buffer.long
    }

    override fun write(buffer: BufferStream) {
        buffer.putLong(partition)
        buffer.putLong(reference)
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        this.read(buffer)
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream, context: SchemaContext?) {
        this.write(buffer)
    }
}
