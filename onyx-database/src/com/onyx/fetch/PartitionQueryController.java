package com.onyx.fetch;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.helpers.*;
import com.onyx.index.IndexController;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.*;
import com.onyx.persistence.update.AttributeUpdate;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.util.CompareUtil;
import com.onyx.util.ReflectionUtil;
import com.onyx.util.map.CompatHashMap;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 3/5/15.
 *
 * Controls how to query a partition
 */
public class PartitionQueryController extends PartitionContext
{
    @SuppressWarnings("unused")
    protected QueryCriteria criteria;
    @SuppressWarnings("WeakerAccess")
    protected Class classToScan;
    @SuppressWarnings("WeakerAccess")
    protected EntityDescriptor descriptor;
    @SuppressWarnings("WeakerAccess")
    protected RecordController recordController;
    @SuppressWarnings("WeakerAccess")
    protected Query query;
    @SuppressWarnings("WeakerAccess")
    protected MapBuilder temporaryDataFile;
    @SuppressWarnings("WeakerAccess")
    protected PersistenceManager persistenceManager;

    /**
     * Constructor that gets the necessary entity information
     *
     * @param criteria Query Criteria
     * @param classToScan Entity to scan
     * @param descriptor Entity descriptor
     */
    public PartitionQueryController(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, Query query, SchemaContext context, PersistenceManager persistenceManager)
    {

        super(context, descriptor);
        this.criteria = criteria;
        this.classToScan = classToScan;
        this.descriptor = descriptor;
        this.recordController = context.getRecordController(descriptor);
        this.query = query;
        this.persistenceManager = persistenceManager;

        temporaryDataFile = context.createTemporaryMapBuilder();
    }

    /**
     * Find object ids that match the criteria
     *
     * @param criteria Query Criteria
     * @param startingResults Results to start with
     * @param replace Whether to replace the reference
     * @return References matching criteria
     * @throws com.onyx.exception.EntityException General query exception
     */
    @SuppressWarnings("unchecked")
    public Map getIndexesForCriteria(QueryCriteria criteria, Map startingResults, boolean replace, Query query) throws EntityException
    {

        Map results;

        if(criteria == null){
            criteria = new QueryCriteria(descriptor.getIdentifier().getName(), QueryCriteriaOperator.NOT_EQUAL);
        }

        // If it is a negative criteria to start off the bat, we need to get the entire set of records
        if (criteria.isNot() && startingResults == null) {
            QueryCriteria tempCriteria = new QueryCriteria(descriptor.getIdentifier().getName(), QueryCriteriaOperator.NOT_EQUAL);
            criteria = tempCriteria.and(criteria);
        }

        final TableScanner scanner = ScannerFactory.getInstance(getContext()).getScannerForQueryCriteria(criteria, classToScan, temporaryDataFile, query, persistenceManager);

        if (startingResults != null)
        {
            results = scanner.scan(startingResults);
        } else
        {
            results = scanner.scan();
        }


        if (!replace && startingResults != null)
        {
            if (criteria.isNot())
            {
                for (Object index : results.keySet()) {
                    if (query.isTerminated())
                        break;

                    if (criteria.isNot()) {
                        startingResults.remove(index);
                    }
                }
            } else {
                for (Object index : startingResults.keySet()) {
                    if (query.isTerminated())
                        break;

                    results.put(index, startingResults.get(index));
                }
            }
        }

        for (QueryCriteria subCriteria : criteria.getSubCriteria())
        {
            if(query.isTerminated())
                break;

            if (subCriteria.isAnd())
            {
                if (criteria.isNot()) {
                    Map itemsToRemove = getIndexesForCriteria(subCriteria, results, false, query);
                    Iterator<Object> iterator = itemsToRemove.keySet().iterator();
                    //noinspection WhileLoopReplaceableByForEach
                    while (iterator.hasNext()) {
                        startingResults.remove(iterator.next());
                    }
                } else {
                    results = getIndexesForCriteria(subCriteria, results, !subCriteria.isNot(), query);
                }
            } else if (subCriteria.isOr()) {
                Map orResults = getIndexesForCriteria(subCriteria, startingResults, !subCriteria.isNot(), query);

                for (Object index : orResults.keySet()) {
                    if (query.isTerminated())
                        break;

                    results.put(index, index);

                }
            }
        }

        if(query.isTerminated())
        {
            return new CompatHashMap();
        }

        if (criteria.isNot())
            return startingResults;
        return results;
    }

