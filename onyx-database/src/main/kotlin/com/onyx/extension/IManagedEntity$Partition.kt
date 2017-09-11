package com.onyx.extension

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Get the partition id the entity belongs to or should belong to.  If a partition field is not defined, it will return -1@since
 * @param context Schema context defining entity properties
 * @return Partition id
 */
fun IManagedEntity.partitionId(context:SchemaContext):Long {
    if(hasPartition(context)) {
        val partitionVal = partitionValue(context)
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
 */
fun IManagedEntity.hasPartition(context: SchemaContext):Boolean = descriptor(context).partition != null