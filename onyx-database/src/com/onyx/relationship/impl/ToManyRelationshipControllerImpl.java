package com.onyx.relationship.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.RelationshipHydrationException;
import com.onyx.fetch.PartitionReference;
import com.onyx.helpers.IndexHelper;
import com.onyx.helpers.PartitionHelper;
import com.onyx.helpers.RelationshipHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.collections.LazyRelationshipCollection;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;

import java.util.*;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Handles the to many relationship persistence
 */
@SuppressWarnings("unchecked")
public class ToManyRelationshipControllerImpl extends AbstractRelationshipController implements RelationshipController
{

    protected final DiskMap<Object, Set<RelationshipReference>> records;

    /**
     * To Many Relationship Controller Constructor
     *
     * @param entityDescriptor Parent entity descriptor
     * @param relationshipDescriptor Child entity descriptor
     */
    public ToManyRelationshipControllerImpl(EntityDescriptor entityDescriptor, RelationshipDescriptor relationshipDescriptor, SchemaContext context) throws EntityException
    {
        super(entityDescriptor, relationshipDescriptor, context);
        MapBuilder mapBuilder = context.getDataFile(entityDescriptor);

        records = (DiskMap) mapBuilder.getHashMap(entityDescriptor.getClazz().getName() + relationshipDescriptor.getName(), RELATIONSHIP_MAP_LOAD_FACTOR);
    }

    /**
     *
     * @param entity Entity to save relationship
     * @param manager Relationship manager keeps track of actions already taken on entity relationships
     */
    @Override
    public void saveRelationshipForEntity(IManagedEntity entity, EntityRelationshipManager manager) throws EntityException
    {
        if (relationshipDescriptor.getCascadePolicy() == CascadePolicy.DEFER_SAVE)
        {
            return;
        }

        final List<IManagedEntity> relationshipObjects = getRelationshipListValue(relationshipDescriptor, entity);
        final RelationshipReference entityIdentifier = new RelationshipReference(AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier()), getPartitionId(entity));

        Set<RelationshipReference> existingRelationshipObjects = null;

        // If the structure does not exist, lets create one and persist it
        synchronized (records)
        {
            Object retVal = records.get(entityIdentifier);
            if (retVal instanceof Set)
                existingRelationshipObjects = (Set) retVal;
            // This is to account for a schema change from a to one to a to many
            else if (retVal != null)
            {
                existingRelationshipObjects = new HashSet<>();
                existingRelationshipObjects.add((RelationshipReference) retVal);
            }
            else if (existingRelationshipObjects == null)
            {
                existingRelationshipObjects = new HashSet<>();
                // Commit the relationship record
                records.put(entityIdentifier, existingRelationshipObjects);
            }
        }

        Set<RelationshipReference> relationshipObjectCopy = new HashSet<>();
        RelationshipReference relationshipObjectIdentifier ;

        boolean saveRelationship;
        Object reltnIdentifier;

        if (relationshipObjects != null)
        {
            relationshipObjectCopy = new HashSet(existingRelationshipObjects);

            // Iterate through and save the relationship
            for (IManagedEntity relationshipObject : relationshipObjects)
            {
                // Get the inverse identifier
                reltnIdentifier = AbstractRecordController.getIndexValueFromEntity(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier());
                relationshipObjectIdentifier = new RelationshipReference(reltnIdentifier, getPartitionId(relationshipObject));

                // If it is in the list, it is accounted for, lets continue
                relationshipObjectCopy.remove(relationshipObjectIdentifier);

                // Cascade save the entity
                if ((relationshipDescriptor.getCascadePolicy() == CascadePolicy.ALL || relationshipDescriptor.getCascadePolicy() == CascadePolicy.SAVE)
                        && !manager.contains(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier()))
                {

                    // The EntityRelationshipManager ensures we do not recursively save cascading objects
                    EntityRelationshipManager newManager = new EntityRelationshipManager();
                    newManager.add(entity, entityDescriptor.getIdentifier());

                    final RecordController inverseRecordController = getRecordControllerForEntity(relationshipObject);

                    Object id = AbstractRecordController.getIndexValueFromEntity(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier());
                    final long oldReference = (id != null) ? inverseRecordController.getReferenceId(id) : 0;

                    relationshipObjectIdentifier = new RelationshipReference(inverseRecordController.save(relationshipObject), getPartitionId(relationshipObject));

                    IndexHelper.saveAllIndexesForEntity(getContext(), getDescriptorForEntity(relationshipObject), relationshipObjectIdentifier.identifier, oldReference, relationshipObject);
                    RelationshipHelper.saveAllRelationshipsForEntity(relationshipObject, newManager, getContext());

                    // The record exists
                    saveRelationship = true;

                }
                else
                {
                    // Ensure the record exists
                    saveRelationship = getRecordControllerForPartition(relationshipObjectIdentifier.partitionId).existsWithId(relationshipObjectIdentifier.identifier);
                }

                // The entity exists yay, that means we can save it
                if (saveRelationship)
                {
                    existingRelationshipObjects.add(relationshipObjectIdentifier);
                }

                // Save the inverse relationship
                if (!manager.contains(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier()))
                {
                    if (relationshipDescriptor.getInverse() != null && relationshipDescriptor.getInverse().length() > 0)
                    {
                        saveInverseRelationship(entity, relationshipObject, entityIdentifier, relationshipObjectIdentifier);
                    }
                }
            }
        }

