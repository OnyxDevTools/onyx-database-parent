package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Get the partition id the entity belongs to or should belong to.  If a partition field is not defined, it will return -1@since
 * @param context Schema context defining entity properties
 * @return Partition id
 *
 * @since 2.0.0
 */
fun IManagedEntity.partitionId(context:SchemaContext, descriptor: EntityDescriptor = context.getDescriptorForEntity(this, "")):Long {
    if(hasPartition(context, descriptor)) {
        val partitionVal = partitionValue(context, descriptor)
        if(partitionVal != NULL_PARTITION) {
            val systemPartitionEntry = context.getPartitionWithValue(this::class.java, partitionVal)
            return systemPartitionEntry?.primaryKey?.toLong() ?: 0L
        }
    }
    return 0L
}

/**
 * Indicates whether the entity supports a partition
 *
 * @param context Schema context defining entity properties
 * @since 2.0.0
 */
fun IManagedEntity.hasPartition(context: SchemaContext, descriptor: EntityDescriptor = context.getDescriptorForEntity(this, "")):Boolean = descriptor.partition != null