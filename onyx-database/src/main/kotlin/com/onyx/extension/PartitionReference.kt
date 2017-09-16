package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.fetch.PartitionReference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.util.ReflectionUtil

fun PartitionReference.toManagedEntity(context: SchemaContext, descriptor: EntityDescriptor):IManagedEntity {
    val entity: IManagedEntity = ReflectionUtil.instantiate(descriptor.entityClass) as IManagedEntity
    val records = entity.records(context, descriptor = descriptor, partitionId = partition)
    return records.getWithRecID(reference)
}