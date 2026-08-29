package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Get an index controller.
 *
 * @param context Schema context entity belongs to.
 * @name Name of the indexed property
 *
 * @since 2.0.0
 */
fun IManagedEntity.indexInteractor(context: SchemaContext, name:String, descriptor: EntityDescriptor = descriptor(context)) = context.getIndexInteractor(descriptor.indexes[name]!!)

/**
 * Save all indexes for an entity.  Sift through the indexed properties and update their references.  You must pass
 * in the previous reference of the entity so it can know what previous references it needs to update.
 *
 * @param context Schema context entity belongs to
 * @param previousReferenceId Previous entity reference id before updating entity
 * @param newReferenceId The newly saved reference id
 *
 * @since 2.0.0
 *
 */
fun IManagedEntity.saveIndexes(
    context: SchemaContext,
    previousReferenceId: Long,
    newReferenceId: Long,
    descriptor: EntityDescriptor = descriptor(context),
    previousEntity: IManagedEntity? = null
) {
    if (descriptor.hasIndexes) {
        require(previousReferenceId <= 0L || previousEntity != null) {
            "The previously persisted entity is required when updating indexes without reverse mappings"
        }
        // Save All Indexes
        descriptor.indexes.values.forEach {
            val indexInteractor = indexInteractor(context, it.name, descriptor)
            val oldIndexValue: Any? = previousEntity?.get(context, descriptor, it.name)
            val indexValue: Any? = if (
                this is VectorManagedEntity && it.name == VectorManagedEntity.REPRESENTATION_FIELD
            ) {
                consumePreparedVectorIndexValue()
            } else {
                get(context, descriptor, it.name)
            }
            indexInteractor.save(oldIndexValue, indexValue, previousReferenceId, newReferenceId)
        }
    }
}

/**
 * Delete all indexes for an entity.  This is typically done prior to deleting an entity so that the indexed data
 * is no longer hanging around.
 *
 * @param context Schema context the entity belongs to
 * @param referenceId Entity's current reference ID
 *
 * @since 2.0.0
 */
fun IManagedEntity.deleteAllIndexes(context: SchemaContext, referenceId:Long, descriptor: EntityDescriptor = descriptor(context)) {
    if (descriptor.hasIndexes) {
        descriptor.indexes.values.forEach {
            val indexValue: Any? = get(context, descriptor, it.name)
            indexInteractor(context, it.name, descriptor).delete(indexValue, referenceId)
        }
    }
}
