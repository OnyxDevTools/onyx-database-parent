package com.onyx.relationship.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.AttributeMissingException
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.fetch.PartitionReference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.interactors.record.RecordInteractor
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.relationship.EntityRelationshipManager
import com.onyx.relationship.RelationshipReference
import com.onyx.util.ReflectionUtil

import java.util.HashSet

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Base class for handling relationships
 */
abstract class AbstractRelationshipController @Throws(OnyxException::class) constructor(protected var entityDescriptor: EntityDescriptor, protected var relationshipDescriptor: RelationshipDescriptor, var context: SchemaContext) {

    protected var recordInteractor: RecordInteractor = context.getRecordInteractor(entityDescriptor)

    @Throws(OnyxException::class)
    fun saveRelationshipForEntity(entity: IManagedEntity, manager: EntityRelationshipManager) {

        if (relationshipDescriptor.cascadePolicy === CascadePolicy.DEFER_SAVE && relationshipDescriptor.isToMany) {
            return
        }
        if(manager.contains(entity, context))
            return
        manager.add(entity, context)


        val relationshipValue:Any? = entity.get(context, descriptor = entityDescriptor, name = relationshipDescriptor.name)

        @Suppress("UNCHECKED_CAST")
        val relationshipObjects: List<IManagedEntity> = when (relationshipValue) {
                                                            null -> arrayListOf()
                                                            is List<*> -> relationshipValue as List<IManagedEntity>
                                                            else -> arrayListOf(relationshipValue) as List<IManagedEntity>
                                                        }

        val parentEntityRelationshipReference = RelationshipReference(entity.identifier(context), entity.partitionId(context))
        val relationshipReferenceMap = entity.relationshipReferenceMap(context, relationship = relationshipDescriptor.name)
        val relationshipMapToActual: MutableMap<RelationshipReference, IManagedEntity> = HashMap()

        val newRelationshipReferences = HashSet<RelationshipReference>()

        // Map relationship entities to Relationship References
        relationshipObjects.forEach {
            var partitionReference: PartitionReference?

            if (relationshipDescriptor.shouldSaveEntity
                    && !manager.contains(it, context)) {

                partitionReference = it.reference(context)
                it.save(context)
                it.saveIndexes(context, partitionReference.reference)
                it.saveRelationships(context, manager)

            }

            partitionReference = it.reference(context)

            val relationshipReference = RelationshipReference(it.identifier(context), partitionReference.partition, partitionReference.reference)

            if (relationshipReference.referenceId > 0) {
                newRelationshipReferences.add(relationshipReference)
                relationshipMapToActual[relationshipReference] = it // Map a reference to the actual value so that I may use it later on for cascading
            }
        }


        // If the structure does not exist, lets create one and persist it
        synchronized(relationshipReferenceMap!!) {
            val existingRelationshipReferences = relationshipReferenceMap.getOrElse(parentEntityRelationshipReference) { HashSet() }

            if(relationshipDescriptor.shouldDeleteEntityReference) {
                val allRemovedItems = existingRelationshipReferences - newRelationshipReferences
                allRemovedItems.forEach {
                    if(!manager.contains(it.toManagedEntity(context, relationshipDescriptor.inverseClass)!!, context)) {
                        deleteInverseRelationshipReference(entity, parentEntityRelationshipReference, it)
                        if (relationshipDescriptor.shouldDeleteEntity) {
                            val entityToDelete = it.toManagedEntity(context, relationshipDescriptor.inverseClass)
                            entityToDelete?.deleteAllIndexes(context, it.referenceId)
                            entityToDelete?.deleteRelationships(context, manager)
                            entityToDelete?.recordInteractor(context)?.delete(entityToDelete)
                        }
                    }
                }
            }

            if(relationshipDescriptor.isToMany && !relationshipDescriptor.shouldDeleteEntityReference) {
                relationshipReferenceMap.put(parentEntityRelationshipReference, (existingRelationshipReferences + newRelationshipReferences).toMutableSet())
            } else {
                relationshipReferenceMap.put(parentEntityRelationshipReference, newRelationshipReferences.toMutableSet())
            }
        }

        newRelationshipReferences.forEach { saveInverseRelationship(entity, relationshipMapToActual[it]!!, parentEntityRelationshipReference, it) }

    }

