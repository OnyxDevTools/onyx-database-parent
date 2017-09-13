package com.onyx.relationship.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.OnyxException;
import com.onyx.fetch.PartitionReference;
import com.onyx.helpers.IndexHelper;
import com.onyx.helpers.RelationshipHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.interactors.record.RecordInteractor;
import com.onyx.interactors.record.impl.DefaultRecordInteractor;
import com.onyx.interactors.record.impl.SequenceRecordInteractor;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.relationship.RelationshipInteractor;
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
public class ToOneRelationshipControllerImpl extends AbstractRelationshipController implements RelationshipInteractor {

    private DiskMap<RelationshipReference, RelationshipReference> toOneMap = null;

    /**
     * Constructor
     *
     * @param entityDescriptor Entity descriptor
     * @param relationshipDescriptor relationship descriptor
     */
    public ToOneRelationshipControllerImpl(EntityDescriptor entityDescriptor, RelationshipDescriptor relationshipDescriptor, SchemaContext context) throws OnyxException
    {
        super(entityDescriptor, relationshipDescriptor, context);

        MapBuilder mapBuilder = context.getDataFile(entityDescriptor);
        // Get the correct data file
        toOneMap = (DiskMap)mapBuilder.getHashMap(entityDescriptor.getEntityClass().getName() + relationshipDescriptor.getName(), RELATIONSHIP_MAP_LOAD_FACTOR);
    }


    /**
     * Save Relationship for entity
     *
     * @param entity Entity to save relationships for
     * @param manager Prevents recursion
     */
    public void saveRelationshipForEntity(IManagedEntity entity, EntityRelationshipManager manager) throws OnyxException
    {
        IManagedEntity relationshipObject = getRelationshipValue(entity);

        RelationshipReference currentInverseIdentifier = null;
        final RelationshipReference entityIdentifier = new RelationshipReference(DefaultRecordInteractor.Companion.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier()), getPartitionId(entity));

