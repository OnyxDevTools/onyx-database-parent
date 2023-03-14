package com.onyx.entity

import com.onyx.descriptor.EntityDescriptor
import com.onyx.extension.common.ClassMetadata
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator
import java.util.*

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity information
 */
@Entity(fileName = "system")
data class SystemEntity @JvmOverloads constructor(

    @Index
    var name: String = "",

    @Attribute
    var className: String? = null,

    @Attribute
    var fileName: String? = null,

    @Attribute
    var attributes: MutableList<SystemAttribute> = ArrayList(),

    @Attribute
    var relationships: MutableList<SystemRelationship> = ArrayList(),

    @Attribute
    var indexes: MutableList<SystemIndex> = ArrayList(),

    @Attribute
    var identifier: SystemIdentifier? = null
) : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var primaryKey: Int = 0

    @Suppress("unused")
    @Attribute
    var isLatestVersion:Boolean = true

    val type:Class<*> by lazy { ClassMetadata.classForName(name) }

    constructor(descriptor: EntityDescriptor?) : this(
            name = descriptor!!.entityClass.canonicalName,
            className = descriptor.entityClass.simpleName,
            indexes = ArrayList(),
            relationships = ArrayList(),
            attributes = ArrayList(),
            fileName = descriptor.fileName) {

        this.identifier = SystemIdentifier(descriptor.identifier!!)
        this.attributes = descriptor.attributes.values.map { SystemAttribute(it) }.sortedBy { it.name }.toMutableList()
        this.relationships = descriptor.relationships.values.map { SystemRelationship(it) }.sortedBy { it.name }.toMutableList()
        this.indexes = descriptor.indexes.values.map { SystemIndex(it) }.sortedBy { it.name }.toMutableList()
    }
}
