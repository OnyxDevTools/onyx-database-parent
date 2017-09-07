package com.onyx.entity

import com.onyx.descriptor.AttributeDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.util.OffsetField

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity attribute information
 */
@Entity(fileName = "system")
data class SystemAttribute @JvmOverloads constructor(

    @Identifier
    @Attribute
    var name: String = "",

    @Attribute
    var dataType: String? = null,

    @Attribute
    var size: Int = 0,

    @Attribute
    private var isNullable: Boolean = false,

    @Attribute
    var isEnum: Boolean = false,

    @Attribute
    var enumValues: String? = null

): ManagedEntity() {

    constructor(descriptor: AttributeDescriptor):this(
        name = descriptor.name,
        size = descriptor.size,
        dataType = descriptor.type.canonicalName,
        isNullable = descriptor.isNullable,
        isEnum = descriptor.isEnum,
        enumValues = descriptor.enumValues)

    @Transient
    @JvmField
    var field: OffsetField? = null
}
