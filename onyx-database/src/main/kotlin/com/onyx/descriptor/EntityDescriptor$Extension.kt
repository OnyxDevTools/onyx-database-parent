package com.onyx.descriptor

import com.onyx.interactors.record.RecordInteractor

/**
 * Get the record interactor corresponding to this descriptor
 *
 * @since 2.0.0
 */
fun EntityDescriptor.recordInteractor(): RecordInteractor = this.context.getRecordInteractor(this)

/**
 * Truncate All Data associated with an entity type.
 * This will also delete the underlying data files for non-system entities.
 *
 * @param includeAllPartitions Delete all partition data
 */
fun EntityDescriptor.truncateData(includeAllPartitions: Boolean) {
    // Check if this is a system entity
    val isSystemEntity = this.entityClass.name.startsWith("com.onyx.entity.")

    if (!hasPartition) {
        // Clear all records, indexes, and relationships
        this.recordInteractor().clear()
        this.indexes.values.forEach {
            context.getIndexInteractor(it).clear()
        }
        this.relationships.values.forEach {
            context.getRelationshipInteractor(it).clear()
        }
    }

    // Delete All Partitions data and indexes
    if (this.hasPartition && includeAllPartitions) {
        context.getAllPartitions(this.entityClass).forEach {
            context.getDescriptorForEntity(this.entityClass, it.value).apply {
                this.truncatePartitionData()
            }
        }
    }

    // Delete data files for non-system entities
    if (!isSystemEntity && !this.hasPartition) {
        context.deleteEntityDataFiles(this)
    }
}

/**
 * Truncate data for a specific partition.
 * This will also delete the underlying data files for the partition (for non-system entities).
 *
 * @param partitionId The partition ID to truncate
 */
fun EntityDescriptor.truncatePartitionData(partitionId: Long? = context.getPartitionWithValue(this.entityClass, this.partition?.partitionValue ?: "")?.primaryKey?.toLong()) {
    partitionId ?: return
    // Check if this is a system entity
    val isSystemEntity = this.entityClass.name.startsWith("com.onyx.entity.")

    val partition = context.getPartitionWithId(partitionId) ?: return
    val partitionDescriptor = context.getDescriptorForEntity(this.entityClass, partition.value)

    // Clear records, indexes, and relationships for this partition
    partitionDescriptor.recordInteractor().clear()
    partitionDescriptor.indexes.values.forEach {
        context.getIndexInteractor(it).clear()
    }
    partitionDescriptor.relationships.values.forEach {
        context.getRelationshipInteractor(it).clear()
    }

    // Delete the partition data files for non-system entities
    if (!isSystemEntity) {
        context.deletePartitionDataFiles(partitionDescriptor, partitionId)
        partition.let {
            context.systemPersistenceManager?.delete(it)
            context.clearPartitionCache(partition)
        }
    }
}
