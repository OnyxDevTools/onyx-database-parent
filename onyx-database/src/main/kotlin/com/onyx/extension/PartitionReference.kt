package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.fetch.PartitionReference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.util.ReflectionUtil

/**
 * Hydrate a managed entity based on this partition reference
 * @param context Schema context outlines where the data files are
 * @param descriptor Entity's descriptor information
 * @since 2.0.0
 */
fun PartitionReference.toManagedEntity(context: SchemaContext, descriptor: EntityDescriptor):IManagedEntity {
    val entity: IManagedEntity = ReflectionUtil.instantiate(descriptor.entityClass) as IManagedEntity
    val records = entity.records(context = context, descriptor = descriptor, partitionId = partition)
    return records.getWithRecID(reference)
}