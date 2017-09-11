package com.onyx.extension

import com.onyx.descriptor.recordController
import com.onyx.fetch.PartitionReference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.record.RecordController

/**
 * Get the entity's reference ID from the store.
 *
 * @param context Schema context the entity belongs to
 *
 * @return This is the location where the entity is stored within its
 * data store.
 *
 * @since 2.0.0
 */
fun IManagedEntity.referenceId(context: SchemaContext):Long {
    val identifier = identifier(context)
    return if(identifier == null) 0L else recordController(context).getReferenceId(identifier)
}

/**
 * Returns the full reference object including the partition for the entity
 */
fun IManagedEntity.reference(context: SchemaContext):PartitionReference = PartitionReference(partitionId(context), referenceId(context))

/**
 * Get the record controller associated to this entity.
 *
 * @param context Schema context entity belongs to
 * @return The record controller manages the i/o of the entity
 * @since 2.0.0
 */
fun IManagedEntity.recordController(context: SchemaContext): RecordController = descriptor(context).recordController()

/**
 * Save an entity within it's store
 *
 * @param context Schema context entity belongs to
 * @since 2.0.0
 */
fun IManagedEntity.save(context:SchemaContext):Any = recordController(context).save(this)
