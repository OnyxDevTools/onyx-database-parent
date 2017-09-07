package com.onyx.entity

import com.onyx.descriptor.PartitionDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*

import java.util.ArrayList

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Partition information for an entity
 */
@Entity(fileName = "system")
data class SystemPartition @JvmOverloads constructor(

    @Attribute
    @Identifier(loadFactor = 3)
    var id: String = "",

    @Attribute
    var name: String = "",

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "partition", inverseClass = SystemEntity::class, loadFactor = 3)
    var entity: SystemEntity? = null,

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.SAVE, inverse = "partition", inverseClass = SystemPartitionEntry::class, fetchPolicy = FetchPolicy.EAGER, loadFactor = 3)
    var entries: MutableList<SystemPartitionEntry> = ArrayList()

) : ManagedEntity() {

    constructor(descriptor: PartitionDescriptor, entity: SystemEntity):this (
        entity = entity,
        name = descriptor.name,
        id = entity.name + descriptor.name
    )

}
