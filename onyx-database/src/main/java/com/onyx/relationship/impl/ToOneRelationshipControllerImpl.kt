package com.onyx.relationship.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.relationship.EntityRelationshipManager
import com.onyx.relationship.RelationshipInteractor
import com.onyx.relationship.RelationshipReference
import com.onyx.extension.*


/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Handles actions on a to one relationship
 */
class ToOneRelationshipControllerImpl
/**
 * Constructor
 *
 * @param entityDescriptor Entity descriptor
 * @param relationshipDescriptor relationship descriptor
 */
@Throws(OnyxException::class)
constructor(entityDescriptor: EntityDescriptor, relationshipDescriptor: RelationshipDescriptor, context: SchemaContext) : AbstractRelationshipController(entityDescriptor, relationshipDescriptor, context), RelationshipInteractor {

    /**
     * Hydrate relationship for entity
     *
     * @param entity           Entity to hydrate
     * @param manager          Relationship manager prevents recursion
     * @param force            Force hydrate
     */
    @Throws(OnyxException::class)
    override fun hydrateRelationshipForEntity(entity: IManagedEntity, manager: EntityRelationshipManager, force: Boolean) {
        manager.add(entity, context)

        val existingRelationshipReferenceObjects: MutableSet<RelationshipReference> = entity.relationshipReferenceMap(context, relationshipDescriptor.name)?.get(entity.toRelationshipReference(context)) ?: HashSet()

        if(!existingRelationshipReferenceObjects.isEmpty()) {
            val relationshipEntity:IManagedEntity? = existingRelationshipReferenceObjects.first().toManagedEntity(context, relationshipDescriptor.inverseClass)
            relationshipEntity?.hydrateRelationships(context, manager)
            val existing:IManagedEntity? = entity[context, entityDescriptor, relationshipDescriptor.name]
            if(existing != null && relationshipEntity != null)
                existing.copy(relationshipEntity, context)
            else
                entity[context, entityDescriptor, relationshipDescriptor.name] = relationshipEntity
        }
    }

    /**
     * Batch Save all relationship ids
     *
     * @param entity Entity to update
     * @param relationshipIdentifiers Relationship references
     */
    @Throws(OnyxException::class)
    override fun updateAll(entity: IManagedEntity, relationshipIdentifiers: MutableSet<RelationshipReference>) {}

}