    /**
     * Hydrate a subset of records with the given identifiers
     *
     * @param results References from query results
     * @param orderBy Order criteria
     * @param start Start of results
     * @param count Number of results to truncate at
     * @return Hydrated entities
     * @throws EntityException Error hydrating entities
     */
    @SuppressWarnings("unchecked")
    public List hydrateResultsWithIndexes(Map results, QueryOrder[] orderBy, int start, int count) throws EntityException
    {

        // Sort if needed
        if (orderBy != null && orderBy.length > 0)
        {
            results = this.sort(orderBy, results);
        }

        final List returnValue = new ArrayList<>();

        // Count was not specified, lets get all
        if (count <= 0 || count > results.size())
        {
            count = results.size();
        }

        Iterator<PartitionReference> iterator = results.keySet().iterator();
        Object index;
        IManagedEntity value = null;
        int i = 0;

        while (iterator.hasNext())
        {

            if (i < start && count > 0)
            {
                i++;
                iterator.next();
                continue;
            }

            if ((i >= ((count + start)) || i >= results.size()))
                break;

            index = iterator.next();

            if(index instanceof PartitionReference)
            {
                PartitionReference ref = (PartitionReference) index;
                value = getRecordControllerForPartition(ref.partition).getWithReferenceId(ref.reference);
            }
            else if(index != null && index instanceof Long)
            {
                value = recordController.getWithReferenceId((long)index);
            }
            if(value != null) {
                RelationshipHelper.hydrateAllRelationshipsForEntity(value, new EntityRelationshipManager(), getContext());
                returnValue.add(value);
            }
            i++;
        }

        return returnValue;
    }


    /**
     * Sort using order by query order objects with included values
     *
     * @param orderBy Query order criteria
     * @param indexValues Index references
     * @return Sorted references
     * @throws EntityException Error sorting objects
     */
    @SuppressWarnings({"unchecked", "RedundantThrows"})
    public Map sort(QueryOrder[] orderBy, Map indexValues) throws EntityException
    {
        final Map retVal = new TreeMap(new PartitionSortCompare(query, orderBy, indexValues, descriptor, getContext()));
        retVal.putAll(indexValues);
        return retVal;
    }

