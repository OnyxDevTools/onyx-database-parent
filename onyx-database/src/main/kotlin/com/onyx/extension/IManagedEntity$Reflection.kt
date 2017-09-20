package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.util.ReflectionUtil

// Indicates null value for a partition
val NULL_PARTITION = ""

/**
 * Get the primary key or identifier of an entity
 *
 * @param context Schema context the entity belongs to
 * @return Property value of the identifier field
 * @since 2.0.0
 */
fun IManagedEntity.identifier(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, "")):Any? = ReflectionUtil.getAny(this, descriptor!!.identifier!!.field)

/**
 * Copy an entity's properties from another entity of the same type
 *
 * @param from Entity to copy properties from
 * @param context Schema Context, this is not required but either the descriptor or context is required
 * @param descriptor Contains entity property information
 * @since 2.0.0
 */
fun IManagedEntity.copy(from:IManagedEntity, context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, "")) = ReflectionUtil.copy(from, this, descriptor)

/**
 * Get the partition field value from the entity.  If the partition field does not exist it will return an empty value
 *
 * @param context Schema context the entity belongs to
 * @since 2.0.0
 */
fun IManagedEntity.partitionValue(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, "")):String {
    if(descriptor!!.partition != null) {
        val propertyValue:Any? = get(context, descriptor, descriptor.partition!!.name)
        return propertyValue?.toString() ?: NULL_PARTITION
    }
    return NULL_PARTITION
}

/**
 * Overloaded operator to use entity[context, property] syntax
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
operator fun <T : Any?> IManagedEntity.get(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, ""), name:String): T? = ReflectionUtil.getAny(this, descriptor?.reflectionFields!![name]) as T?

/**
 * Overload operator to use entity[context, property] = value syntax
 * @since 2.0.0
 */
operator fun <T : Any?> IManagedEntity.set(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, ""), name: String, value:T):T  { ReflectionUtil.setAny(this, value, descriptor?.reflectionFields!![name]); return value }
