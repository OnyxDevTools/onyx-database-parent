package com.onyx.relationship.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.helpers.IndexHelper;
import com.onyx.helpers.RelationshipHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.record.impl.SequenceRecordControllerImpl;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Handles actions on a to one relationship
 */
@SuppressWarnings("unchecked")
public class ToOneRelationshipControllerImpl extends AbstractRelationshipController implements RelationshipController {

    private DiskMap<RelationshipReference, RelationshipReference> toOneMap = null;

    /**
     * Constructor
     *
     * @param entityDescriptor Entity descriptor
     * @param relationshipDescriptor relationship descriptor
     */
    public ToOneRelationshipControllerImpl(EntityDescriptor entityDescriptor, RelationshipDescriptor relationshipDescriptor, SchemaContext context) throws EntityException
    {
        super(entityDescriptor, relationshipDescriptor, context);

        MapBuilder mapBuilder = context.getDataFile(entityDescriptor);
        // Get the correct data file
        toOneMap = (DiskMap)mapBuilder.getHashMap(entityDescriptor.getClazz().getName() + relationshipDescriptor.getName(), RELATIONSHIP_MAP_LOAD_FACTOR);
    }


    /**
     * Save Relationship for entity
     *
     * @param entity Entity to save relationships for
     * @param manager Prevents recursion
     */
    public void saveRelationshipForEntity(IManagedEntity entity, EntityRelationshipManager manager) throws EntityException
    {
        IManagedEntity relationshipObject = getRelationshipValue(entity);

        RelationshipReference currentInverseIdentifier = null;
        final RelationshipReference entityIdentifier = new RelationshipReference(AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier()), getPartitionId(entity));

        // Hydrate the inverse identifier if it exists
        if (relationshipObject != null && currentInverseIdentifier == null)
        {
            // Get Inverse Partition ID
            long partitionID = getPartitionId(relationshipObject);
            Object id = AbstractRecordController.getIndexValueFromEntity(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier());
            boolean exists = getRecordControllerForEntity(relationshipObject).existsWithId(id);
            if(exists)
            {
                currentInverseIdentifier = new RelationshipReference(id, partitionID);
            }
        }

        RelationshipReference newReference = null;

        // Cascade Save. Make sure it is either ALL, or SAVE.  Also ensure that we haven't already saved it before
        if ((relationshipDescriptor.getCascadePolicy() == CascadePolicy.ALL
                || relationshipDescriptor.getCascadePolicy() == CascadePolicy.SAVE)
                && relationshipObject != null
                && !manager.contains(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier()))
        {

            RecordController inverseRecordController = getRecordControllerForEntity(relationshipObject);

            Object id = AbstractRecordController.getIndexValueFromEntity(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier());
            final long oldReference = (id != null) ? inverseRecordController.getReferenceId(id) : 0;

            newReference = new RelationshipReference(inverseRecordController.save(relationshipObject), getPartitionId(relationshipObject));

            // Make sure we put it on the saved list
            EntityRelationshipManager newManager = new EntityRelationshipManager();
            newManager.add(entity, entityDescriptor.getIdentifier());

            IndexHelper.saveAllIndexesForEntity(getContext(), getDescriptorForEntity(relationshipObject), newReference.identifier, oldReference, relationshipObject);
            RelationshipHelper.saveAllRelationshipsForEntity(relationshipObject, new EntityRelationshipManager(), getContext());
        }

        // Cascade Delete. Make sure it is either ALL, or DELETE.
        if ((relationshipDescriptor.getCascadePolicy() == CascadePolicy.DELETE
                || relationshipDescriptor.getCascadePolicy() == CascadePolicy.ALL))
        {
            RelationshipReference existingReference = null;

            if (newReference == null)
            {
                existingReference = toOneMap.get(entityIdentifier);
            }
            // No need to re-hydrate because we already have it above

            if (existingReference != null && (!existingReference.equals(currentInverseIdentifier) || (newReference != null && !existingReference.equals(newReference))))
            {
                final RecordController inverseRecordController = getRecordControllerForPartition(existingReference.partitionId);
                relationshipObject = inverseRecordController.getWithId(existingReference.identifier);

                if (relationshipObject != null && !manager.contains(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier()))
                {
                    IndexHelper.deleteAllIndexesForEntity(getContext(), getDescriptorForEntity(relationshipObject), inverseRecordController.getReferenceId(existingReference.identifier));
                    RelationshipHelper.deleteAllRelationshipsForEntity(relationshipObject, manager, getContext());
                    getRecordControllerForEntity(relationshipObject).deleteWithId(existingReference.identifier);
                }

                // Make sure we do not save the relationship again
                currentInverseIdentifier = null;

            }
        }
        // Ensure we delete the inverse
        else if (relationshipObject == null)
        {
            currentInverseIdentifier = toOneMap.get(entityIdentifier);

            if(currentInverseIdentifier != null)
            {
                deleteInverseRelationshipReference(entityIdentifier, currentInverseIdentifier);
                currentInverseIdentifier = null;
            }
        }

        RelationshipReference refToSave = (newReference != null) ? newReference : currentInverseIdentifier;

