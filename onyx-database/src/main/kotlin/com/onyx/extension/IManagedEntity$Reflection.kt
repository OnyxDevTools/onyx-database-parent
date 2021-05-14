package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.InvalidConstructorException
import com.onyx.extension.common.*
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

// Indicates null value for a partition
const val nullPartition = ""

/**
 * Get the primary key or identifier of an entity
 *
 * @param context Schema context the entity belongs to
 * @return Property value of the identifier field
 * @since 2.0.0
 */
fun IManagedEntity.identifier(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, "")):Any? = this.getAny(descriptor!!.identifier!!.field)

/**
 * Copy an entity's properties from another entity of the same type
 *
 * @param from Entity to copy properties from
 * @param context Schema Context, this is not required but either the descriptor or context is required
 * @param descriptor Contains entity property information
 * @since 2.0.0
 */
fun IManagedEntity.copy(from:IManagedEntity, context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, "")) = descriptor!!.reflectionFields.values.forEach {
    catchAll {
        this.setAny(it,from.getAny(it))
    }
}

/**
 * Get the partition field value from the entity.  If the partition field does not exist it will return an empty value
 *
 * @param context Schema context the entity belongs to
 * @since 2.0.0
 */
fun IManagedEntity.partitionValue(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, "")):String {
    if(descriptor!!.partition != null) {
        val propertyValue:Any? = get(context, descriptor, descriptor.partition!!.name)
        return propertyValue?.toString() ?: nullPartition
    }
    return nullPartition
}

/**
 * Get the partition field value from the entity.  If the partition field does not exist it will return an empty value
 *
 * @param context Schema context the entity belongs to
 * @since 2.0.0
 */
fun IManagedEntity.setPartitionValue(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, ""), value:Any?) {
    if(descriptor!!.partition != null) {
        set(context, descriptor, descriptor.partition!!.name, value)
    }
}

/**
 * Overloaded operator to use entity[context, property] syntax
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
operator fun <T> IManagedEntity.get(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, ""), name:String): T? = this.getAny(descriptor!!.reflectionFields[name]!!)

/**
 * Overload operator to use entity[context, property] = value syntax
 * @since 2.0.0
 */
operator fun <T> IManagedEntity.set(context: SchemaContext? = null, descriptor: EntityDescriptor? = context?.getDescriptorForEntity(this, ""), name: String, value:T):T  { this.setAny(descriptor!!.reflectionFields[name]!!, value); return value }

/**
 * Instantiate a managed entity.  The only difference with this method is the exception handling
 * @return New Instance
 * @throws InvalidConstructorException Exception occurred while creating new value
 *
 * @since 2.0.0 Moved from Entity Descriptor class since it should not be creating new objects
 */
@Throws(InvalidConstructorException::class)
fun <T : IManagedEntity> Class<*>.createNewEntity(): T = try {
    this.instance()
} catch (e1: Exception) {
    throw InvalidConstructorException(InvalidConstructorException.CONSTRUCTOR_NOT_FOUND, e1)
}