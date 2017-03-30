package com.onyx.record.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.ValidationHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * This implementation of the record controller will create a new sequence if the id was not defined
 */
public class SequenceRecordControllerImpl extends AbstractRecordController implements RecordController {

    private static final int LAST_SEQUENCE_VALUE = 1;
    private final AtomicLong sequenceValue = new AtomicLong(0);
    private Map<Integer, Long> metadata = null;

    private static final int METADATA_MAP_LOAD_FACTOR = 1;

    /**
     * Constructor including the entity descriptor
     *
     * @param entityDescriptor Record Entity Descriptor
     * @param context Schema context
     */
    @SuppressWarnings("unchecked")
    public SequenceRecordControllerImpl(EntityDescriptor entityDescriptor, SchemaContext context)
    {
        super(entityDescriptor, context);

        MapBuilder dataFile = context.getDataFile(entityDescriptor);
        metadata = (DiskMap)dataFile.getHashMap("metadata" + entityDescriptor.getClazz().getName(), METADATA_MAP_LOAD_FACTOR);

        // Initialize the sequence key
        Long val = metadata.get(LAST_SEQUENCE_VALUE);
        if (val != null)
            sequenceValue.set(metadata.get(LAST_SEQUENCE_VALUE));
    }

    /**
     * Save an entity
     *
     * @param entity Entity to save
     * @return Entity identifier
     * @throws EntityException Error saving entity
     *
     * @since 1.2.3 Added an optimization so it does not do a check if it exist before
     *              putting if there are no listeners for persit
     */
    @Override
    public Object save(IManagedEntity entity) throws EntityException
    {

        Object retval = getIndexValueFromEntity(entity);
        Long val;
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

        if (this.entityDescriptor.getPreInsertCallback() != null || this.entityDescriptor.getPreUpdateCallback() != null) {
            records.compute(id, (o, current) -> {
                try {
                    if (current == null) {
                        isNew.set(true);
                        invokePreInsertCallback(entity);
                    } else {

                        long recordId = records.getRecID(retval);
                        if(recordId > 0L)
                        {
                            // Update Cached queries
                            context.getQueryCacheController().updateCachedQueryResultsForEntity(entity, this.entityDescriptor, recordId, true);
                        }
                        invokePreUpdateCallback(entity);
                    }
                } catch (EntityCallbackException ignore) {
                }
                return entity;
            });
        } else {
            long recordId = records.getRecID(retval);
            if(recordId > 0L)
            {
                // Update Cached queries
                context.getQueryCacheController().updateCachedQueryResultsForEntity(entity, this.entityDescriptor, recordId, true);
            }

            records.put(id, entity);
        }

        // Update Cached queries
        context.getQueryCacheController().updateCachedQueryResultsForEntity(entity, this.entityDescriptor, records.getRecID(id),false);

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
     * @param entity Entity to get index value of
     * @return identifier value for entity
     * @throws com.onyx.exception.AttributeMissingException Attribute does not exist on entity
     */
    @SuppressWarnings("WeakerAccess")
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
     * @param entity entity to check to see if it exists
     * @return Whether it alread exist in the record set
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
     * @param entity Entity to delete
     * @throws EntityException Error deleting entity
     */
    public void delete(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        final Object identifierValue = getIndexValueFromEntity(entity);

        // Update Cached queries
        long recordId = records.getRecID(identifierValue);
        if(recordId > -1)
        {
            invokePreRemoveCallback(entity);
            context.getQueryCacheController().updateCachedQueryResultsForEntity(entity, this.entityDescriptor, recordId,true);
            this.deleteWithId(identifierValue);
            invokePostRemoveCallback(entity);
        }

    }

    /**
     * Get an entity by the entity with populated primary key
     *
     * @param entity Entity to get.  A prereq is that it has its identifier defined
     * @return The entity if it exsts
     */
    public IManagedEntity get(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        Object identifierValue = getIndexValueFromEntity(entity);
        return getWithId(identifierValue);
    }

}
