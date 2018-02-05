package com.onyx.entity

import com.onyx.descriptor.PartitionDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Relationship
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Partition information for an entity
 */
@Entity(fileName = "system")
data class SystemPartition @JvmOverloads constructor(

    @Identifier(loadFactor = 3)
    var id: String = "",

    @Attribute
    var name: String = "",

    @Attribute
    var entityClass: String = ""

) : ManagedEntity() {

    constructor(descriptor: PartitionDescriptor, entity: SystemEntity):this (
        id = entity.name + descriptor.name,
        name = descriptor.name,
        entityClass = entity.name
    )

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.SAVE, inverse = "partition", inverseClass = SystemPartitionEntry::class, fetchPolicy = FetchPolicy.EAGER, loadFactor = 3)
    var entries: CopyOnWriteArrayList<SystemPartitionEntry> = CopyOnWriteArrayList()

}
