package com.onyx.relationship;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.record.AbstractRecordController;

import java.util.HashMap;
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
    private Map<String, Map<Object, IManagedEntity>> entities = new HashMap();

    /**
     *
     * Checks to see whether it exists in the entities map
     * @param entity
     * @param indexDescriptor
     * @return
     * @throws AttributeMissingException
     */
    public boolean contains(IManagedEntity entity, IndexDescriptor indexDescriptor) throws AttributeMissingException
    {
        final String className = entity.getClass().getName();

        if(!entities.containsKey(className))
        {
            entities.put(className, new HashMap());
        }

        final Map<Object, IManagedEntity> entityMap = entities.get(className);
        final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, indexDescriptor);

        if(indexValue == null)
        {
            return false;
        }
        return entityMap.containsKey(indexValue);
    }

    /**
     * Adds a new value to the 2 dimensional map
     *
     * @param entity
     * @param indexDescriptor
     * @throws AttributeMissingException
     */
    public void add(IManagedEntity entity, IndexDescriptor indexDescriptor) throws AttributeMissingException
    {
        final String className = entity.getClass().getName();

        if(!entities.containsKey(className))
        {
            entities.put(className, new HashMap());
        }

        final Map<Object, IManagedEntity> entityMap = entities.get(className);

        entityMap.put(AbstractRecordController.getIndexValueFromEntity(entity, indexDescriptor), entity);
    }

    /**
     * Gets the element, from the 2 dimensional map
     *
     * @param entity
     * @return
     */
    public IManagedEntity get(IManagedEntity entity, IndexDescriptor descriptor) throws AttributeMissingException
    {
        final String className = entity.getClass().getName();

        if(!entities.containsKey(className))
        {
            entities.put(className, new HashMap());
        }

        final Map<Object, IManagedEntity> entityMap = entities.get(className);
        final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, descriptor);

        return entityMap.get(indexValue);
    }
}
