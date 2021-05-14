package com.onyx.interactors.relationship.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.interactors.record.RecordInteractor
import com.onyx.interactors.relationship.data.RelationshipTransaction
import com.onyx.interactors.relationship.data.RelationshipReference

import java.util.HashSet


/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Base class for handling relationships
 */
abstract class AbstractRelationshipInteractor @Throws(OnyxException::class) constructor(protected var entityDescriptor: EntityDescriptor, protected var relationshipDescriptor: RelationshipDescriptor, var context: SchemaContext) {

    protected var recordInteractor: RecordInteractor = context.getRecordInteractor(entityDescriptor)

    /**
     * Delete a relationship for an entity.  Go through and remove all the inverse relationships and handle the
     * cascading.
     *
     * @param entity Entity to remove relationships from
     * @param transaction Holder of the transaction information
     */
    @Throws(OnyxException::class)
    @Synchronized
    fun deleteRelationshipForEntity(entity: IManagedEntity, transaction: RelationshipTransaction) {
        transaction.add(entity, context)

        val relationshipReferenceMap = entity.relationshipReferenceMap(context, relationship = relationshipDescriptor.name)
        val entityRelationshipReference = entity.toRelationshipReference(context)
        val relationshipsToRemove:MutableSet<RelationshipReference> = HashSet(relationshipReferenceMap[entityRelationshipReference] ?: HashSet())
        relationshipReferenceMap[entityRelationshipReference] = HashSet()

        relationshipsToRemove.forEach {
            val entityToDelete = it.toManagedEntity(context, relationshipDescriptor.inverseClass)
            deleteInverseRelationshipReference(entity, entityRelationshipReference, it)
            if (relationshipDescriptor.shouldDeleteEntity && !transaction.contains(entityToDelete!!, context)) {
                entityToDelete.deleteAllIndexes(context, entityToDelete.referenceId(context))
                entityToDelete.recordInteractor(context).delete(entityToDelete)
                entityToDelete.deleteRelationships(context, transaction)
            }
        }
    }

    /**
     * Save the inverse relationship, this will handle both to many relationships and to one relationships
     *
     * @param parentIdentifier Parent entity identifier
     * @param childIdentifier Child entity identifier
     */
    @Throws(OnyxException::class)
    @Synchronized
    protected fun saveInverseRelationship(parentEntity: IManagedEntity, childEntity: IManagedEntity, parentIdentifier: RelationshipReference, childIdentifier: RelationshipReference) {
        val inverseRelationshipDescriptor = parentEntity.inverseRelationshipDescriptor(context, relationshipDescriptor.name) ?: return
        val relationshipMap = childEntity.relationshipReferenceMap(context, inverseRelationshipDescriptor.name)

        if(inverseRelationshipDescriptor.isToOne) {
            relationshipMap[childIdentifier] = hashSetOf(parentIdentifier)
            childEntity[context, inverseRelationshipDescriptor.entityDescriptor, inverseRelationshipDescriptor.name] = parentEntity
        } else {
            val relationshipReferences = HashSet(relationshipMap.getOrElse(childIdentifier) { HashSet() })
            if(!relationshipReferences.contains(parentIdentifier)) {
                relationshipReferences.add(parentIdentifier)
                relationshipMap[childIdentifier] = relationshipReferences
            }
        }
    }

    /**
     * Save the inverse relationship, this will handle both to many relationships and to one relationships
     *
     * @param parentIdentifier Parent entity identifier
     * @param childIdentifier Child entity identifier
     */
    @Throws(OnyxException::class)
    @Synchronized
    protected fun deleteInverseRelationshipReference(parentEntity: IManagedEntity, parentIdentifier: RelationshipReference, childIdentifier: RelationshipReference) {
        val inverseRelationshipDescriptor = parentEntity.inverseRelationshipDescriptor(context, relationshipDescriptor.name) ?: return
        val relationshipMap = childIdentifier.toManagedEntity(context, inverseRelationshipDescriptor.entityDescriptor.entityClass, inverseRelationshipDescriptor.entityDescriptor)?.relationshipReferenceMap(context, inverseRelationshipDescriptor.name) ?: return

        // Synchronized since we are saving the entire set
        val relationshipReferences = HashSet(relationshipMap.getOrElse(childIdentifier) { HashSet() })
        relationshipReferences.remove(parentIdentifier)
        relationshipMap[childIdentifier] = relationshipReferences
    }

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of relationship references
     */
    @Throws(OnyxException::class)
    fun getRelationshipIdentifiersWithReferenceId(referenceId: Reference): List<RelationshipReference> {
        val entity = referenceId.toManagedEntity(context, relationshipDescriptor.entityDescriptor)
        val existingReferences = entity?.relationshipReferenceMap(context, relationshipDescriptor.name)?.get(entity.toRelationshipReference(context))
        return existingReferences?.toList() ?: ArrayList()
    }
}
