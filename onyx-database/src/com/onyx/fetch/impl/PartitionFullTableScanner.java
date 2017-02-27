package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.EntityExceptionWrapper;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.util.CompareUtil;
import com.onyx.util.ReflectionUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Created by timothy.osborn on 1/3/15.
 * <p>
 * It can either scan the entire table or a subset of index values
 */
public class PartitionFullTableScanner extends FullTableScanner implements TableScanner {

    private SystemEntity systemEntity = null;

    /**
     * Constructor
     *
     * @param criteria    Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor  Entity descriptor of entity type to scan
     */
    public PartitionFullTableScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
        systemEntity = context.getSystemEntityByName(query.getEntityType().getName());
    }


    /**
     * Scan records with existing values
     *
     * @param existingValues Existing values to check for criteria
     * @return Existing values that match the criteria
     * @throws EntityException Cannot scan partition
     */
    @SuppressWarnings("unchecked")
    private Map scanPartition(DiskMap existingValues, long partitionId) throws EntityException {
        final Map allResults = new HashMap();

        final Iterator iterator = existingValues.keySet().iterator();
        IManagedEntity entity;
        Object attributeValue;
        Object keyValue;

        while (iterator.hasNext()) {
            if (query.isTerminated())
                return allResults;

            keyValue = iterator.next();

            entity = (IManagedEntity) existingValues.get(keyValue);

            // Ensure entity still exists
            if (entity == null) {
                continue;
            }

            // Get the attribute key
            attributeValue = ReflectionUtil.getAny(entity, fieldToGrab);

            // Compare and add
            if (CompareUtil.compare(criteria.getValue(), attributeValue, criteria.getOperator())) {
                long ref = existingValues.getRecID(keyValue);
                allResults.put(new PartitionReference(partitionId, ref), new PartitionReference(partitionId, ref));
            }

        }

        return allResults;
    }

    /**
     * Full Table Scan
     *
     * @return References matching criteria
     * @throws EntityException Cannot scan partition
     */
    @SuppressWarnings("unchecked")
    public Map scan() throws EntityException {

        final EntityExceptionWrapper wrapper = new EntityExceptionWrapper();
        Map<PartitionReference, PartitionReference> results = new ConcurrentHashMap<>();

        if (query.getPartition() == QueryPartitionMode.ALL) {

            Iterator<SystemPartitionEntry> it = systemEntity.getPartition().getEntries().iterator();
            CountDownLatch partitionScanCountDown = new CountDownLatch(systemEntity.getPartition().getEntries().size());
            while (it.hasNext()) {
                final SystemPartitionEntry partition = it.next();

                executorService.execute(() -> {
                    try {
                        final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());

                        final MapBuilder dataFile = getContext().getDataFile(partitionDescriptor);
                        DiskMap recs = (DiskMap) dataFile.getHashMap(partitionDescriptor.getClazz().getName(), partitionDescriptor.getIdentifier().getLoadFactor());

                        Map partitionResults = scanPartition(recs, partition.getIndex());
                        results.putAll(partitionResults);
                        partitionScanCountDown.countDown();
                    } catch (EntityException e) {
                        wrapper.exception = e;
                    }

                });

            }

            try {
                partitionScanCountDown.await();
            } catch (InterruptedException ignore) {
            }

            if (wrapper.exception != null) {
                throw wrapper.exception;
            }
        } else {
            return super.scan();
        }

        return results;
    }

}