        // Hydrate the inverse identifier if it exists
        if (relationshipObject != null && currentInverseIdentifier == null)
        {
            // Get Inverse Partition ID
            long partitionID = getPartitionId(relationshipObject);
            Object id = DefaultRecordInteractor.Companion.getIndexValueFromEntity(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier());
            boolean exists = getRecordInteractorForEntity(relationshipObject).existsWithId(id);
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
                && !manager.contains(relationshipObject, getContext()))
        {

            RecordInteractor inverseRecordInteractor = getRecordInteractorForEntity(relationshipObject);

            Object id = DefaultRecordInteractor.Companion.getIndexValueFromEntity(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier());
            final long oldReference = (id != null) ? inverseRecordInteractor.getReferenceId(id) : 0;

            newReference = new RelationshipReference(inverseRecordInteractor.save(relationshipObject), getPartitionId(relationshipObject));

            // Make sure we put it on the saved list
            EntityRelationshipManager newManager = new EntityRelationshipManager();
            newManager.add(entity, getContext());

            IndexHelper.saveAllIndexesForEntity(getContext(), getDescriptorForEntity(relationshipObject), newReference.getIdentifier(), oldReference, relationshipObject);
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
                final RecordInteractor inverseRecordInteractor = getRecordInteractorForPartition(existingReference.getPartitionId());
                relationshipObject = inverseRecordInteractor.getWithId(existingReference.getIdentifier());

                if (relationshipObject != null && !manager.contains(relationshipObject, getContext()))
                {
                    IndexHelper.deleteAllIndexesForEntity(getContext(), getDescriptorForEntity(relationshipObject), inverseRecordInteractor.getReferenceId(existingReference.getIdentifier()));
                    RelationshipHelper.deleteAllRelationshipsForEntity(relationshipObject, manager, getContext());
                    getRecordInteractorForEntity(relationshipObject).deleteWithId(existingReference.getIdentifier());
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
        else
        {
            RelationshipReference previousRelationshipReference = toOneMap.get(entityIdentifier);

            if(previousRelationshipReference != null && !previousRelationshipReference.equals(currentInverseIdentifier))
            {
                deleteInverseRelationshipReference(entityIdentifier, previousRelationshipReference);
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
    public void deleteRelationshipForEntity(RelationshipReference entityIdentifier, EntityRelationshipManager manager) throws OnyxException
    {

        final RelationshipReference inverseIdentifier = toOneMap.get(entityIdentifier);

        // Cascade delete, which will delete all relationships for the entity
        if (inverseIdentifier != null
                && (relationshipDescriptor.getCascadePolicy() == CascadePolicy.DELETE || relationshipDescriptor.getCascadePolicy() == CascadePolicy.ALL))
        {

            RecordInteractor inverseRecordInteractor = getRecordInteractorForPartition(inverseIdentifier.getPartitionId());
            IManagedEntity relationshipObject = getRecordInteractorForPartition(inverseIdentifier.getPartitionId()).getWithId(inverseIdentifier.getIdentifier());
            if(relationshipObject == null)
                return;

            EntityDescriptor inverseDescriptor = getDescriptorForEntity(relationshipObject);
            if(!manager.contains(relationshipObject, getContext()))
            {
                manager.add(relationshipObject, getContext());

                IndexHelper.deleteAllIndexesForEntity(getContext(), getDescriptorForEntity(relationshipObject), inverseRecordInteractor.getReferenceId(inverseIdentifier.getIdentifier()));
                RelationshipHelper.deleteAllRelationshipsForEntity(relationshipObject, manager, getContext());

                inverseRecordInteractor.deleteWithId(inverseIdentifier.getIdentifier());
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
    private boolean isSequenceIdentifier(RelationshipReference identifier) throws OnyxException
    {
        return (getRecordInteractorForPartition(identifier.getPartitionId()) instanceof SequenceRecordInteractor);
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
    public void hydrateRelationshipForEntity(RelationshipReference entityIdentifier, IManagedEntity entity, EntityRelationshipManager manager, boolean force) throws OnyxException
    {
        manager.add(entity, getContext());

        // Get the Identifier
        final RelationshipReference inverseIdentifier = toOneMap.get(entityIdentifier);

        // If there are results, lets assign the key and recursively hydrate entities
        if (inverseIdentifier != null
               && (!isSequenceIdentifier(inverseIdentifier) || Long.valueOf(String.valueOf(inverseIdentifier.getIdentifier())) > 0))
        {
            IManagedEntity relationshipObject = getRecordInteractorForPartition(inverseIdentifier.getPartitionId()).getWithId(inverseIdentifier.getIdentifier());

            if (relationshipObject == null)
            {
                return;
//                throw new RelationshipHydrationException(relationshipDescriptor.getParentClass().getName(), relationshipDescriptor.getInverse(), inverseIdentifier.identifier);
            }

            EntityDescriptor inverseDescriptor = getDescriptorForEntity(relationshipObject);
            // If the manager contains, lets move on and dont hydrate recursively
            if (manager.contains(relationshipObject, getContext()))
            {
                setRelationshipValue(relationshipDescriptor, entity, manager.get(relationshipObject, getContext()));
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
    public List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(Long referenceId) throws OnyxException
    {
        List<RelationshipReference> results = new ArrayList<>();

        IManagedEntity entity = recordInteractor.getWithReferenceId(referenceId);
        Object indexValue = DefaultRecordInteractor.Companion.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

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
    public List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(PartitionReference referenceId) throws OnyxException
    {
        List<RelationshipReference> results = new ArrayList<>();

        IManagedEntity entity = getRecordInteractorForPartition(referenceId.partition).getWithReferenceId(referenceId.reference);
        Object indexValue = DefaultRecordInteractor.Companion.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

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
    public void updateAll(IManagedEntity entity, Set<RelationshipReference> relationshipIdentifiers) throws OnyxException
    {
    }

}