        // Save the inverse
        if (refToSave != null)
        {
            saveInverseRelationship(entity, relationshipObject, entityIdentifier, refToSave);
            toOneMap.put(entityIdentifier, refToSave);
        }
        else
        {
            if(toOneMap.containsKey(entityIdentifier))
            {
                toOneMap.remove(entityIdentifier);
            }
        }

    }

    /**
     * Delete Relationship entity
     *
     * @param entityIdentifier Entity relationship reference
     * @param manager Prevents recursion
     */
    public void deleteRelationshipForEntity(RelationshipReference entityIdentifier, EntityRelationshipManager manager) throws EntityException
    {

        final RelationshipReference inverseIdentifier = toOneMap.get(entityIdentifier);

        // Cascade delete, which will delete all relationships for the entity
        if (inverseIdentifier != null
                && (relationshipDescriptor.getCascadePolicy() == CascadePolicy.DELETE || relationshipDescriptor.getCascadePolicy() == CascadePolicy.ALL))
        {

            RecordController inverseRecordController = getRecordControllerForPartition(inverseIdentifier.partitionId);
            IManagedEntity relationshipObject = getRecordControllerForPartition(inverseIdentifier.partitionId).getWithId(inverseIdentifier.identifier);

            EntityDescriptor inverseDescriptor = getDescriptorForEntity(relationshipObject);
            if(!manager.contains(relationshipObject, inverseDescriptor.getIdentifier()))
            {
                manager.add(relationshipObject, inverseDescriptor.getIdentifier());

                IndexHelper.deleteAllIndexesForEntity(getContext(), getDescriptorForEntity(relationshipObject), inverseRecordController.getReferenceId(inverseIdentifier.identifier));
                RelationshipHelper.deleteAllRelationshipsForEntity(relationshipObject, manager, getContext());

                inverseRecordController.deleteWithId(inverseIdentifier.identifier);
            }
        }
        // Only delete the relationship Reference
        else if (inverseIdentifier != null)
        {
            this.deleteInverseRelationshipReference(entityIdentifier, inverseIdentifier);

            // Remove it from the data file
            toOneMap.remove(inverseIdentifier);
        }

    }

    /**
     * Determines whether the record controller for the relationship entity is a sequence or not
     *
     * @param identifier Relationship reference
     * @return Whether the record controller is an implementation of a Sequence
     */
    private boolean isSequenceIdentifier(RelationshipReference identifier) throws EntityException
    {
        return (getRecordControllerForPartition(identifier.partitionId) instanceof SequenceRecordControllerImpl);
    }

    /**
     * Hydrate a to one relationship
     *
     * @param entityIdentifier Relationship reference
     * @param entity Parent entity
     * @param manager Prevents recursion
     * @param force Force hydrate
     */
    @Override
    public void hydrateRelationshipForEntity(RelationshipReference entityIdentifier, IManagedEntity entity, EntityRelationshipManager manager, boolean force) throws EntityException
    {
        manager.add(entity, entityDescriptor.getIdentifier());

        // Get the Identifier
        final RelationshipReference inverseIdentifier = toOneMap.get(entityIdentifier);

        // If there are results, lets assign the key and recursively hydrate entities
        if (inverseIdentifier != null
               && (!isSequenceIdentifier(inverseIdentifier) || Long.valueOf(String.valueOf(inverseIdentifier.identifier)) > 0))
        {
            IManagedEntity relationshipObject = getRecordControllerForPartition(inverseIdentifier.partitionId).getWithId(inverseIdentifier.identifier);

            if (relationshipObject == null)
            {
                return;
//                throw new RelationshipHydrationException(relationshipDescriptor.getParentClass().getName(), relationshipDescriptor.getInverse(), inverseIdentifier.identifier);
            }

            EntityDescriptor inverseDescriptor = getDescriptorForEntity(relationshipObject);
            // If the manager contains, lets move on and dont hydrate recursively
            if (manager.contains(relationshipObject, inverseDescriptor.getIdentifier()))
            {
                setRelationshipValue(relationshipDescriptor, entity, manager.get(relationshipObject, inverseDescriptor.getIdentifier()));
                return;
            }
            else
            {
                setRelationshipValue(relationshipDescriptor, entity, relationshipObject);
            }

            RelationshipHelper.hydrateAllRelationshipsForEntity(relationshipObject, manager, getContext());
        }
        else
        {
            setRelationshipValue(relationshipDescriptor, entity, null);
        }

    }

    /**
     * Get Relationship Identifiers
     *
     * @param referenceId Relationship reference
     * @return List of relationship references
     */
    @Override
    public List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(Long referenceId) throws EntityException
    {
        List<RelationshipReference> results = new ArrayList<>();

        IManagedEntity entity = recordController.getWithReferenceId(referenceId);
        Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        // Get the identifier
        final RelationshipReference inverseIdentifier = toOneMap.get(new RelationshipReference(indexValue, 0));

        // Add it to results if it is not null and return them
        if (inverseIdentifier != null)
        {
            results.add(inverseIdentifier);
        }

        return results;
    }

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of relationship references
     */
    @Override
    public List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(PartitionReference referenceId) throws EntityException
    {
        List<RelationshipReference> results = new ArrayList<>();

        IManagedEntity entity = getRecordControllerForPartition(referenceId.partition).getWithReferenceId(referenceId.reference);
        Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        // Get the identifier
        final RelationshipReference inverseIdentifier = toOneMap.get(new RelationshipReference(indexValue, referenceId.partition));

        // Add it to results if it is not null and return them
        if (inverseIdentifier != null)
        {
            results.add(inverseIdentifier);
        }

        return results;
    }

    /**
     * Batch Save all relationship ids
     *
     * @param entity Entity to update
     * @param relationshipIdentifiers Relationship references
     */
    public void updateAll(IManagedEntity entity, Set<RelationshipReference> relationshipIdentifiers) throws EntityException
    {
    }

}
