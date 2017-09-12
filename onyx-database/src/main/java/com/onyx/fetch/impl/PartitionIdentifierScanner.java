package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityClassNotFoundException;
import com.onyx.helpers.PartitionHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.util.ReflectionUtil;
import com.onyx.util.map.CompatHashMap;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.SynchronizedMap;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.OnyxException;
import com.onyx.exception.OnyxExceptionWrapper;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.interactors.record.RecordInteractor;
import com.onyx.diskmap.MapBuilder;

import java.util.*;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * This scans a parition for matching identifiers
 */
public class PartitionIdentifierScanner extends IdentifierScanner implements TableScanner
{

    private SystemEntity systemEntity = null;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     */
    public PartitionIdentifierScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws OnyxException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
        systemEntity = context.getSystemEntityByName(query.getEntityType().getName());
    }

    /**
     * Full scan with ids
     *
     * @return References matching criteria
     * @throws OnyxException Cannot scan partition
     */
    @SuppressWarnings("unchecked")
    private Map scanPartition(RecordInteractor recordInteractor, long partitionId) throws OnyxException
    {
        final Map returnValue = new CompatHashMap();

        // If it is an in clause
        if(criteria.getValue() instanceof List)
        {
            for (Object idValue : (List)criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                long referenceId = recordInteractor.getReferenceId(idValue);
                // The id does exist, lets add it to the results
                if(referenceId > -1)
                {
                    final PartitionReference reference = new PartitionReference(partitionId, referenceId);
                    returnValue.put(reference, reference);
                }
            }
        }

        // Its an equals, if the object exists, add it to the results
        else
        {
            long referenceId = recordInteractor.getReferenceId(criteria.getValue());
            if(referenceId > -1)
            {
                final PartitionReference reference = new PartitionReference(partitionId, referenceId);
                returnValue.put(reference, reference);
            }
        }

        return returnValue;
    }

    /**
     * Scan existing values for identifiers
     *
     * @return Matching identifiers within partition
     * @throws OnyxException Cannot scan partition
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map scan() throws OnyxException
    {
        final OnyxExceptionWrapper wrapper = new OnyxExceptionWrapper();
        CompatMap<PartitionReference, PartitionReference> results = new SynchronizedMap();

        if(query.getPartition() == QueryPartitionMode.ALL)
        {
            for(SystemPartitionEntry partition : systemEntity.getPartition().getEntries())
            {
                try {
                    final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());
                    final RecordInteractor recordInteractor = getContext().getRecordInteractor(partitionDescriptor);
                    Map partitionResults = scanPartition(recordInteractor, partition.getIndex());
                    results.putAll(partitionResults);
                } catch (OnyxException e) {
                    wrapper.setException(e);
                }
            }

            if (wrapper.getException() != null)
            {
                throw wrapper.getException();
            }
        }
        else
        {
            IManagedEntity entity;
            try {
                entity = (IManagedEntity) ReflectionUtil.instantiate(query.getEntityType());
            } catch (InstantiationException | IllegalAccessException e) {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND, query.getEntityType());
            }

            PartitionHelper.setPartitionValueForEntity(entity, query.getPartition(), getContext());
            long partitionId = getPartitionId(entity);
            if(partitionId < 1)
                return new HashMap();
            RecordInteractor partitionRecordInteractor = getRecordInteractorForPartition(partitionId);
            results.putAll(scanPartition(partitionRecordInteractor, partitionId));
        }

        return results;
    }


    /**
     * Scan existing values for identifiers
     *
     * @param existingValues Existing values to match with criteria
     * @return Values matching criteria
     * @throws OnyxException Cannot scan partition
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map scan(Map existingValues) throws OnyxException
    {
        final CompatMap returnValue = new CompatHashMap();

        final RecordInteractor recordInteractor = getContext().getRecordInteractor(descriptor);

        Iterator iterator = existingValues.keySet().iterator();

        Object key;

        while (iterator.hasNext())
        {
            key = iterator.next();

            if(key instanceof PartitionReference)
            {
                RecordInteractor recordInteractorForPartition = this.getRecordInteractorForPartition(((PartitionReference) key).partition);
                // If it is an in clause
                if(criteria.getValue() instanceof List)
                {
                    for (Object idValue : (List)criteria.getValue())
                    {
                        if(query.isTerminated())
                            return returnValue;

                        long referenceId = recordInteractorForPartition.getReferenceId(idValue);

                        if(referenceId == ((PartitionReference)key).reference)
                        {
                            returnValue.put(key, key);
                        }
                    }
                }
                // Its an equals, if the object exists, add it to the results
                else
                {
                    long referenceId = recordInteractorForPartition.getReferenceId(criteria.getValue());

                    if(referenceId ==  ((PartitionReference)key).reference)
                    {
                        returnValue.put(key, key);
                    }
                }
            }
            else
            {
                // If it is an in clause
                if(criteria.getValue() instanceof List)
                {
                    for (Object idValue : (List)criteria.getValue())
                    {
                        if(query.isTerminated())
                            return returnValue;

                        long referenceId = recordInteractor.getReferenceId(idValue);

                        if(referenceId == (Long)key)
                        {
                            returnValue.put(referenceId, referenceId);
                        }
                    }
                }
                // Its an equals, if the object exists, add it to the results
                else
                {
                    long referenceId = recordInteractor.getReferenceId(criteria.getValue());

                    if(referenceId ==  (Long)key)
                    {
                        returnValue.put(referenceId, referenceId);
                    }
                }
            }
        }

        return returnValue;
    }
}
