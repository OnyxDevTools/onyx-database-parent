package com.onyx.interactors.relationship.data

import com.onyx.diskmap.serializer.ObjectBuffer
import com.onyx.diskmap.serializer.ObjectSerializable

import java.io.IOException

/**
 * Created by timothy.osborn on 3/19/15.
 *
 * Reference of a relationship
 */
class RelationshipReference @JvmOverloads constructor(var identifier: Any? = "", var partitionId: Long = 0L, var referenceId:Long = 0) : ObjectSerializable, Comparable<RelationshipReference> {

    @Throws(IOException::class)
    override fun writeObject(buffer: ObjectBuffer) {
        buffer.writeLong(partitionId)
        buffer.writeObject(identifier)
    }

    @Throws(IOException::class)
    override fun readObject(buffer: ObjectBuffer) {
        partitionId = buffer.readLong()
        identifier = buffer.readObject()
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

    override fun equals(other:Any?):Boolean = when {
        other !is RelationshipReference -> false
        other.identifier!!::class.java != identifier!!::class.java -> false
        else -> other.partitionId == partitionId && other.identifier == identifier
    }
}