        // Go through and delete the cascaded objects
        if ((relationshipDescriptor.getCascadePolicy() == CascadePolicy.DELETE
                || relationshipDescriptor.getCascadePolicy() == CascadePolicy.ALL)) // 5/7/2015 Prevent lazy relationships from cascading.  It must be initialized
        {
            for (RelationshipReference relationshipKeyToDelete : relationshipObjectCopy)
            {

                // Delete the actual relationship
                existingRelationshipObjects.remove(relationshipKeyToDelete);

                // Delete the inverse
                deleteInverseRelationshipReference(entityIdentifier, relationshipKeyToDelete);
            }
        }

        synchronized (records)
        {
            records.put(entityIdentifier, existingRelationshipObjects);
        }
    }

    /**
     * Deletes the entire relationship list for a to many relationship
     *
     * @param entityIdentifier Relationship reference key
     * @param manager Relationship manager keeps track of actions already taken on entity relationships
     */
    @Override
    public void deleteRelationshipForEntity(RelationshipReference entityIdentifier, EntityRelationshipManager manager) throws EntityException
    {
        IManagedEntity entity = recordController.getWithId(entityIdentifier.identifier);
        manager.add(entity, entityDescriptor.getIdentifier());

        Set<RelationshipReference> existingRelationshipObjects;
        synchronized (records)
        {
            Object retVal = records.get(entityIdentifier);
            if (retVal instanceof Set)
                existingRelationshipObjects = (Set) retVal;
            else
            {
                existingRelationshipObjects = new HashSet<>();
                existingRelationshipObjects.add((RelationshipReference) retVal);
            }

            for (RelationshipReference inverseIdentifier : existingRelationshipObjects)
            {
                deleteInverseRelationshipReference(entityIdentifier, inverseIdentifier);
            }
        }

        synchronized (records)
        {
            records.remove(entityIdentifier);
        }
    }

    /**
     * Hydrate relationship for entity
     *
     * @param entityIdentifier Entity relationship id
     * @param entity Entity to hydrate
     * @param manager Relationship manager prevents recursion
     * @param force Force hydrate
     */
    @Override
    public void hydrateRelationshipForEntity(RelationshipReference entityIdentifier, IManagedEntity entity, EntityRelationshipManager manager, boolean force) throws EntityException
    {
        manager.add(entity, entityDescriptor.getIdentifier());

        Set<RelationshipReference> existingRelationshipObjects = null;

        Object retVal = records.get(entityIdentifier);
        if (retVal instanceof Set)
            existingRelationshipObjects = (Set) retVal;
        // This is to account for a schema change from a to one to a to many
        else if (retVal != null)
        {
            existingRelationshipObjects = new HashSet<>();
            existingRelationshipObjects.add((RelationshipReference) retVal);
        }

        List<IManagedEntity> relationshipObjects = getRelationshipListValue(relationshipDescriptor, entity);

        if (relationshipDescriptor.getFetchPolicy() == FetchPolicy.LAZY && !force)
        {
            relationshipObjects = new LazyRelationshipCollection(defaultDescriptor, existingRelationshipObjects, getContext());
        }
        else if (relationshipObjects == null && !(relationshipObjects instanceof LazyRelationshipCollection))
        {
            relationshipObjects = new ArrayList<>();
        }
        else if (force && relationshipObjects instanceof LazyRelationshipCollection)
        {
            relationshipObjects = new ArrayList<>();
        }
        else
        {
            relationshipObjects.clear();
        }

        setRelationshipValue(relationshipDescriptor, entity, relationshipObjects);

        RelationshipReference inverseIdentifier;

        if (existingRelationshipObjects != null
                && (relationshipDescriptor.getFetchPolicy() != FetchPolicy.LAZY || force))
        {

            for (RelationshipReference existingRelationshipObject : existingRelationshipObjects) {
                inverseIdentifier = existingRelationshipObject;
                IManagedEntity relationshipObject = getRecordControllerForPartition(inverseIdentifier.partitionId).getWithId(inverseIdentifier.identifier);

                if (relationshipObject == null) {
                    throw new RelationshipHydrationException(relationshipDescriptor.getParentClass().getName(), relationshipDescriptor.getInverse(), inverseIdentifier.identifier);
                }

                if (!manager.contains(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier())) {
                    RelationshipHelper.hydrateAllRelationshipsForEntity(relationshipObject, manager, getContext());
                }
                relationshipObjects.add(relationshipObject);

                //sort related children if the child entity implements Comparable
                if (relationshipObjects.size() > 0 && Comparable.class.isAssignableFrom(relationshipObjects.get(0).getClass())) {
                    relationshipObjects.sort(null);
                }

            }
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
        IManagedEntity entity = recordController.getWithReferenceId(referenceId);
        Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());
        Set<RelationshipReference> existingRelationshipObjects = null;
        Object retVal = records.get(new RelationshipReference(indexValue, 0));
        if (retVal instanceof Set)
            existingRelationshipObjects = (Set) retVal;
        // This is to account for a schema change from a to one to a to many
        else if (retVal != null)
        {
            existingRelationshipObjects = new HashSet<>();
            existingRelationshipObjects.add((RelationshipReference) retVal);
        }

        if (existingRelationshipObjects != null)
        {
            return new ArrayList<>(existingRelationshipObjects);
        }
        return new ArrayList<>();
    }

    /**
     * Retrieves the identifiers for a given entity
     *
     * @return List of relationship references
     */
    @Override
    public List<RelationshipReference> getRelationshipIdentifiersWithReferenceId(PartitionReference referenceId) throws EntityException
    {
        IManagedEntity entity = getRecordControllerForPartition(referenceId.partition).getWithReferenceId(referenceId.reference);
        Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());
        Set<RelationshipReference> existingRelationshipObjects = null;

        Object retVal = records.get(new RelationshipReference(indexValue, referenceId.partition));
        if (retVal instanceof Set)
            existingRelationshipObjects = (Set) retVal;
        // This is to account for a schema change from a to one to a to many
        else if (retVal != null)
        {
            existingRelationshipObjects = new HashSet<>();
            existingRelationshipObjects.add((RelationshipReference) retVal);
        }

        if (existingRelationshipObjects != null)
        {
            return new ArrayList<>(existingRelationshipObjects);
        }
        return new ArrayList<>();
    }

    /**
     * Batch Save all relationship ids
     *
     * @param entity entity to update
     * @param relationshipIdentifiers Relationship references
     */
    public void updateAll(IManagedEntity entity, Set<RelationshipReference> relationshipIdentifiers) throws EntityException
    {
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, this.getContext());
        Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        RelationshipReference entityId;
        if(partitionValue != "" && partitionValue != null) {
            SystemPartitionEntry relationshipDescriptor = this.getContext().getPartitionWithValue(entityDescriptor.getClazz(), PartitionHelper.getPartitionFieldValue(entity, this.getContext()));
            entityId = new RelationshipReference(indexValue, relationshipDescriptor.getIndex());
        } else {
            entityId = new RelationshipReference(indexValue, 0L);
        }
        records.put(entityId, relationshipIdentifiers);
    }

}
