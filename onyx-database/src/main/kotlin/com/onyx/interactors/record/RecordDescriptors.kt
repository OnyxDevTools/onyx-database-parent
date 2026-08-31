package com.onyx.interactors.record

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.context.SchemaContext

/** Resolves the concrete descriptor represented by a query collector reference. */
internal fun SchemaContext.descriptorForReference(
    reference: Reference,
    entityClass: Class<*>,
    baseDescriptor: EntityDescriptor,
): EntityDescriptor {
    if (reference.partition == 0L) return baseDescriptor
    val partition = requireNotNull(getPartitionWithId(reference.partition)) {
        "Partition ${reference.partition} disappeared before its record could be mutated"
    }
    return getDescriptorForEntity(entityClass, partition.value)
}
