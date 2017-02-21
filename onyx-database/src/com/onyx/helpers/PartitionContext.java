package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.EntityExceptionWrapper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.record.RecordController;
import com.onyx.diskmap.MapBuilder;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by timothy.osborn on 3/19/15.
 *
 * The purpose of this class is to act as a helper to cache the partition information regarding an entity.
 * It is to make it more efficient
 *
 */
public class PartitionContext
{

    protected String contextId = null;
    protected RecordController defaultRecordController = null;
    protected EntityDescriptor defaultDescriptor = null;

    /**
     * Constructor
     *
     * @param context Schema Context
     * @param descriptor Entity Descriptor
     */
    public PartitionContext(SchemaContext context, EntityDescriptor descriptor)
    {
        this.contextId = context.getContextId();
        this.defaultDescriptor = descriptor;
        this.defaultRecordController = context.getRecordController(this.defaultDescriptor);
    }

    /**
     * Cached Partition Files
     */
    private Map<Long, MapBuilder> cachedPartitionFiles = Collections.synchronizedMap(new WeakHashMap<Long, MapBuilder>());

    /**
     * Cached Data Files for partition ids, return default if there is no partition.  Otherwise insert into cache.
     *
     */
    protected MapBuilder getDataFileWithPartitionId(long partitionId) throws EntityException
    {
        if(partitionId == 0)
        {
            return this.getContext().getDataFile(this.defaultDescriptor);
        }

        final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

        MapBuilder retVal = cachedPartitionFiles.compute(partitionId, (aLong, db) -> {
            if(db == null)
            {
                try
                {
                    return getContext().getPartitionDataFile(defaultDescriptor, partitionId);
                } catch (EntityException e)
                {
                    exceptionWrapper.exception = e;
                }
            }
            return db;
        });

        if(exceptionWrapper.exception != null)
        {
            throw exceptionWrapper.exception;
        }

        return retVal;
    }

    /**
     * Cached Partition Files
     */
    private class PartitionKey
    {

