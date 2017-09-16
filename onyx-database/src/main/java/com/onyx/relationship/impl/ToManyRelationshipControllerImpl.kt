package com.onyx.relationship.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.collections.LazyRelationshipCollection
import com.onyx.persistence.context.SchemaContext
import com.onyx.relationship.EntityRelationshipManager
import com.onyx.relationship.RelationshipInteractor
import com.onyx.relationship.RelationshipReference
import com.onyx.extension.*

import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * Created by timothy.osborn on 2/5/15.
 *
 *
 * Handles the to many relationship persistence
 */
class ToManyRelationshipControllerImpl @Throws(OnyxException::class) constructor(entityDescriptor: EntityDescriptor, relationshipDescriptor: RelationshipDescriptor, context: SchemaContext) : AbstractRelationshipController(entityDescriptor, relationshipDescriptor, context), RelationshipInteractor {


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
        var relationshipObjects: MutableList<IManagedEntity>? = entity[context, entityDescriptor, relationshipDescriptor.name]

        when {
            relationshipDescriptor.fetchPolicy === FetchPolicy.LAZY && !force                    -> relationshipObjects = LazyRelationshipCollection(context.getDescriptorForEntity(relationshipDescriptor.inverseClass, ""), existingRelationshipReferenceObjects, context)
            relationshipObjects == null && relationshipObjects !is LazyRelationshipCollection<*> -> relationshipObjects = ArrayList()
            force && relationshipObjects is LazyRelationshipCollection<*>                        -> relationshipObjects = ArrayList()
            else -> relationshipObjects.clear()
        }

        if (relationshipDescriptor.fetchPolicy !== FetchPolicy.LAZY || force) {
            existingRelationshipReferenceObjects.forEach {
                val relationshipObject = it.toManagedEntity(context, relationshipDescriptor.inverseClass)
                relationshipObject?.hydrateRelationships(context, manager)
                if(relationshipObject != null)
                    relationshipObjects!!.add(relationshipObject)
            }
        }

        //sort related children if the child entity implements Comparable
        if (relationshipObjects.size > 0 && relationshipObjects !is LazyRelationshipCollection && relationshipObjects[0] is Comparable<*>) {
            @Suppress("UNCHECKED_CAST")
            (relationshipObjects as MutableList<Comparable<Any>>).sortBy { it }
        }

        entity[context, entityDescriptor, relationshipDescriptor.name] = relationshipObjects
    }

}
