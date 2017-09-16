package com.onyx.relationship

import com.onyx.exception.OnyxException
import com.onyx.fetch.PartitionReference
import com.onyx.persistence.IManagedEntity

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Contract on how to manipulate relationships
 */
interface RelationshipInteractor {

    /**
     * Saves a relationship for an entity
     *
     * @param entity Entity being saved
     * @param manager Prevents recursion
     * @throws OnyxException Error saving realationship object
     */
    @Throws(OnyxException::class)
    fun saveRelationshipForEntity(entity: IManagedEntity, manager: EntityRelationshipManager)

    /**
     * Delete Relationship entity
     *
     * @param relationshipToRemove Relationship reference
     * @param manager prevents recursion
     * @throws OnyxException Error deleting relationship
     */
    @Throws(OnyxException::class)
    fun deleteRelationshipForEntity(entity:IManagedEntity, manager: EntityRelationshipManager)

    /**
     * Hydrate a relationship values.  Force the hydration if specified
     *
     * @param entity Entity to hydrate
     * @param manager prevents infanite loop
     * @param force force hydrate
     * @throws OnyxException error hydrating relationship
     */
    @Throws(OnyxException::class)
    fun hydrateRelationshipForEntity(entity: IManagedEntity, manager: EntityRelationshipManager, force: Boolean)

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of realtionship references
     */
    @Throws(OnyxException::class)
    fun getRelationshipIdentifiersWithReferenceId(referenceId: Long?): List<RelationshipReference>

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of relationship references
     */
    @Throws(OnyxException::class)
    fun getRelationshipIdentifiersWithReferenceId(referenceId: PartitionReference): List<RelationshipReference>

    /**
     * Batch Save all relationship ids
     *
     * @param entity Entity to update
     * @param relationshipIdentifiers list of entity references
     */
    @Throws(OnyxException::class)
    fun updateAll(entity: IManagedEntity, relationshipIdentifiers: MutableSet<RelationshipReference>)
}