    /**
     * Hydrate given attributes
     *
     * @param attributes Attributes to hydrate
     * @param indexValues References for entities
     * @param forSort For sorting purposes.
     * @param start Start range
     * @param count Number of entities to hydrate
     * @return Hydrated key value set for entity attributes
     *
     * @throws EntityException Cannot hydrate entities
     */
    @SuppressWarnings("unchecked")
    public Map<Object, Map<String, Object>> hydrateQueryAttributes(String[] attributes, Map indexValues, @SuppressWarnings("SameParameterValue") boolean forSort, long start, long count) throws EntityException
    {
        final List<ScannerProperties> scanObjects = ScannerProperties.getScannerProperties(attributes, descriptor, query, getContext());

        Map<Object, Map<String, Object>> results;
        if(!forSort)
        {
            results = new LinkedHashMap<>();
        }
        else
        {
            int TEMPORARY_MAP_LOAD_FACTOR = 1;
            results = temporaryDataFile.getHashMap("sortingValues", TEMPORARY_MAP_LOAD_FACTOR);
        }

        if(indexValues.size() == 0)
        {
            return results;
        }

        Iterator<Map.Entry<Object, Object>> iterator = indexValues.entrySet().iterator();

        Map.Entry<Object, Object> entry;
        Object entityAttribute;

        Map<String, Object> record;

        // Count was not specified, lets get all
        if (count <= 0 || count > indexValues.size())
        {
            count = indexValues.size();
        }

        int i = 0;
        while(iterator.hasNext())
        {
            if (i < start && count > 0)
            {
                iterator.next();
                i++;
                continue;
            }

            if ((i >= ((count + start)) || i >= indexValues.size()))
                break;


            record = new CompatHashMap();
            entry = iterator.next();
            for (ScannerProperties properties : scanObjects)
            {
                if(properties.useParentDescriptor)
                {
                    if(entry.getKey() instanceof PartitionReference)
                    {
                        PartitionReference ref = (PartitionReference) entry.getKey();
                        entityAttribute = getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(properties.attributeDescriptor.getField(), ref.reference);
                    }
                    else
                    {
                        entityAttribute = properties.recordController.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), (long)entry.getKey());
                    }
                }
                else
                {
                    if(entry.getKey() instanceof PartitionReference)
                    {
                        PartitionReference ref = (PartitionReference) entry.getValue();
                        entityAttribute = getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(properties.attributeDescriptor.getField(), ref.reference);
                    }
                    else
                    {
                        entityAttribute = properties.recordController.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), (long)entry.getValue());
                    }
                }
                record.put(properties.attributeDescriptor.getName(), entityAttribute);

                results.put(entry.getKey(), record);
            }

            i++;
        }

        return results;
    }

    /**
     * Delete record with reference ids
     *
     * @param records References to delete
     * @param query Query object
     * @return Number of entities deleted
     * @throws EntityException Cannot delete entities
     */
    @SuppressWarnings("unchecked")
    public int deleteRecordsWithIndexes(Map records, Query query) throws EntityException
    {

        final Iterator<Object> iterator = records.keySet().iterator();
        Object referenceId;
        IManagedEntity entity;
        int i = 0;
        int start = query.getFirstRow();
        int recordsUpdated = 0;

        int count = query.getMaxResults();

        // Count was not specified, lets get all
        if (count <= 0 || count > records.size())
        {
            count = records.size();
        }

        while(iterator.hasNext())
        {
            if (i < start && count > 0)
            {
                iterator.next();
                i++;
                continue;
            }

            if ((i >= ((count + start)) || i >= records.size()))
                break;

            referenceId = iterator.next();

            if(referenceId instanceof PartitionReference)
            {
                PartitionReference ref = (PartitionReference) referenceId;
                entity = getRecordControllerForPartition(ref.partition).getWithReferenceId(ref.reference);
                IndexHelper.deleteAllIndexesForEntity(getContext(), getDescriptorWithPartitionId(ref.partition), ref.reference);
            }
            else
            {
                entity = recordController.getWithReferenceId((long)referenceId);
                IndexHelper.deleteAllIndexesForEntity(getContext(), descriptor, (long)referenceId);
            }


            RelationshipHelper.deleteAllRelationshipsForEntity(entity, new EntityRelationshipManager(), getContext());

            if(referenceId instanceof PartitionReference)
            {
                PartitionReference ref = (PartitionReference) referenceId;
                getRecordControllerForPartition(ref.partition).delete(entity);
            }
            else
            {
                recordController.delete(entity);
            }

            recordsUpdated++;
            i++;
        }

        return recordsUpdated;
    }

    /**
     * Update records
     *
     * @param results Entity references
     * @param updates Attributes to update
     * @param start Start index of update
     * @param count max number of entities to update
     * @return how many entities were updated
     * @throws EntityException Cannot update entity
     */
    @SuppressWarnings("unchecked")
    public int updateRecordsWithValues(Map results, List<AttributeUpdate> updates, int start, int count) throws EntityException
    {
        final Iterator<Object> iterator = results.keySet().iterator();
        Object referenceId;
        IManagedEntity entity;
        int i = 0;
        int recordsUpdated = 0;

        // Count was not specified, lets get all
        if (count <= 0 || count > results.size())
        {
            count = results.size();
        }

        RecordController possibleNewRecordControllerForPartition = null;

        while(iterator.hasNext())
        {

            if (i < start && count > 0)
            {
                iterator.next();
                i++;
                continue;
            }

            if ((i >= ((count + start)) || i >= results.size()))
                break;

            referenceId = iterator.next();

            if(referenceId instanceof PartitionReference)
            {
                PartitionReference ref = (PartitionReference) referenceId;
                entity = getRecordControllerForPartition(ref.partition).getWithReferenceId(ref.reference);
            }
            else
            {
                entity = recordController.getWithReferenceId((long)referenceId);
            }
            if(entity == null)
                continue;

            boolean updatedPartition = false;
            Object oldPartitionValue = null;

            final Object identifier = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());

            // DELETE THE OLD INDEXES
            for(AttributeUpdate updateInstruction : updates)
            {
                // Identify whether the partition has changed
                if(referenceId instanceof PartitionReference
                        && PartitionHelper.isPartitionField(updateInstruction.getFieldName(), descriptor)
                        && !CompareUtil.compare(oldPartitionValue = PartitionHelper.getPartitionFieldValue(entity, getContext()), updateInstruction.getValue(), QueryCriteriaOperator.EQUAL))
                {
                    updatedPartition = true;
                }


                ReflectionUtil.setAny(entity, updateInstruction.getValue(), updateInstruction.getAttributeDescriptor().getField());

                if(!updatedPartition && updateInstruction.getIndexController() != null)
                {
                    // Save index values

                    if(referenceId instanceof PartitionReference && query.getPartition() == QueryPartitionMode.ALL) // This is in the case it is a mixed bag of partitioned data.  NOT EFFICIENT
                    {
                        EntityDescriptor oldDescriptor = getContext().getDescriptorForEntity(entity, PartitionHelper.getPartitionFieldValue(entity, getContext()));
                        IndexController previousIndexController = getContext().getIndexController(oldDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                        previousIndexController.delete(((PartitionReference) referenceId).reference);
                    }
                    else
                    {
                        updateInstruction.getIndexController().delete((long) referenceId);
                    }
                }
                else if(updatedPartition && updateInstruction.getIndexController() != null && referenceId instanceof PartitionReference)
                {
                    EntityDescriptor oldDescriptor = getContext().getDescriptorForEntity(entity, oldPartitionValue);
                    IndexController previousIndexController = getContext().getIndexController(oldDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                    // Delete old index key and insert new one in new partition
                    previousIndexController.delete(((PartitionReference) referenceId).reference);
                }
            }

            long newReferenceId;

            if(referenceId instanceof PartitionReference)
            {
                if(updatedPartition)
                {
                    getContext().getDescriptorForEntity(entity);

                    PartitionReference ref = (PartitionReference) referenceId;
                    getRecordControllerForPartition(ref.partition).delete(entity);

                    if(possibleNewRecordControllerForPartition == null)
                    {
                        possibleNewRecordControllerForPartition = getContext().getRecordController(getContext().getDescriptorForEntity(entity));
                    }
                    possibleNewRecordControllerForPartition.save(entity); // Re-partitioning this will be slooooowwww
                    newReferenceId = possibleNewRecordControllerForPartition.getReferenceId(identifier);
                }
                else
                {
                    PartitionReference ref = (PartitionReference) referenceId;
                    RecordController partitionRecordController = getRecordControllerForPartition(ref.partition);
                    partitionRecordController.save(entity);
                    newReferenceId = partitionRecordController.getReferenceId(identifier);
                }
            }
            else
            {
                recordController.save(entity);
                newReferenceId = recordController.getReferenceId(identifier);
            }

            // SAVE NEW INDEXES
            for(AttributeUpdate updateInstruction : updates)
            {

                if(!updatedPartition && updateInstruction.getIndexController() != null)
                {
                    // Save index values
                    final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, updateInstruction.getIndexController().getIndexDescriptor());
                    updateInstruction.getIndexController().save(indexValue, 0, newReferenceId);
                }
                else if(updatedPartition && updateInstruction.getIndexController() != null && referenceId instanceof PartitionReference)
                {

                    EntityDescriptor newDescriptor = getContext().getDescriptorForEntity(entity);
                    IndexController newIndexController = getContext().getIndexController(newDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                    final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, newDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                    // Delete old index key and insert new one in new partition
                    newIndexController.save(indexValue, 0, newReferenceId);
                }
            }


            recordsUpdated++;

            i++;
        }

        return recordsUpdated;
    }

    public void cleanup()
    {
        // Changed to recycle map builders rather than destroy
        if(this.temporaryDataFile != null) {
            getContext().releaseMapBuilder(this.temporaryDataFile);
        }
        this.contextId = null;
        this.criteria = null;
        this.classToScan = null;
        this.descriptor = null;
        this.recordController = null;
        this.query = null;
        this.temporaryDataFile = null;
        this.persistenceManager = null;
        this.defaultRecordController = null;
        this.defaultDescriptor = null;
    }

    /**
     * Get the count for a query.  This is used to get the count without actually executing the query.  It is lighter weight
     * than the entire query and in most cases will use the longSize on the disk map data structure if it is
     * for the entire table.
     *
     * @param query Query to identify count for
     * @return The number of records matching query criterium
     * @throws EntityException Excaption ocurred while executing query
     * @since 1.3.0 Added as enhancement #71
     */
    public long getCountForQuery(Query query) throws EntityException {
        if (ValidationHelper.isDefaultQuery(descriptor, query)) {
            SystemEntity systemEntity = getContext().getSystemEntityByName(query.getEntityType().getName());

            if (QueryPartitionMode.ALL.equals(query.getPartition())) {
                AtomicLong resultCount = new AtomicLong(0);

                Iterator<SystemPartitionEntry> it = systemEntity.getPartition().getEntries().iterator();
                //noinspection WhileLoopReplaceableByForEach
                while (it.hasNext()) {
                    final SystemPartitionEntry partition = it.next();

                    final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());

                    final MapBuilder dataFile = getContext().getDataFile(partitionDescriptor);
                    DiskMap recs = (DiskMap) dataFile.getHashMap(partitionDescriptor.getClazz().getName(), partitionDescriptor.getIdentifier().getLoadFactor());

                    resultCount.addAndGet(recs.longSize());
                }
                return resultCount.get();
            } else if (query.getPartition() == null || query.getPartition().equals("")) {
                final EntityDescriptor partitionDescriptor = getContext().getBaseDescriptorForEntity(query.getEntityType());
                final MapBuilder dataFile = getContext().getDataFile(partitionDescriptor);
                DiskMap recs = (DiskMap) dataFile.getHashMap(partitionDescriptor.getClazz().getName(), partitionDescriptor.getIdentifier().getLoadFactor());
                return recs.longSize();
            } else {
                final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), query.getPartition());
                final MapBuilder dataFile = getContext().getDataFile(partitionDescriptor);
                DiskMap recs = (DiskMap) dataFile.getHashMap(partitionDescriptor.getClazz().getName(), partitionDescriptor.getIdentifier().getLoadFactor());
                return recs.longSize();
            }
        } else {
            Map results = this.getIndexesForCriteria(query.getCriteria(), null, true, query);
            return results.size();
        }
    }
}

