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
import com.onyx.interactors.index.IndexInteractor;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.diskmap.MapBuilder;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan a partition for matching index values
 */
public class PartitionIndexScanner extends IndexScanner implements TableScanner {

    private SystemEntity systemEntity = null;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     * @param temporaryDataFile Temproary data file to put results into
     */
    public PartitionIndexScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws OnyxException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);

        systemEntity = context.getSystemEntityByName(query.getEntityType().getName());
    }

    /**
     * Full Table Scan
     *
     * @return Matching references for criteria
     * @throws OnyxException Cannot scan partition
     */
    @SuppressWarnings("unchecked")
    public Map scan() throws OnyxException
    {

        final OnyxExceptionWrapper wrapper = new OnyxExceptionWrapper();
        CompatMap<PartitionReference, PartitionReference> results = new SynchronizedMap();

        if(query.getPartition() == QueryPartitionMode.ALL)
        {
            Iterator<SystemPartitionEntry> it = systemEntity.getPartition().getEntries().iterator();
            final CountDownLatch countDownLatch = new CountDownLatch(systemEntity.getPartition().getEntries().size());

            while(it.hasNext())
            {
                final SystemPartitionEntry partition = it.next();

                executorService.execute(() -> {
                    try
                    {
                        final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());
                        final IndexInteractor partitionIndexInteractor = getContext().getIndexInteractor(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
                        Map partitionResults = scanPartition(partitionIndexInteractor, partition.getIndex());
                        results.putAll(partitionResults);
                        countDownLatch.countDown();
                    } catch (OnyxException e)
                    {
                        countDownLatch.countDown();
                        wrapper.setException(e);
                    }

                });

            }

            try {
                countDownLatch.await();
            } catch (InterruptedException ignore) {}

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

            final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), query.getPartition());
            final IndexInteractor partitionIndexInteractor = getContext().getIndexInteractor(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
            results.putAll(scanPartition(partitionIndexInteractor, partitionId));
        }

        return results;
    }

    /**
     * Scan indexes
     *
     * @return Matching values meeting criteria
     * @throws OnyxException Cannot scan partition
     */
    @SuppressWarnings("unchecked")
    private Map scanPartition(IndexInteractor partitionIndexInteractor, long partitionId) throws OnyxException
    {
        final CompatMap returnValue = new CompatHashMap();
        final List<Long> references = new ArrayList<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                references.addAll((Collection<? extends Long>) partitionIndexInteractor.findAll(idValue).keySet());
            }
        }
        else
        {
            Set values;

            if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                values = partitionIndexInteractor.findAllAbove(criteria.getValue(), false);
            else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                values = partitionIndexInteractor.findAllAbove(criteria.getValue(), true);
            else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                values = partitionIndexInteractor.findAllBelow(criteria.getValue(), false);
            else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                values = partitionIndexInteractor.findAllBelow(criteria.getValue(), true);
            else
                values =  partitionIndexInteractor.findAll(criteria.getValue()).keySet();

            references.addAll(values);
        }

        for(Long val : references)
        {
            returnValue.put(new PartitionReference(partitionId, val), new PartitionReference(partitionId, val));
        }

        return returnValue;
    }

    /**
     * Scan indexes that are within the existing values
     *
     * @param existingValues Existing values to match criteria
     * @return Existing values meeting additional criteria
     * @throws OnyxException Cannot scan partition
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map scan(Map existingValues) throws OnyxException
    {
        final CompatMap returnValue = new CompatHashMap<>();

        final OnyxExceptionWrapper wrapper = new OnyxExceptionWrapper();
        CompatMap<PartitionReference, PartitionReference> results = new SynchronizedMap<>();

        if(query.getPartition() == QueryPartitionMode.ALL)
        {
            for(SystemPartitionEntry partition : systemEntity.getPartition().getEntries())
            {
                try {
                    final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());
                    final IndexInteractor partitionIndexInteractor = getContext().getIndexInteractor(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
                    Map partitionResults = scanPartition(partitionIndexInteractor, partition.getIndex());

                    results.putAll(partitionResults);

                    //noinspection Convert2streamapi
                    for(PartitionReference reference : results.keySet())
                    {
                        if (existingValues.containsKey(reference)) {
                            returnValue.put(reference, reference);
                        }
                    }

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

            final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), query.getPartition());
            final IndexInteractor partitionIndexInteractor = getContext().getIndexInteractor(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
            results.putAll(scanPartition(partitionIndexInteractor, partitionId));

            //noinspection Convert2streamapi
            for(PartitionReference reference : results.keySet())
            {
                if (existingValues.containsKey(reference)) {
                    returnValue.put(reference, reference);
                }
            }
        }
        return returnValue;
    }
}
