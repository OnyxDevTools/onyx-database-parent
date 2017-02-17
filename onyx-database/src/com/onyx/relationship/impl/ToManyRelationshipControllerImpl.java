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
import com.onyx.structure.DiskMap;
import com.onyx.structure.MapBuilder;

import java.util.*;

/**
 * Created by timothy.osborn on 2/5/15.
 */
public class ToManyRelationshipControllerImpl extends AbstractRelationshipController implements RelationshipController
{

    protected DiskMap<Object, Set<RelationshipReference>> records;

    /**
     * To Many Relationship Controller Constructor
     *
     * @param entityDescriptor
     * @param relationshipDescriptor
     * @throws EntityException
     */
    public ToManyRelationshipControllerImpl(EntityDescriptor entityDescriptor, RelationshipDescriptor relationshipDescriptor, SchemaContext context) throws EntityException
    {
        super(entityDescriptor, relationshipDescriptor, context);
        MapBuilder mapBuilder = context.getDataFile(entityDescriptor);

        records = (DiskMap) mapBuilder.getSkipListMap(entityDescriptor.getClazz().getName() + relationshipDescriptor.getName());
    }

    /**
     *
     * @param entity
     * @param manager
     * @throws EntityException
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
        RelationshipReference relationshipObjectIdentifier = null;

        boolean saveRelationship = false;
        Object reltnIdentifier = null;

        if (relationshipObjects != null)
        {
            relationshipObjectCopy = new HashSet(existingRelationshipObjects);

            saveRelationship = false;

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

                    IndexHelper.saveAllIndexesForEntity(context, getDescriptorForEntity(relationshipObject), relationshipObjectIdentifier.identifier, oldReference, relationshipObject);
                    RelationshipHelper.saveAllRelationshipsForEntity(relationshipObject, newManager, context);

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
     * @param entityIdentifier
     * @param manager
     * @throws EntityException
     */
    @Override
    public void deleteRelationshipForEntity(RelationshipReference entityIdentifier, EntityRelationshipManager manager) throws EntityException
    {
        IManagedEntity entity = recordController.getWithId(entityIdentifier.identifier);
        manager.add(entity, entityDescriptor.getIdentifier());

        Set<RelationshipReference> existingRelationshipObjects = null;
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
     * @param entityIdentifier
     * @param entity
     * @param manager
     * @param force
     * @throws EntityException
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
            relationshipObjects = new LazyRelationshipCollection(defaultDescriptor, existingRelationshipObjects, context);
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

        RelationshipReference inverseIdentifier = null;

        if (existingRelationshipObjects != null
                && (relationshipDescriptor.getFetchPolicy() != FetchPolicy.LAZY || force == true))
        {
            Iterator<RelationshipReference> keyIterator = existingRelationshipObjects.iterator();

            while (keyIterator.hasNext())
            {
                inverseIdentifier = keyIterator.next();
                IManagedEntity relationshipObject = getRecordControllerForPartition(inverseIdentifier.partitionId).getWithId(inverseIdentifier.identifier);

                if (relationshipObject == null)
                {
                    throw new RelationshipHydrationException(relationshipDescriptor.getParentClass().getName(), relationshipDescriptor.getInverse(), inverseIdentifier.identifier);
                }

                if (!manager.contains(relationshipObject, getDescriptorForEntity(relationshipObject).getIdentifier()))
                {
                    RelationshipHelper.hydrateAllRelationshipsForEntity(relationshipObject, manager, context);
                }
                relationshipObjects.add(relationshipObject);

                //sort related children if the child entity implements Comparable
                if (relationshipObjects.size() > 0 && Comparable.class.isAssignableFrom(relationshipObjects.get(0).getClass()))
                {
                    relationshipObjects.sort(null);
                }

            }
        }

    }

    /**
     * Get Relationship Identifiers
     *
     * @param referenceId
     * @return
     * @throws EntityException
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
     * @return
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
     * @param entity
     * @param relationshipIdentifiers
     */
    public void updateAll(IManagedEntity entity, Set<RelationshipReference> relationshipIdentifiers) throws EntityException
    {
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, this.context);
        Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        RelationshipReference entityId = null;
        if(partitionValue != "" && partitionValue != null) {
            SystemPartitionEntry relationshipDescriptor = this.context.getPartitionWithValue(entityDescriptor.getClazz(), PartitionHelper.getPartitionFieldValue(entity, this.context));
            entityId = new RelationshipReference(indexValue, relationshipDescriptor.getIndex());
        } else {
            entityId = new RelationshipReference(indexValue, 0L);
        }
        records.put(entityId, relationshipIdentifiers);
    }

}
