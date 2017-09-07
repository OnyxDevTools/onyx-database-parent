package com.onyx.entity

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*

import java.util.ArrayList

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity information
 */
@Entity(fileName = "system")
data class SystemEntity @JvmOverloads constructor(

    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    @Attribute
    var primaryKey: Int = 0,

    @Index(loadFactor = 3)
    @Attribute
    var name: String = "",

    @Attribute
    var className: String? = null,

    @Attribute
    var fileName: String? = null,

    @Attribute
    var identifier: SystemIdentifier? = null,

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverse = "entity", inverseClass = SystemPartition::class, loadFactor = 3)
    var partition: SystemPartition? = null,

    @Attribute
    var attributes: MutableList<SystemAttribute> = ArrayList(),

    @Attribute
    var relationships: MutableList<SystemRelationship> = ArrayList(),

    @Attribute
    var indexes: MutableList<SystemIndex> = ArrayList()

) : ManagedEntity() {

    constructor(descriptor: EntityDescriptor?) : this(
            name = descriptor!!.clazz.name,
            className = descriptor.clazz.simpleName,
            indexes = ArrayList(),
            relationships = ArrayList(),
            attributes = ArrayList(),
            fileName = descriptor.fileName) {

        this.identifier = SystemIdentifier(descriptor.identifier)

        descriptor.attributes.values.forEach {
            this.attributes.add(SystemAttribute(it))
        }

        descriptor.relationships.values.forEach {
            this.relationships.add(SystemRelationship(it))
        }

        descriptor.indexes.values.forEach {
            this.indexes.add(SystemIndex(it))
        }

        if (descriptor.partition != null) {
            this.partition = SystemPartition(descriptor.partition, this)
        }

        this.attributes.sortBy { it.name }
        this.relationships.sortBy { it.name }
        this.indexes.sortBy { it.name }
    }
}
