package com.onyx.interactors.relationship.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.collections.LazyRelationshipCollection
import com.onyx.persistence.context.SchemaContext
import com.onyx.interactors.relationship.data.RelationshipTransaction
import com.onyx.interactors.relationship.RelationshipInteractor
import com.onyx.interactors.relationship.data.RelationshipReference
import com.onyx.extension.*
import com.onyx.extension.common.catchAll
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.query.QueryListenerEvent

import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * Created by timothy.osborn on 2/5/15.
 *
 *
 * Handles the to many relationship persistence
 */
class ToManyRelationshipInteractor @Throws(OnyxException::class) constructor(entityDescriptor: EntityDescriptor, relationshipDescriptor: RelationshipDescriptor, context: SchemaContext) : AbstractRelationshipInteractor(entityDescriptor, relationshipDescriptor, context), RelationshipInteractor {

    /**
     * Save a relationship for an entity
     *
     * @param entity  Entity to save relationship
     * @param transaction Relationship transaction keeps track of actions already taken on entity relationships
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    @Synchronized
    override fun saveRelationshipForEntity(entity: IManagedEntity, transaction: RelationshipTransaction) {
        if (relationshipDescriptor.cascadePolicy === CascadePolicy.DEFER_SAVE) return

        val relationshipObjects: MutableList<IManagedEntity?>? = when (val reflectionRelationshipObjects:Any? = entity[context, relationshipDescriptor.entityDescriptor, relationshipDescriptor.name]) {
            is List<*> -> reflectionRelationshipObjects as MutableList<IManagedEntity?>
            else -> null
        }

        val parentRelationshipReference     = entity.toRelationshipReference(context)
        val relationshipReferenceMap        = entity.relationshipReferenceMap(context, relationshipDescriptor.name)
        val existingRelationshipReferences: MutableSet<RelationshipReference> = HashSet(relationshipReferenceMap[parentRelationshipReference] ?: HashSet())
        val existingRelationshipReferencesBefore = HashSet(existingRelationshipReferences)
        var existingRelationshipReferencesCopy: MutableSet<RelationshipReference?> = java.util.HashSet()

        if (relationshipObjects != null) {
            existingRelationshipReferencesCopy = HashSet(existingRelationshipReferences)

            relationshipObjects.forEach {

                // Get the inverse identifier
                val relationshipObjectIdentifier = it?.toRelationshipReference(context) ?: return@forEach

                // If it is in the list, it is accounted for, lets continue
                existingRelationshipReferencesCopy.remove(relationshipObjectIdentifier)

                // Cascade save the entity
                val entityDoesExist = if (relationshipDescriptor.shouldSaveEntity && !transaction.contains(it, context)) {
                    val relationshipDescriptor = it.descriptor(context)
                    val putResult = it.save(context, relationshipDescriptor)
                    relationshipObjectIdentifier.identifier = putResult.key

                    val reference = it.reference(putResult.recordId, context, relationshipDescriptor)
                    it.saveIndexes(context, if(putResult.isInsert) 0L else putResult.recordId, putResult.recordId)
                    it.saveRelationships(context, RelationshipTransaction(entity, context))
                    context.queryCacheInteractor.updateCachedQueryResultsForEntity(it, relationshipDescriptor, reference, if(putResult.isInsert) QueryListenerEvent.INSERT else QueryListenerEvent.UPDATE)
                    true
                } else {
                    it.reference(context).reference > 0L
                }

                // The entity exists yay, that means we can save it
                if (entityDoesExist) {
                    existingRelationshipReferences.add(relationshipObjectIdentifier)
                }

                // Save the inverse relationship
                if (!transaction.contains(it, context) && relationshipDescriptor.inverse != null && relationshipDescriptor.inverse!!.isNotEmpty()) {
                    saveInverseRelationship(entity, it, parentRelationshipReference, relationshipObjectIdentifier)
                }
            }
        }

        // Go through and delete the cascaded objects
        if (relationshipDescriptor.shouldDeleteEntityReference) {
            existingRelationshipReferencesCopy.forEach {
                // Delete the actual relationship
                existingRelationshipReferences.remove(it!!)

                // Delete the inverse
                deleteInverseRelationshipReference(entity, parentRelationshipReference, it)
                if(relationshipDescriptor.shouldDeleteEntity) {
                    val entityToDelete = it.toManagedEntity(context, relationshipDescriptor.inverseClass)
                    entityToDelete?.deleteAllIndexes(context, entityToDelete.referenceId(context))
                    entityToDelete?.deleteRelationships(context, transaction)
                    entityToDelete?.recordInteractor(context)?.delete(entityToDelete)
                }
            }
        }

        if(existingRelationshipReferences != existingRelationshipReferencesBefore) {
            relationshipReferenceMap[parentRelationshipReference] = existingRelationshipReferences
        }
    }

    /**
     * Hydrate relationship for entity
     *
     * @param entity           Entity to hydrate
     * @param transaction          Relationship transaction prevents recursion
     * @param force            Force hydrate
     */
    @Throws(OnyxException::class)
    override fun hydrateRelationshipForEntity(entity: IManagedEntity, transaction: RelationshipTransaction, force: Boolean) {
        if(entityDescriptor.hasRelationships) {
            transaction.add(entity, context)

            val existingRelationshipReferenceObjects: MutableSet<RelationshipReference> =
                entity.relationshipReferenceMap(context, relationshipDescriptor.name)[entity.toRelationshipReference(
                    context
                )] ?: HashSet()
            var relationshipObjects: MutableList<IManagedEntity>? =
                entity[context, entityDescriptor, relationshipDescriptor.name]

            when {
                relationshipDescriptor.fetchPolicy === FetchPolicy.LAZY && !force -> relationshipObjects =
                    LazyRelationshipCollection(
                        context.getDescriptorForEntity(relationshipDescriptor.inverseClass, ""),
                        ArrayList(existingRelationshipReferenceObjects),
                        context
                    )

                relationshipObjects == null && relationshipObjects !is LazyRelationshipCollection<*> -> relationshipObjects =
                    ArrayList()

                force && relationshipObjects is LazyRelationshipCollection<*> -> relationshipObjects = ArrayList()
                else -> relationshipObjects.clear()
            }

            if (relationshipDescriptor.fetchPolicy !== FetchPolicy.LAZY || force) {
                existingRelationshipReferenceObjects.forEach {
                    val relationshipObject = it.toManagedEntity(context, relationshipDescriptor.inverseClass)
                    relationshipObject?.hydrateRelationships(context, transaction)
                    if (relationshipObject != null)
                        relationshipObjects.add(relationshipObject)
                }
            }

            //sort related children if the child entity implements Comparable
            catchAll {
                if (relationshipObjects.size > 0 && relationshipObjects !is LazyRelationshipCollection && relationshipDescriptor.inverseClass.isAssignableFrom(
                        Comparable::class.java
                    )
                ) {
                    @Suppress("UNCHECKED_CAST")
                    (relationshipObjects as MutableList<Comparable<Any>>).sortBy { it }
                }
            }

            entity[context, entityDescriptor, relationshipDescriptor.name] = relationshipObjects
        }
    }

    /**
     * Batch Save all relationship ids
     *
     * @param entity                  entity to update
     * @param relationshipIdentifiers Relationship references
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun updateAll(entity: IManagedEntity, relationshipIdentifiers: MutableSet<RelationshipReference>) {
        entity.relationshipReferenceMap(context, relationshipDescriptor.name)[entity.toRelationshipReference(context)] =
            relationshipIdentifiers
    }

}
