package com.onyx.entity

import com.onyx.descriptor.AttributeDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity attribute information
 */
@Entity(fileName = "system")
data class SystemAttribute @JvmOverloads constructor(

    @Identifier(loadFactor = 5)
    var name: String = "",

    @Suppress("MemberVisibilityCanPrivate")
    @Attribute
    var dataType: String? = null,

    @Attribute
    var size: Int = 0,

    @Attribute
    private var isNullable: Boolean = false,

    @Attribute
    @Suppress("MemberVisibilityCanPrivate")
    var isEnum: Boolean = false,

    @Attribute
    @Suppress("MemberVisibilityCanPrivate")
    var enumValues: String? = null

): ManagedEntity() {

    constructor(descriptor: AttributeDescriptor):this(
        name = descriptor.name,
        size = descriptor.size,
        dataType = descriptor.type.canonicalName,
        isNullable = descriptor.isNullable,
        isEnum = descriptor.isEnum,
        enumValues = descriptor.enumValues)
}
