package com.onyx.descriptor

import com.onyx.interactors.record.RecordInteractor

/**
 * Get the record interactor corresponding to this descriptor
 *
 * @since 2.0.0
 */
fun EntityDescriptor.recordInteractor(): RecordInteractor = this.context.getRecordInteractor(this)

/**
 * Truncate All Data associated with an entity type
 *
 * @param includeAllPartitions Delete all partition data
 */
fun EntityDescriptor.truncateData(includeAllPartitions: Boolean) {
    this.recordInteractor().clear()
    this.indexes.values.forEach {
        context.getIndexInteractor(it).clear()
    }
    this.relationships.values.forEach {
        context.getRelationshipInteractor(it).clear()
    }

    // Delete All Partitions data and indexes
    if (this.hasPartition && includeAllPartitions) {
        context.getAllPartitions(this.entityClass).forEach {
            context.getDescriptorForEntity(this.entityClass, it.value).apply {
                this.recordInteractor().clear()
                this.indexes.values.forEach {
                    context.getIndexInteractor(it).clear()
                }
                this.relationships.values.forEach {
                    context.getRelationshipInteractor(it).clear()
                }
            }
        }
    }
}
