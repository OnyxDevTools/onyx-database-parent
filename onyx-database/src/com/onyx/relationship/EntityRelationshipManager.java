package com.onyx.relationship;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.util.map.CompatHashMap;
import com.onyx.exception.AttributeMissingException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.record.AbstractRecordController;
import com.onyx.util.map.CompatMap;

import java.util.Map;

/**
 * Created by timothy.osborn on 12/23/14.
 *
 * The purpose of this class is to retain a reference to all of the elements that you are transacting
 *
 * There are two usages for this.
 *
 * 1) When saving you can fetch the same reference for an inverse relationship and apply it
 * 2) When fetching recursive elements you do not have to re-fetch an element you have already fetched
 */
public class EntityRelationshipManager
{
    private final CompatMap<String, CompatMap<Object, IManagedEntity>> entities = new CompatHashMap<>();

    /**
     *
     * Checks to see whether it exists in the entities structure
     * @param entity Entity to check to see if action has already been taken
     * @param indexDescriptor Entity's index descriptor
     * @throws AttributeMissingException Attribute does not exist
     * @return Whether the entity is already there
     */
    public boolean contains(IManagedEntity entity, IndexDescriptor indexDescriptor) throws AttributeMissingException {
        final String className = entity.getClass().getName();

        if (!entities.containsKey(className)) {
            entities.put(className, new CompatHashMap<>());
        }

        final Map<Object, IManagedEntity> entityMap = entities.get(className);
        final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, indexDescriptor);

        return indexValue != null && entityMap.containsKey(indexValue);
    }

    /**
     * Adds a new key to the 2 dimensional structure
     *
     * @param entity Entity to add to controller
     * @param indexDescriptor Entity's index controller
     * @throws AttributeMissingException Attribute does not exist
     */
    public void add(IManagedEntity entity, IndexDescriptor indexDescriptor) throws AttributeMissingException
    {
        final String className = entity.getClass().getName();

        if(!entities.containsKey(className))
        {
            entities.put(className, new CompatHashMap<>());
        }

        final Map<Object, IManagedEntity> entityMap = entities.get(className);

        entityMap.put(AbstractRecordController.getIndexValueFromEntity(entity, indexDescriptor), entity);
    }

    /**
     * Gets the element, from the 2 dimensional structure
     *
     * @param entity Entity to check
     * @return Entity reference
     */
    public IManagedEntity get(IManagedEntity entity, IndexDescriptor descriptor) throws AttributeMissingException
    {
        final String className = entity.getClass().getName();

        if(!entities.containsKey(className))
        {
            entities.put(className, new CompatHashMap<>());
        }

        final Map<Object, IManagedEntity> entityMap = entities.get(className);
        final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, descriptor);

        return entityMap.get(indexValue);
    }
}
