package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.recordInteractor
import com.onyx.scan.PartitionReference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.interactors.record.RecordInteractor
import com.onyx.interactors.relationship.data.RelationshipReference

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
fun IManagedEntity.referenceId(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)):Long {
    val identifier = identifier(context)
    return if(identifier == null) 0L else recordInteractor(context, descriptor).getReferenceId(identifier)
}

/**
 * Returns the full reference object including the partition for the entity
 * @param context Schema context entity belongs to
 * @since 2.0.0
 */
fun IManagedEntity.reference(context: SchemaContext):PartitionReference = PartitionReference(partitionId(context), referenceId(context))

/**
 * Generate a relationship reference based on the entity's record information
 * @param context Schema context entity belongs to
 * @param descriptor Default the descriptor to the entity descriptor
 * @since 2.0.0
 */
fun IManagedEntity.toRelationshipReference(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)): RelationshipReference = RelationshipReference(identifier(context, descriptor), partitionId(context, descriptor), referenceId(context, descriptor))

/**
 * Get the record controller associated to this entity.
 *
 * @param context Schema context entity belongs to
 * @return The record controller manages the i/o of the entity
 * @since 2.0.0
 */
fun IManagedEntity.recordInteractor(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)): RecordInteractor = descriptor.recordInteractor()

/**
 * Save an entity within it's store
 *
 * @param context Schema context entity belongs to
 * @since 2.0.0
 */
fun IManagedEntity.save(context:SchemaContext, descriptor: EntityDescriptor = descriptor(context)):Any = recordInteractor(context, descriptor).save(this)
