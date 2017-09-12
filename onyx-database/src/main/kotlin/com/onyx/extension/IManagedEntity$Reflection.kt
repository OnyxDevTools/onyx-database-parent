package com.onyx.extension

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.util.ReflectionUtil

// Indicates null value for a partition
val NULL_PARTITION = ""

/**
 * Get a reflective property
 * @param context Schema context entity belongs to
 * @param name name of the property to get
 * @since 2.0.0
 * @return Property value
 */
fun IManagedEntity.property(context: SchemaContext, name: String):Any? = ReflectionUtil.getAny(this, descriptor(context).attributes[name]!!.field)

/**
 * Get the primary key or identifier of an entity
 *
 * @param context Schema context the entity belongs to
 * @return Property value of the identifier field
 * @since 2.0.0
 */
fun IManagedEntity.identifier(context: SchemaContext):Any? {
    val descriptor = descriptor(context)
    return ReflectionUtil.getAny(this, descriptor.identifier!!.field)
}

/**
 * Get the partition field value from the entity.  If the partition field does not exist it will return an empty value
 *
 * @param context Schema context the entity belongs to
 * @since 2.0.0
 */
fun IManagedEntity.partitionValue(context: SchemaContext):String {
    val descriptor = descriptor(context)
    if(descriptor.partition != null) {
        val propertyValue = property(context, descriptor.partition!!.name)
        return propertyValue?.toString() ?: NULL_PARTITION
    }
    return NULL_PARTITION
}

/**
 * Overloaded operator to use entity[property] syntax
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
operator fun <T : Any?> IManagedEntity.get(context: SchemaContext, name:String): T = property(context = context, name = name) as T

/**
 * Overload operator to use entity[context, property] = value syntax
 * @since 2.0.0
 */
operator fun <T : Any?> IManagedEntity.set(context: SchemaContext, name: String, value:T):T  { ReflectionUtil.setAny(this, value, descriptor(context).attributes[name]!!.field); return value }
