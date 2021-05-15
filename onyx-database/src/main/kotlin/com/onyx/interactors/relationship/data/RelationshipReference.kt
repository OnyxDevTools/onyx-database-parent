package com.onyx.interactors.relationship.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable

/**
 * Created by timothy.osborn on 3/19/15.
 *
 * Reference of a relationship
 */
class RelationshipReference @JvmOverloads constructor(var identifier: Any? = "", var partitionId: Int = 0) : BufferStreamable, Comparable<RelationshipReference> {

    override fun read(buffer: BufferStream) {
        partitionId = buffer.int
        identifier = buffer.value
    }

    override fun write(buffer: BufferStream) {
        buffer.putInt(partitionId)
        buffer.putObject(identifier)
    }

    override fun compareTo(other: RelationshipReference): Int = when {
        this.partitionId < other.partitionId -> -1
        this.partitionId > other.partitionId -> 1
        other.identifier!!.javaClass == this.identifier!!.javaClass && this.identifier is Comparable<*> -> @Suppress("UNCHECKED_CAST") (this.identifier as Comparable<Any>).compareTo(other.identifier!! as Comparable<Any>)
        else -> 0
    }

    override fun hashCode(): Int = when (identifier) {
        null -> partitionId.hashCode()
        else -> identifier!!.hashCode() + partitionId.hashCode()
    }

    override fun equals(other:Any?):Boolean = other != null && (other as RelationshipReference).partitionId == partitionId && other.identifier == identifier
}