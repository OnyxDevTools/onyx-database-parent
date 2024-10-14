package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.extension.common.instance
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Hydrate a managed entity based on this partition reference
 * @param context Schema context outlines where the data files are
 * @param descriptor Entity's descriptor information
 * @since 2.0.0
 */
fun Reference.toManagedEntity(context: SchemaContext, descriptor: EntityDescriptor):IManagedEntity? {
    val entity: IManagedEntity = descriptor.entityClass.instance(context.contextId)
    val records = entity.records(context = context, descriptor = descriptor, partitionId = partition)
    return records.getWithRecID(reference)
}

/**
 * Hydrate a managed entity based on this partition reference
 * @param context Schema context outlines where the data files are
 * @param clazz entity type
 * @param descriptor Entity's descriptor information
 * @since 2.0.0
 */
fun Reference.toManagedEntity(context: SchemaContext, clazz: Class<*>, descriptor: EntityDescriptor = context.getBaseDescriptorForEntity(clazz)!!):IManagedEntity? {
    val entity: IManagedEntity = descriptor.entityClass.instance(context.contextId)
    val records = entity.records(context = context, descriptor = descriptor, partitionId = partition)
    return records.getWithRecID(reference)
}