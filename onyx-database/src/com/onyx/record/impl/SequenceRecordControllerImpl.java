package com.onyx.record.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.ValidationHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.structure.DiskMap;
import com.onyx.structure.MapBuilder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 2/5/15.
 */
public class SequenceRecordControllerImpl extends AbstractRecordController implements RecordController {

    protected static final int LAST_SEQUENCE_VALUE = 1;

    protected AtomicLong sequenceValue = new AtomicLong(0);

    protected Map<Integer, Long> metadata = null;

    /**
     * Constructor including the entity descriptor
     *
     * @param entityDescriptor
     */
    public SequenceRecordControllerImpl(EntityDescriptor entityDescriptor, SchemaContext context)
    {
        super(entityDescriptor, context);

        MapBuilder dataFile = context.getDataFile(entityDescriptor);
        metadata = (DiskMap)dataFile.getSkipListMap("metadata" + entityDescriptor.getClazz().getName());

        // Initialize the sequence key
        Long val = metadata.get(LAST_SEQUENCE_VALUE);
        if (val != null)
            sequenceValue.set(metadata.get(LAST_SEQUENCE_VALUE));
    }

    /**
     * Save an entity
     *
     * @param entity
     * @return
     * @throws EntityException
     */
    @Override
    public Object save(IManagedEntity entity) throws EntityException
    {

        Object retval = getIndexValueFromEntity(entity);
        Long val = 0l;
        if(retval instanceof Integer || retval.getClass() == int.class)
        {
            val = ((Integer)retval).longValue();
        }
        else
        {
            val = (long)retval;
        }

        ValidationHelper.validateEntity(entityDescriptor, entity); // Validate the entity, this wil throw an exception if not valid so no need to continue

        invokePrePersistCallback(entity); // Invoke Pre persist callback for entity

        final AtomicBoolean isNew = new AtomicBoolean(false); // Keeps track of whether the record is new or not

        // Assign a new index key, synchronize so that the index size gets persisted in order and aren't fighting over threads
        synchronized (sequenceValue)
        {
            if (val == null || val == 0)
            {
                val = sequenceValue.incrementAndGet();
                setIndexValueForEntity(entity, val);
                metadata.put(LAST_SEQUENCE_VALUE, val);
            } else if (val > sequenceValue.get())
            {
                sequenceValue.set(val);
                metadata.put(LAST_SEQUENCE_VALUE, val);
            }
        }

        Object id = val;
        if(this.entityDescriptor.getIdentifier().getType() == Integer.class || this.entityDescriptor.getIdentifier().getType() == int.class)
        {
            id = val.intValue();
        }

        records.compute(id, (o, current) -> {
            try
            {
                if (current == null)
                {
                    isNew.set(true);
                    invokePreInsertCallback(entity);
                } else
                {
                    invokePreUpdateCallback(entity);
                }
            } catch (EntityCallbackException e)
            {
            }
            return entity;
        });

        invokePostPersistCallback(entity); // Always invoke Post persist callback

        // Invoke Post insert or update callback
        if (isNew.get())
        {
            invokePostInsertCallback(entity);
        } else
        {
            invokePostUpdateCallback(entity);
        }

        // Return the id
        return id;
    }

    /**
     * Overridden in order to ensure it is a long
     *
     * @param entity
     * @return
     * @throws com.onyx.exception.AttributeMissingException
     */
    protected Object getIndexValueFromEntity(IManagedEntity entity) throws AttributeMissingException
    {
        Object indexObjectValue = AbstractRecordController.getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());
        if(indexObjectValue  == null)
            indexObjectValue = 0;
        return indexObjectValue;
    }

    /**
     * Returns true if the record exists in database
     *
     * @param entity
     * @return
     */
    public boolean exists(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        final Object identifierValue = getIndexValueFromEntity(entity);

        return records.containsKey(identifierValue);
    }

    /**
     * Delete
     *
     * @param entity
     * @throws EntityException
     */
    public void delete(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        final Object identifierValue = getIndexValueFromEntity(entity);

        invokePreRemoveCallback(entity);

        this.deleteWithId(identifierValue);

        invokePostRemoveCallback(entity);
    }

    /**
     * Get an entity by the entity with populated primary key
     *
     * @param entity
     * @return
     */
    public IManagedEntity get(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        Object identifierValue = getIndexValueFromEntity(entity);
        return getWithId(identifierValue);
    }

}
