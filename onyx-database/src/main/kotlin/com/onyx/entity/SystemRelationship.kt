package com.onyx.entity

import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*

/**
 * tim.osborn on 3/2/15.
 *
 * Relationship information for an entity
 */
@Suppress("MemberVisibilityCanPrivate")
@Entity(fileName = "system")
data class SystemRelationship @JvmOverloads constructor(

    @Identifier
    var name: String = "",

    @Attribute
    var inverse: String? = null,

    @Attribute
    var inverseClass: String = "",

    @Attribute
    var parentClass: String? = null,

    @Attribute
    var fetchPolicy: Byte = 0,

    @Attribute
    var cascadePolicy: Byte = 0,

    @Attribute
    var relationshipType: Byte = 0,

    @Attribute
    var loadFactor: Byte = 0

) : ManagedEntity() {

    constructor(relationshipDescriptor: RelationshipDescriptor):this(
        cascadePolicy = relationshipDescriptor.cascadePolicy.ordinal.toByte(),
        fetchPolicy = relationshipDescriptor.fetchPolicy.ordinal.toByte(),
        inverse = relationshipDescriptor.inverse,
        inverseClass = relationshipDescriptor.inverseClass.name,
        relationshipType = relationshipDescriptor.relationshipType.ordinal.toByte(),
        name = relationshipDescriptor.name,
        parentClass = relationshipDescriptor.parentClass.name,
        loadFactor = relationshipDescriptor.loadFactor
    )
}