    @Throws(OnyxException::class)
    fun deleteRelationshipForEntity(entity: IManagedEntity, manager: EntityRelationshipManager) {
        manager.add(entity, context)

        val relationshipReferenceMap = entity.relationshipReferenceMap(context, relationship = relationshipDescriptor.name)
        val entityRelationshipReference = entity.toRelationshipReference(context)
        var relationshipsToRemove:MutableSet<RelationshipReference>? = null

        synchronized(relationshipReferenceMap!!) {
            relationshipsToRemove = relationshipReferenceMap[entityRelationshipReference]
            relationshipReferenceMap.put(entityRelationshipReference, HashSet())
        }

        relationshipsToRemove?.forEach {
            val entityToDelete = it.toManagedEntity(context, relationshipDescriptor.inverseClass)
            deleteInverseRelationshipReference(entity, entityRelationshipReference, it)
            if (relationshipDescriptor.shouldDeleteEntity) {
                entityToDelete?.deleteAllIndexes(context, it.referenceId)
                entityToDelete?.deleteRelationships(context, manager)
                entityToDelete?.recordInteractor(context)?.delete(entityToDelete)
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
    private fun saveInverseRelationship(parentEntity: IManagedEntity, childEntity: IManagedEntity, parentIdentifier: RelationshipReference, childIdentifier: RelationshipReference) {
        val inverseRelationshipDescriptor = parentEntity.inverseRelationshipDescriptor(context, relationshipDescriptor.name) ?: return

        // Get the Data Map that corresponds to the inverse relationship
        val relationshipMap = childEntity.relationshipReferenceMap(context, inverseRelationshipDescriptor.name)

        // Synchronized since we are saving the entire set
        synchronized(relationshipMap!!) {
            // Push it on the toManyRelationships
            val toManyRelationships = relationshipMap.getOrElse(childIdentifier) { HashSet() }
            synchronized(toManyRelationships) {
                if(inverseRelationshipDescriptor.isToOne)
                    toManyRelationships.clear()
                toManyRelationships.add(parentIdentifier)
            }

            // Save the relationship by
            relationshipMap.put(childIdentifier, toManyRelationships)
        }

        if(inverseRelationshipDescriptor.isToOne)
            childEntity.set(context, descriptor = inverseRelationshipDescriptor.entityDescriptor, name = inverseRelationshipDescriptor.name, value = parentEntity)
    }

    /**
     * Save the inverse relationship, this will handle both to many relationships and to one relationships
     *
     * @param parentIdentifier Parent entity identifier
     * @param childIdentifier Child entity identifier
     */
    @Throws(OnyxException::class)
    private fun deleteInverseRelationshipReference(parentEntity: IManagedEntity, parentIdentifier: RelationshipReference, childIdentifier: RelationshipReference) {
        val inverseRelationshipDescriptor = parentEntity.inverseRelationshipDescriptor(context, relationshipDescriptor.name) ?: return

        val relationshipMap = childIdentifier.toManagedEntity(context, inverseRelationshipDescriptor.entityDescriptor.entityClass, inverseRelationshipDescriptor.entityDescriptor)?.relationshipReferenceMap(context, inverseRelationshipDescriptor.name)!!

        // Synchronized since we are saving the entire set
        synchronized(relationshipMap) {
            // Push it on the toManyRelationships
            val toManyRelationships = relationshipMap.getOrElse(childIdentifier) { HashSet() }
            synchronized(toManyRelationships) {
                toManyRelationships.remove(parentIdentifier)
            }
            relationshipMap.put(childIdentifier, toManyRelationships)
        }
    }

    /**
     * Helper that uses reflection to get the relationship object, for a to one relationship
     *
     * @param entity Entity to reflect
     * @return Entity relationship value
     * @throws com.onyx.exception.AttributeMissingException relationship property does not exist
     */
    @Throws(AttributeMissingException::class)
    @Deprecated("Should use IManagedEntity+Reflection")
    fun getRelationshipValue(entity: IManagedEntity): IManagedEntity? {
        try {
            val relationshipField = relationshipDescriptor.field
            return ReflectionUtil.getAny(entity, relationshipField) as IManagedEntity?
        } catch (e: OnyxException) {
            throw AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE)
        }
    }

    /**
     * Get Relationship Identifiers
     *
     * @param referenceId Relationship reference
     * @return List of relationship references
     */
    @Throws(OnyxException::class)
    fun getRelationshipIdentifiersWithReferenceId(referenceId: Long?): List<RelationshipReference> {
        val entity = recordInteractor.getWithReferenceId(referenceId!!)!!
        val existingReferences = entity.relationshipReferenceMap(context, relationshipDescriptor.name)?.get(entity.toRelationshipReference(context))
        return existingReferences?.toList() ?: ArrayList()
    }

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of relationship references
     */
    @Throws(OnyxException::class)
    fun getRelationshipIdentifiersWithReferenceId(referenceId: PartitionReference): List<RelationshipReference> {
        val entity = referenceId.toManagedEntity(context, relationshipDescriptor.entityDescriptor)
        val existingReferences = entity.relationshipReferenceMap(context, relationshipDescriptor.name)?.get(entity.toRelationshipReference(context))
        return existingReferences?.toList() ?: ArrayList()
    }

    /**
     * Batch Save all relationship ids
     *
     * @param entity                  entity to update
     * @param relationshipIdentifiers Relationship references
     */
    @Throws(OnyxException::class)
    open fun updateAll(entity: IManagedEntity, relationshipIdentifiers: MutableSet<RelationshipReference>) {
        entity.relationshipReferenceMap(context, relationshipDescriptor.name)?.put(entity.toRelationshipReference(context), relationshipIdentifiers)
    }
}
