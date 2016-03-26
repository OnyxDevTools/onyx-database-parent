package com.onyx.relationship;

import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.persistence.IManagedEntity;

import java.util.List;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/5/15.
 */
public interface RelationshipController
{

    /**
     * Saves a relationship for an entity
     *
     * @param entity
     * @param manager
     * @throws EntityException
     */
    void saveRelationshipForEntity(IManagedEntity entity, EntityRelationshipManager manager) throws EntityException;

    /**
     * Delete Relationship entity
     *
     * @param entityIdentifier
     * @param manager
     * @throws EntityException
     */
    void deleteRelationshipForEntity(RelationshipReference entityIdentifier, EntityRelationshipManager manager) throws EntityException;

    /**
     * Hydrate a relationship values.  Force the hydration if specified
     *
     * @param entityIdentifier
     * @param entity
     * @param manager
     * @param force
     * @throws EntityException
     */
    void hydrateRelationshipForEntity(RelationshipReference entityIdentifier, IManagedEntity entity, EntityRelationshipManager manager, boolean force) throws EntityException;

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return
     */
    List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(Long referenceId) throws EntityException;

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return
     */
    List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(PartitionReference referenceId) throws EntityException;

    /**
     * Batch Save all relationship ids
     *
     * @param entity
     * @param relationshipIdentifiers
     */
    void updateAll(IManagedEntity entity, Set<RelationshipReference> relationshipIdentifiers) throws EntityException;
}
