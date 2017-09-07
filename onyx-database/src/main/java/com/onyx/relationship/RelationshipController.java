package com.onyx.relationship;

import com.onyx.exception.OnyxException;
import com.onyx.fetch.PartitionReference;
import com.onyx.persistence.IManagedEntity;

import java.util.List;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Contract on how to manipulate relationships
 */
public interface RelationshipController
{

    /**
     * Saves a relationship for an entity
     *
     * @param entity Entity being saved
     * @param manager Prevents recursion
     * @throws OnyxException Error saving realationship object
     */
    void saveRelationshipForEntity(IManagedEntity entity, EntityRelationshipManager manager) throws OnyxException;

    /**
     * Delete Relationship entity
     *
     * @param entityIdentifier Relationship reference
     * @param manager prevents recursion
     * @throws OnyxException Error deleting relationship
     */
    void deleteRelationshipForEntity(RelationshipReference entityIdentifier, EntityRelationshipManager manager) throws OnyxException;

    /**
     * Hydrate a relationship values.  Force the hydration if specified
     *
     * @param entityIdentifier entity relationship reference
     * @param entity Entity to hydrate
     * @param manager prevents infanite loop
     * @param force force hydrate
     * @throws OnyxException error hydrating relationship
     */
    void hydrateRelationshipForEntity(RelationshipReference entityIdentifier, IManagedEntity entity, EntityRelationshipManager manager, boolean force) throws OnyxException;

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of realtionship references
     */
    List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(Long referenceId) throws OnyxException;

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of relationship references
     */
    List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(PartitionReference referenceId) throws OnyxException;

    /**
     * Batch Save all relationship ids
     *
     * @param entity Entity to update
     * @param relationshipIdentifiers list of entity references
     */
    void updateAll(IManagedEntity entity, Set<RelationshipReference> relationshipIdentifiers) throws OnyxException;
}
