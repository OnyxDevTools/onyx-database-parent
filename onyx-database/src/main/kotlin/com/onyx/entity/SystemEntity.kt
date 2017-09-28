package com.onyx.entity

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType
import java.util.*

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity information
 */
@Entity(fileName = "system")
data class SystemEntity @JvmOverloads constructor(

    @Index(loadFactor = 3)
    var name: String = "",

    @Attribute
    var className: String? = null,

    @Attribute
    var fileName: String? = null,

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverseClass = SystemPartition::class, loadFactor = 3)
    var partition: SystemPartition? = null,

    @Attribute
    var attributes: MutableList<SystemAttribute> = ArrayList(),

    @Attribute
    var relationships: MutableList<SystemRelationship> = ArrayList(),

    @Attribute
    var indexes: MutableList<SystemIndex> = ArrayList(),

    @Attribute
    var identifier: SystemIdentifier? = null
) : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    var primaryKey: Int = 0

    constructor(descriptor: EntityDescriptor?) : this(
            name = descriptor!!.entityClass.name,
            className = descriptor.entityClass.simpleName,
            indexes = ArrayList(),
            relationships = ArrayList(),
            attributes = ArrayList(),
            fileName = descriptor.fileName) {

        this.identifier = SystemIdentifier(descriptor.identifier!!)
        this.attributes = descriptor.attributes.values.map { SystemAttribute(it) }.sortedBy { it.name }.toMutableList()
        this.relationships = descriptor.relationships.values.map { SystemRelationship(it) }.sortedBy { it.name }.toMutableList()
        this.indexes = descriptor.indexes.values.map { SystemIndex(it) }.sortedBy { it.name }.toMutableList()

        if (descriptor.partition != null) {
            this.partition = SystemPartition(descriptor.partition!!, this)
        }
    }
}
