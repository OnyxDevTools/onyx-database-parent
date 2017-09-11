package com.onyx.extension

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * This extension makes available the index resources for a managed entity.  It also contains methods for manipulating
 * index data for an entity
 *
 * @author Tim Osborn
 * @since 2.0.0 Refactored to remove helper methods
 */

/**
 * Get an index controller.
 *
 * @param context Schema context entity belongs to.
 * @name Name of the indexed property
 *
 * @since 2.0.0
 */
fun IManagedEntity.indexController(context: SchemaContext, name:String) = context.getIndexController(descriptor(context).indexes[name]!!)

/**
 * Save all indexes for an entity.  Sift through the indexed properties and update their references.  You must pass
 * in the previous reference of the entity so it can know what previous references it needs to update.
 *
 * @param context Schema context entity belongs to
 * @param previousReferenceId Previous entity reference id before updating entity
 *
 * @since 2.0.0
 *
 */
fun IManagedEntity.saveIndexes(context: SchemaContext, previousReferenceId:Long) {
    val descriptor = descriptor(context)
    if (descriptor.hasIndexes) {
        val newReferenceId = referenceId(context)

        // Save All Indexes
        descriptor.indexes.values.forEach {
            val indexController = indexController(context, it.name)
            val indexValue = property(context, it.name)
            indexController.save(indexValue, previousReferenceId, newReferenceId)
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
fun IManagedEntity.deleteAllIndexes(context: SchemaContext, referenceId:Long) {
    val descriptor = descriptor(context)
    if (descriptor.hasIndexes) {
        descriptor.indexes.values.forEach {
            indexController(context, it.name).delete(referenceId)
        }
    }
}