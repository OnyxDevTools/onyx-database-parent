package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.reflection.Reflection

/**
 * Hydrate a managed entity based on this partition reference
 * @param context Schema context outlines where the data files are
 * @param descriptor Entity's descriptor information
 * @since 2.0.0
 */
fun Reference.toManagedEntity(context: SchemaContext, descriptor: EntityDescriptor):IManagedEntity {
    val entity: IManagedEntity = Reflection.instantiate(descriptor.entityClass)
    val records = entity.records(context = context, descriptor = descriptor, partitionId = partition)
    return records.getWithRecID(reference)!!
}

/**
 * Hydrate a managed entity based on this partition reference
 * @param context Schema context outlines where the data files are
 * @param clazz entity type
 * @param descriptor Entity's descriptor information
 * @since 2.0.0
 */
fun Reference.toManagedEntity(context: SchemaContext, clazz: Class<*>, descriptor: EntityDescriptor = context.getBaseDescriptorForEntity(clazz)!!):IManagedEntity? {
    val entity: IManagedEntity = Reflection.instantiate(descriptor.entityClass)
    val records = entity.records(context = context, descriptor = descriptor, partitionId = partition)
    return records.getWithRecID(reference)
}