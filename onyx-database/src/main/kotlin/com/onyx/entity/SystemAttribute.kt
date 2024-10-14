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

    @Identifier
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
    var enumValues: String? = null,

    @Attribute
    var isPartition: Boolean = false

): ManagedEntity() {

    constructor(descriptor: AttributeDescriptor):this(
        name = descriptor.name,
        size = descriptor.size,
        dataType = descriptor.type.canonicalName,
        isNullable = descriptor.isNullable,
        isEnum = descriptor.isEnum,
        enumValues = descriptor.enumValues)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SystemAttribute

        if (name != other.name) return false
        if (dataType != other.dataType) return false
        if (isEnum != other.isEnum) return false
        if (enumValues != other.enumValues) return false
        if (isPartition != other.isPartition) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (dataType?.hashCode() ?: 0)
        result = 31 * result + isEnum.hashCode()
        result = 31 * result + (enumValues?.hashCode() ?: 0)
        result = 31 * result + isPartition.hashCode()
        return result
    }
}
