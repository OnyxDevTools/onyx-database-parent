package com.onyx.entity

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.PartitionDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by timothy.osborn on 3/5/15.
 *
 * Partition entity for an entity
 */
@Entity(fileName = "system")
data class SystemPartitionEntry @JvmOverloads constructor(

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var primaryKey: Int = 0,

    @Index
    var id: String? = null,

    @Attribute(size = 1024)
    var value: String = "",

    @Attribute(size = 2048)
    var fileName: String = "",

    @Index
    var index: Long = 0,

    @Index
    var entityClass: String = ""
) : AbstractSystemEntity(), IManagedEntity {

    constructor(entityDescriptor: EntityDescriptor, descriptor: PartitionDescriptor, index: Long):this (
        id = entityDescriptor.entityClass.name + descriptor.partitionValue,
        value = descriptor.partitionValue,
        fileName = entityDescriptor.fileName + descriptor.partitionValue,
        index = index,
        primaryKey = 0,
        entityClass = entityDescriptor.entityClass.name
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SystemPartitionEntry
        if (primaryKey != other.primaryKey) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primaryKey
        result = 31 * result + (id?.hashCode() ?: 0)
        return result
    }

}
