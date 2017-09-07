package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.MapBuilder;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.OnyxException;
import com.onyx.exception.OnyxExceptionWrapper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.Contexts;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.RecordController;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.SynchronizedMap;

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
    private final CompatMap<Long, MapBuilder> cachedPartitionFiles = new SynchronizedMap<>(new CompatWeakHashMap<>());

    /**
     * Cached Data Files for partition ids, return default if there is no partition.  Otherwise insert into cache.
     *
     */
    protected MapBuilder getDataFileWithPartitionId(long partitionId) throws OnyxException
    {
        if(partitionId == 0)
        {
            return this.getContext().getDataFile(this.defaultDescriptor);
        }

        final OnyxExceptionWrapper exceptionWrapper = new OnyxExceptionWrapper();

        MapBuilder retVal = cachedPartitionFiles.computeIfAbsent(partitionId, (aLong) -> {
            try
            {
                return getContext().getPartitionDataFile(defaultDescriptor, partitionId);
            } catch (OnyxException e)
            {
                exceptionWrapper.setException(e);
            }
            return null;
        });

        if(exceptionWrapper.getException() != null)
        {
            throw exceptionWrapper.getException();
        }

        return retVal;
    }

    /**
     * Cached Partition Files
     */
    private class PartitionKey
    {

        PartitionKey(IManagedEntity entity) throws OnyxException
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

        final Class entityType;
        String partitionVal;
    }

    private final CompatMap<PartitionKey, EntityDescriptor> cachedDescriptorsPerEntity = new SynchronizedMap<>(new CompatWeakHashMap<>());

    @SuppressWarnings("WeakerAccess")
    public EntityDescriptor getDescriptorForEntity(IManagedEntity entity) throws OnyxException
    {
        if(PartitionHelper.hasPartitionField(entity, getContext()))
        {

            final OnyxExceptionWrapper exceptionWrapper = new OnyxExceptionWrapper();

            EntityDescriptor retVal = cachedDescriptorsPerEntity.computeIfAbsent(new PartitionKey(entity), (partitionKey) -> {
                try
                {
                    return getContext().getDescriptorForEntity(entity);
                }
                catch (OnyxException e)
                {
                    exceptionWrapper.setException(e);
                }
                return null;
            });

            if(exceptionWrapper.getException() != null)
            {
                throw exceptionWrapper.getException();
            }

            return retVal;
        }
        return defaultDescriptor;
    }



    private final CompatMap<Long, EntityDescriptor> cachedDescriptorsPerPartition = new SynchronizedMap<>(new CompatWeakHashMap<>());

    protected EntityDescriptor getDescriptorWithPartitionId(long partitionId) throws OnyxException
    {
        if(partitionId != 0)
        {

            final OnyxExceptionWrapper exceptionWrapper = new OnyxExceptionWrapper();

            EntityDescriptor retVal = cachedDescriptorsPerPartition.computeIfAbsent(partitionId, (partitionKey) -> {
                try
                {
                    SystemPartitionEntry partitionEntry = getContext().getPartitionWithId(partitionId);
                    if(partitionEntry == null)
                    {
                        return defaultDescriptor;
                    }

                    // since 1.2.3 This has been fixed because previously we could not depend on the defaultDescriptor
                    // as being identified as the class we are trying to get the partition entry for
                    try {
                        return getContext().getDescriptorForEntity(Class.forName(partitionEntry.getPartition().getEntityClass()), partitionEntry.getValue());
                    } catch (ClassNotFoundException ignore) {
                        // This is ignored because if you get this far without having a defined entity that should never happen
                    }
                }
                catch (OnyxException e)
                {
                    exceptionWrapper.setException(e);
                }
                return null;
            });

            if(exceptionWrapper.getException() != null)
            {
                throw exceptionWrapper.getException();
            }

            return retVal;
        }
        return defaultDescriptor;
    }

    private final CompatMap<PartitionKey, RecordController> cachedControllersPerEntity = new SynchronizedMap<>(new CompatWeakHashMap<>());

    protected RecordController getRecordControllerForEntity(IManagedEntity entity) throws OnyxException
    {
        if(PartitionHelper.hasPartitionField(entity, getContext()))
        {

            final OnyxExceptionWrapper exceptionWrapper = new OnyxExceptionWrapper();

            final RecordController retVal = cachedControllersPerEntity.computeIfAbsent(new PartitionKey(entity), (partitionKey) -> {
                try
                {
                    return getContext().getRecordController(getDescriptorForEntity(entity));
                }
                catch (OnyxException e)
                {
                    exceptionWrapper.setException(e);
                }
                return null;
            });

            if(exceptionWrapper.getException() != null)
            {
                throw exceptionWrapper.getException();
            }

            return retVal;
        }
        return defaultRecordController;
    }


    private final CompatMap<Long, RecordController> cachedControllersPerPartition = new SynchronizedMap<>(new CompatWeakHashMap<>());

    public RecordController getRecordControllerForPartition(long partitionId) throws OnyxException
    {
        if(partitionId != 0)
        {
            final OnyxExceptionWrapper exceptionWrapper = new OnyxExceptionWrapper();

            RecordController retVal = cachedControllersPerPartition.computeIfAbsent(partitionId, (partitionKey) -> {
                try
                {
                    EntityDescriptor inverseDescriptor = getDescriptorWithPartitionId(partitionId);
                    return getContext().getRecordController(inverseDescriptor);
                }
                catch (OnyxException e)
                {
                    exceptionWrapper.setException(e);
                }
                return null;
            });

            if(exceptionWrapper.getException() != null)
            {
                throw exceptionWrapper.getException();
            }

            return retVal;
        }
        return defaultRecordController;
    }


    private final CompatMap<PartitionKey, MapBuilder> cachedDataFilesPerEntity = new SynchronizedMap<>(new CompatWeakHashMap<>());

    protected MapBuilder getDataFileForEntity(IManagedEntity entity) throws OnyxException
    {
        if (PartitionHelper.hasPartitionField(entity, getContext()))
        {
            final OnyxExceptionWrapper exceptionWrapper = new OnyxExceptionWrapper();

            MapBuilder dataFile = cachedDataFilesPerEntity.computeIfAbsent(new PartitionKey(entity), (partitionKey) -> {
                try
                {
                    return getContext().getDataFile(getDescriptorForEntity(entity));
                } catch (OnyxException e)
                {
                    exceptionWrapper.setException(e);
                }
                return null;
            });

            if(exceptionWrapper.getException() != null)
            {
                throw exceptionWrapper.getException();
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

    private final CompatMap<PartitionKey, Long> cachedPartitionIds = new SynchronizedMap<>(new CompatWeakHashMap<>());

    protected long getPartitionId(IManagedEntity entity) throws OnyxException
    {
        if (PartitionHelper.hasPartitionField(entity, getContext()))
        {
            final OnyxExceptionWrapper exceptionWrapper = new OnyxExceptionWrapper();

            Long partitionId = cachedPartitionIds.computeIfAbsent(new PartitionKey(entity), (partitionKey) -> {
                try
                {
                    Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, getContext());
                    if (partitionValue == null || partitionValue == PartitionHelper.NULL_PARTITION)
                    {
                        return 0L;
                    }
                    return getContext().getPartitionWithValue(partitionKey.entityType, partitionKey.partitionVal).getIndex();
                }
                catch (OnyxException e)
                {
                    exceptionWrapper.setException(e);
                    return 0L;
                }
            });

            if(exceptionWrapper.getException() != null)
            {
                throw exceptionWrapper.getException();
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
        return Contexts.get(contextId);
    }
}