        PartitionKey(IManagedEntity entity) throws EntityException
        {
            this.entityType = entity.getClass();
            this.partitionVal = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, getContext()));
        }

        @Override
        public int hashCode()
        {
            return this.entityType.getName().hashCode() + this.partitionVal.hashCode();
        }

        public boolean equals(Object val)
        {
            if(val instanceof PartitionKey)
            {
                final PartitionKey key = (PartitionKey) val;
                return (key.entityType == this.entityType && key.partitionVal.equals(partitionVal));
            }
            return false;
        }

        Class entityType;
        String partitionVal;
    }

    private Map<PartitionKey, EntityDescriptor> cachedDescriptorsPerEntity = Collections.synchronizedMap(new WeakHashMap<PartitionKey, EntityDescriptor>());

    public EntityDescriptor getDescriptorForEntity(IManagedEntity entity) throws EntityException
    {
        if(PartitionHelper.hasPartitionField(entity, getContext()))
        {

            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            EntityDescriptor retVal = cachedDescriptorsPerEntity.compute(new PartitionKey(entity), (partitionKey, descriptor) -> {
                if(descriptor == null)
                {
                    try
                    {
                        return getContext().getDescriptorForEntity(entity);
                    }
                    catch (EntityException e)
                    {
                        exceptionWrapper.exception = e;
                    }
                }
                return descriptor;
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultDescriptor;
    }



    private Map<Long, EntityDescriptor> cachedDescriptorsPerPartition = Collections.synchronizedMap(new WeakHashMap<Long, EntityDescriptor>());

    protected EntityDescriptor getDescriptorWithPartitionId(long partitionId) throws EntityException
    {
        if(partitionId != 0)
        {

            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            EntityDescriptor retVal = cachedDescriptorsPerPartition.compute(partitionId, (partitionKey, descriptor) -> {
                if(descriptor == null)
                {
                    try
                    {
                        SystemPartitionEntry partitionEntry = getContext().getPartitionWithId(defaultDescriptor.getClazz(), partitionId);
                        if(partitionEntry == null)
                        {
                            return defaultDescriptor;
                        }

                        return getContext().getDescriptorForEntity(defaultDescriptor.getClazz(), partitionEntry.getValue());
                    }
                    catch (EntityException e)
                    {
                        exceptionWrapper.exception = e;
                    }
                }
                return descriptor;
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultDescriptor;
    }

    private Map<PartitionKey, RecordController> cachedControllersPerEntity = Collections.synchronizedMap(new WeakHashMap<PartitionKey, RecordController>());

    protected RecordController getRecordControllerForEntity(IManagedEntity entity) throws EntityException
    {
        if(PartitionHelper.hasPartitionField(entity, getContext()))
        {

            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            final RecordController retVal = cachedControllersPerEntity.compute(new PartitionKey(entity), (partitionKey, controller) -> {
                if(controller == null)
                {
                    try
                    {
                        return getContext().getRecordController(getDescriptorForEntity(entity));
                    }
                    catch (EntityException e)
                    {
                        exceptionWrapper.exception = e;
                    }
                }
                return controller;
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultRecordController;
    }


    private Map<Long, RecordController> cachedControllersPerPartition = Collections.synchronizedMap(new WeakHashMap<Long, RecordController>());

    public RecordController getRecordControllerForPartition(long partitionId) throws EntityException
    {
        if(partitionId != 0)
        {
            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            RecordController retVal = cachedControllersPerPartition.compute(partitionId, (partitionKey, controller) -> {
                if(controller == null)
                {
                    try
                    {
                        EntityDescriptor inverseDescriptor = getDescriptorWithPartitionId(partitionId);
                        return getContext().getRecordController(inverseDescriptor);
                    }
                    catch (EntityException e)
                    {
                        exceptionWrapper.exception = e;
                    }
                }
                return controller;
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultRecordController;
    }


    private Map<PartitionKey, MapBuilder> cachedDataFilesPerEntity = Collections.synchronizedMap(new WeakHashMap<PartitionKey, MapBuilder>());

    protected MapBuilder getDataFileForEntity(IManagedEntity entity) throws EntityException
    {
        if (PartitionHelper.hasPartitionField(entity, getContext()))
        {
            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            MapBuilder dataFile = cachedDataFilesPerEntity.compute(new PartitionKey(entity), (partitionKey, db) -> {
                if(db != null)
                {
                    return db;
                }
                try
                {
                    return getContext().getDataFile(getDescriptorForEntity(entity));
                } catch (EntityException e)
                {
                    exceptionWrapper.exception = e;
                }
                return null;
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            if(dataFile == null)
                return this.getContext().getDataFile(this.defaultDescriptor);
            return dataFile;
        }
        else
        {
            return this.getContext().getDataFile(this.defaultDescriptor);
        }
    }

    private Map<PartitionKey, Long> cachedPartitionIds = Collections.synchronizedMap(new WeakHashMap<PartitionKey, Long>());

    protected long getPartitionId(IManagedEntity entity) throws EntityException
    {
        if (PartitionHelper.hasPartitionField(entity, getContext()))
        {
            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            Long partitionId = cachedPartitionIds.compute(new PartitionKey(entity), (partitionKey, id) -> {
                if(id != null)
                {
                    return id;
                }
                try
                {
                    Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, getContext());
                    if (partitionValue == null || partitionValue == PartitionHelper.NULL_PARTITION)
                    {
                        return 0L;
                    }
                    return getContext().getPartitionWithValue(partitionKey.entityType, partitionKey.partitionVal).getIndex();
                }
                catch (EntityException e)
                {
                    exceptionWrapper.exception = e;
                    return 0L;
                }
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return partitionId;
        }
        else
        {
            return 0;
        }
    }

    protected SchemaContext getContext()
    {
        return DefaultSchemaContext.registeredSchemaContexts.get(contextId);
    }
}
