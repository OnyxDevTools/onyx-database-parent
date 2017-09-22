package com.onyx.scan;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.interactors.scanner.TableScanner;
import com.onyx.interactors.scanner.impl.FullTableScanner;
import com.onyx.interactors.scanner.impl.PartitionFullTableScanner;
import com.onyx.persistence.annotations.values.RelationshipType;
import com.onyx.interactors.record.impl.DefaultRecordInteractor;
import com.onyx.interactors.relationship.RelationshipInteractor;
import com.onyx.interactors.relationship.data.RelationshipReference;
import com.onyx.util.map.CompatHashMap;
import com.onyx.exception.OnyxException;
import com.onyx.helpers.*;
import com.onyx.interactors.index.IndexInteractor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.*;
import com.onyx.persistence.query.AttributeUpdate;
import com.onyx.interactors.record.RecordInteractor;
import com.onyx.interactors.relationship.data.RelationshipTransaction;
import com.onyx.depricated.CompareUtil;
import com.onyx.util.ReflectionUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 3/5/15.
 * <p>
 * Controls how to query a partition
 */
public class PartitionQueryController extends PartitionContext {
    @SuppressWarnings("WeakerAccess")
    protected EntityDescriptor descriptor;
    @SuppressWarnings("WeakerAccess")
    protected RecordInteractor recordInteractor;
    @SuppressWarnings("WeakerAccess")
    protected MapBuilder temporaryDataFile;
    @SuppressWarnings("WeakerAccess")
    protected PersistenceManager persistenceManager;

    /**
     * Constructor that gets the necessary entity information
     *
     * @param descriptor         Entity descriptor
     * @param persistenceManager Persistence manager
     * @param context            Schema context
     */
    public PartitionQueryController(EntityDescriptor descriptor, PersistenceManager persistenceManager, SchemaContext context) {

        super(context, descriptor);
        this.descriptor = descriptor;
        this.recordInteractor = context.getRecordInteractor(descriptor);
        this.persistenceManager = persistenceManager;

        temporaryDataFile = context.createTemporaryMapBuilder();
    }

    @SuppressWarnings("unchecked")
    private Map getReferencesForCriteria(Query query, QueryCriteria criteria, Map filteredReferences, boolean forceFullScan) throws OnyxException {
        // Ensure query is still valid
        if (query.isTerminated()) {
            return new CompatHashMap();
        }

        TableScanner scanner;
        if(forceFullScan)
        {
            scanner = ScannerFactory.getInstance(getContext()).getFullTableScanner(criteria, query.getEntityType(), temporaryDataFile, query, persistenceManager);
        }
        else
        {
            scanner = ScannerFactory.getInstance(getContext()).getScannerForQueryCriteria(criteria, query.getEntityType(), temporaryDataFile, query, persistenceManager);
        }
        // Scan for records
        Map criteriaResults;

        // If there are existing references, use those to whiddle it down.  Otherwise
        // start from a clean slate
        if (filteredReferences == null) {
            criteriaResults = scanner.scan();
        } else {
            if(criteria.isOr() || criteria.isNot())
            {
                criteriaResults = scanner.scan();
            }
            else {
                criteriaResults = scanner.scan(filteredReferences);
            }
        }

        // If it is a full table scanner.  No need to go any further, we have all we need since
        // The full table scanner compares all critieria
        if (scanner instanceof FullTableScanner
                || scanner instanceof PartitionFullTableScanner)
            return criteriaResults;

        // Go through and ensure all the sub criteria is met
        for (Object subCriteriaObject : criteria.getSubCriteria()) {
            QueryCriteria<?> subCriteria = (QueryCriteria)subCriteriaObject;
            Map subCritieriaResults = getReferencesForCriteria(query, subCriteria, criteriaResults, false);
            aggregateFilteredReferences(subCriteria, criteriaResults, subCritieriaResults);
        }

        return criteriaResults;
    }

    /**
     * Used to correlate existing reference sets with the criteria met from
     * a single criteria.
     *
     * @param criteria Root Criteria
     * @param totalResults Results from previous scan iterations
     * @param criteriaResults Criteria results used to aggregate a contrived list
     *
     *
     */
    @SuppressWarnings("unchecked")
    private void aggregateFilteredReferences(QueryCriteria criteria, Map totalResults, Map criteriaResults)
    {
        if(criteria.isNot())
        {
            for (Object index : criteriaResults.keySet()) {
                totalResults.remove(index, index);
            }
        }
        else if(criteria.isOr()) {
            for (Object index : criteriaResults.keySet()) {
                totalResults.put(index, index);
            }
        }
        else if(criteria.isAnd())
        {
            Set itemsToRemove = new HashSet();
            //noinspection Convert2streamapi
            for (Object index : totalResults.keySet()) {
                if(!criteriaResults.containsKey(index))
                {
                    itemsToRemove.add(index);
                }
            }
            //noinspection Convert2streamapi
            for(Object index : itemsToRemove)
            {
                totalResults.remove(index);
            }
        }
    }

    /**
     * Find object ids that match the criteria
     *
     * @param query Query Criteria
     * @return References matching query criteria
     * @throws com.onyx.exception.OnyxException General query exception
     * @since 1.3.0 This has been refactored to remove the logic for meeting critieria.  That has
     * been moved to CompareUtil
     */
    @SuppressWarnings("unchecked")
    public Map getReferencesForQuery(Query query) throws OnyxException {
        return getReferencesForCriteria(query, query.getCriteria(), null, query.getCriteria().isNot());
    }

    /**
     * Hydrate a subset of records with the given identifiers
     *
     * @param query      Query containing all the munging instructions
     * @param references References from query results
     * @return Hydrated entities
     * @throws OnyxException Error hydrating entities
     */
    @SuppressWarnings("unchecked")
    public List hydrateResultsWithReferences(Query query, Map references) throws OnyxException {

        // Sort if needed
        if (query.getQueryOrders() != null
                && query.getQueryOrders().size() > 0) {
            references = this.sort(query, references);
        }

        final List returnValue = new ArrayList<>();

        int count = query.getMaxResults();
        int start = query.getFirstRow();

        // Count was not specified, lets get all
        if (count <= 0 || count > references.size()) {
            count = references.size();
        }

        Iterator<Map.Entry> iterator = references.entrySet().iterator();
        Map.Entry entry;
        Object index;
        IManagedEntity value = null;
        int i = 0;

        while (iterator.hasNext()) {

            if (i < start && count > 0) {
                i++;
                iterator.next();
                continue;
            }

            if ((i >= ((count + start)) || i >= references.size()))
                break;

            entry = iterator.next();
            index = entry.getKey();

            if(!(entry.getValue() instanceof IManagedEntity)) {

                if (index instanceof PartitionReference) {
                    PartitionReference ref = (PartitionReference) index;
                    value = getRecordInteractorForPartition(ref.partition).getWithReferenceId(ref.reference);
                } else if (index != null && index instanceof Long) {
                    value = recordInteractor.getWithReferenceId((long) index);
                }

                if (value != null) {
                    RelationshipHelper.hydrateAllRelationshipsForEntity(value, new RelationshipTransaction(), getContext());
                }

                // Back fill for query cache
                entry.setValue(value);
            }
            else
            {
                value = (IManagedEntity)entry.getValue();
            }

            if (value != null) {
                returnValue.add(value);
            }

            i++;
        }

        return returnValue;
    }


    /**
     * Sort using order by query order objects with included values
     *
     * @param query           Query containing order instructions
     * @param referenceValues Query reference values from result of scan
     * @return Sorted references
     * @throws OnyxException Error sorting objects
     */
    @SuppressWarnings({"unchecked", "RedundantThrows"})
    public Map sort(Query query, Map referenceValues) throws OnyxException {
        final Map retVal = new TreeMap(new PartitionSortCompare(query, (query.getQueryOrders() == null) ? new QueryOrder[0] : query.getQueryOrders().toArray(new QueryOrder[query.getQueryOrders().size()]), descriptor, getContext()));
        retVal.putAll(referenceValues);

        return retVal;
    }

    /**
     * Hydrate given attributes
     *
     * @param query      Query containing selection and count information
     * @param references References found during query execution
     * @return Hydrated key value set for entity attributes
     * @throws OnyxException Cannot hydrate entities
     */
    @SuppressWarnings("unchecked")
    public Map<Object, Map<String, Object>> hydrateQuerySelections(Query query, Map references) throws OnyxException {
        final List<ScannerProperties> scanObjects = ScannerProperties.getScannerProperties(query.getSelections().toArray(new String[query.getSelections().size()]), descriptor, query, getContext());

        if (references.size() == 0) {
            return new HashMap<>();
        }

        Map<Object, Map<String, Object>> results = new LinkedHashMap<>();

        Iterator<Map.Entry<Object, Object>> iterator = references.entrySet().iterator();

        Map.Entry<Object, Object> entry;
        Object entityAttribute;

        Map<String, Object> record;

        int count = query.getMaxResults();
        int start = query.getFirstRow();

        // Count was not specified, lets get all
        if (count <= 0 || count > references.size()) {
            count = references.size();
        }

        int i = 0;
        while (iterator.hasNext()) {
            if (i < start && count > 0) {
                iterator.next();
                i++;
                continue;
            }

            if ((i >= ((count + start)) || i >= references.size()))
                break;


            record = new CompatHashMap();

            entry = iterator.next();
            if(entry.getValue() instanceof Map)
            {
                results.put(entry.getKey(), (Map)entry.getValue());
            }
            else {
                for (ScannerProperties properties : scanObjects) {
                    if (properties.relationshipDescriptor != null) {
                        // Added support for TO Many relationships.  This must be treated differently in order to hydrate
                        // a list rather than a single object.  This is treated special
                        if (properties.relationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY
                                || properties.relationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY) {
                            entityAttribute = hydrateRelationshipToManyMap(entry.getKey(), properties);
                        }
                        // This is for a to one relationship
                        else {
                            entityAttribute = hydrateRelationshipToOneMap(entry.getKey(), properties);
                        }
                    } else {
                        entityAttribute = hydrateEntityMap(entry.getKey(), properties);
                    }

                    record.put(properties.getAttribute(), entityAttribute);
                }
                results.put(entry.getKey(), record);
                entry.setValue(entry.getKey());
            }

            i++;
        }

        return results;
    }

    /**
     * Delete record with reference ids
     *
     * @param records References to delete
     * @param query   Query object
     * @return Number of entities deleted
     * @throws OnyxException Cannot delete entities
     */
    @SuppressWarnings("unchecked")
    public int deleteRecordsWithReferences(Map records, Query query) throws OnyxException {

        final Iterator<Object> iterator = records.keySet().iterator();
        Object referenceId;
        IManagedEntity entity;
        int i = 0;
        int start = query.getFirstRow();
        int recordsUpdated = 0;

        int count = query.getMaxResults();

        // Count was not specified, lets get all
        if (count <= 0 || count > records.size()) {
            count = records.size();
        }

        while (iterator.hasNext()) {
            if (i < start && count > 0) {
                iterator.next();
                i++;
                continue;
            }

            if ((i >= ((count + start)) || i >= records.size()))
                break;

            referenceId = iterator.next();

            if (referenceId instanceof PartitionReference) {
                PartitionReference ref = (PartitionReference) referenceId;
                entity = getRecordInteractorForPartition(ref.partition).getWithReferenceId(ref.reference);
                IndexHelper.deleteAllIndexesForEntity(getContext(), getDescriptorWithPartitionId(ref.partition), ref.reference);
            } else {
                entity = recordInteractor.getWithReferenceId((long) referenceId);
                IndexHelper.deleteAllIndexesForEntity(getContext(), descriptor, (long) referenceId);
            }

            RelationshipHelper.deleteAllRelationshipsForEntity(entity, new RelationshipTransaction(), getContext());

            if (referenceId instanceof PartitionReference) {
                PartitionReference ref = (PartitionReference) referenceId;
                getRecordInteractorForPartition(ref.partition).delete(entity);
            } else {
                recordInteractor.delete(entity);
            }

            recordsUpdated++;
            i++;
        }

        return recordsUpdated;
    }

    /**
     * Hydrate an entity from a map
     *
     * @param entry      Key identifier
     * @param properties Scanner Properties for the attribute
     * @return hydrated entity as a map
     * @throws OnyxException Exception why trying to retrieve object
     */
    private Object hydrateEntityMap(Object entry, ScannerProperties properties) throws OnyxException {
        if (entry instanceof PartitionReference) {
            PartitionReference ref = (PartitionReference) entry;
            return getRecordInteractorForPartition(ref.partition).getAttributeWithReferenceId(properties.attributeDescriptor.getField(), ref.reference);
        } else {
            return properties.recordInteractor.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), (long) entry);
        }
    }

    /**
     * Hydrates a to one relationship and formats in the shape of a map
     *
     * @param entry      Query reference entry
     * @param properties Scanner properties
     * @return To one relationship as a map
     * @throws OnyxException General exception
     * @since 1.3.0
     */
    private Object hydrateRelationshipToOneMap(Object entry, ScannerProperties properties) throws OnyxException {
        List values = hydrateRelationshipToManyMap(entry, properties);
        if (values.size() > 0)
            return values.get(0);

        return null;
    }

    /**
     * Hydrates a to many relationship and formats in the shape of a map
     *
     * @param entry      Query reference entry
     * @param properties Scanner properties
     * @return List of to many relationships
     * @throws OnyxException General exception
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    private List hydrateRelationshipToManyMap(Object entry, ScannerProperties properties) throws OnyxException {
        // Get Relationship controller
        final RelationshipInteractor relationshipInteractor = getContext().getRelationshipInteractor(properties.relationshipDescriptor);

        List<RelationshipReference> relationshipReferences;

        // Get relationship references
        if (entry instanceof PartitionReference) {
            relationshipReferences = relationshipInteractor.getRelationshipIdentifiersWithReferenceId((PartitionReference) entry);
        } else {
            relationshipReferences = relationshipInteractor.getRelationshipIdentifiersWithReferenceId((long) entry);
        }


        List value = new ArrayList();

        // Iterate through relationship references and get the values of the relationship
        for (RelationshipReference ref : relationshipReferences) {
            Object relationshipMapValue;

            if (ref.getPartitionId() > 0) {
                final PartitionContext partitionContext = new PartitionContext(getContext(), properties.descriptor);
                final RecordInteractor recordInteractor = partitionContext.getRecordInteractorForPartition(ref.getPartitionId());
                if (properties.attributeDescriptor != null) {
                    relationshipMapValue = recordInteractor.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), recordInteractor.getReferenceId(ref.getIdentifier()));
                } else {
                    relationshipMapValue = recordInteractor.getMapWithReferenceId(recordInteractor.getReferenceId(ref.getIdentifier()));
                }
            } else {
                if (properties.attributeDescriptor != null) {
                    relationshipMapValue = properties.recordInteractor.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), properties.recordInteractor.getReferenceId(ref.getIdentifier()));
                } else {
                    relationshipMapValue = properties.recordInteractor.getMapWithReferenceId(properties.recordInteractor.getReferenceId(ref.getIdentifier()));
                }
            }

            value.add(relationshipMapValue);
        }

        return value;
    }

    /**
     * Update records
     *
     * @param query   Query information containing update values
     * @param results Entity references as a result of the query
     * @return how many entities were updated
     * @throws OnyxException Cannot update entity
     */
    @SuppressWarnings("unchecked")
    public int purformUpdatesForQuery(Query query, Map results) throws OnyxException {
        final Iterator<Object> iterator = results.keySet().iterator();
        Object referenceId;
        IManagedEntity entity;
        int i = 0;
        int recordsUpdated = 0;

        int count = query.getMaxResults();
        int start = query.getFirstRow();

        // Count was not specified, lets get all
        if (count <= 0 || count > results.size()) {
            count = results.size();
        }

        RecordInteractor possibleNewRecordInteractorForPartition = null;

        while (iterator.hasNext()) {

            if (i < start && count > 0) {
                iterator.next();
                i++;
                continue;
            }

            if ((i >= ((count + start)) || i >= results.size()))
                break;

            referenceId = iterator.next();

            if (referenceId instanceof PartitionReference) {
                PartitionReference ref = (PartitionReference) referenceId;
                entity = getRecordInteractorForPartition(ref.partition).getWithReferenceId(ref.reference);
            } else {
                entity = recordInteractor.getWithReferenceId((long) referenceId);
            }
            if (entity == null)
                continue;

            boolean updatedPartition = false;
            Object oldPartitionValue = null;

            final Object identifier = DefaultRecordInteractor.Companion.getIndexValueFromEntity(entity, descriptor.getIdentifier());

            // DELETE THE OLD INDEXES
            for (AttributeUpdate updateInstruction : query.getUpdates()) {
                // Identify whether the partition has changed
                if (referenceId instanceof PartitionReference
                        && PartitionHelper.isPartitionField(updateInstruction.getFieldName(), descriptor)
                        && !CompareUtil.compare(oldPartitionValue = PartitionHelper.getPartitionFieldValue(entity, getContext()), updateInstruction.getValue(), QueryCriteriaOperator.EQUAL)) {
                    updatedPartition = true;
                }


                ReflectionUtil.setAny(entity, updateInstruction.getValue(), updateInstruction.getAttributeDescriptor().getField());

                if (!updatedPartition && updateInstruction.getIndexInteractor() != null) {
                    // Save index values

                    if (referenceId instanceof PartitionReference && query.getPartition() == QueryPartitionMode.ALL) // This is in the case it is a mixed bag of partitioned data.  NOT EFFICIENT
                    {
                        EntityDescriptor oldDescriptor = getContext().getDescriptorForEntity(entity, PartitionHelper.getPartitionFieldValue(entity, getContext()));
                        IndexInteractor previousIndexInteractor = getContext().getIndexInteractor(oldDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                        previousIndexInteractor.delete(((PartitionReference) referenceId).reference);
                    } else if (referenceId instanceof PartitionReference) {
                        updateInstruction.getIndexInteractor().delete(((PartitionReference) referenceId).reference);
                    }
                    else
                    {
                        updateInstruction.getIndexInteractor().delete((long) referenceId);
                    }
                } else if (updatedPartition && updateInstruction.getIndexInteractor() != null && referenceId instanceof PartitionReference) {
                    EntityDescriptor oldDescriptor = getContext().getDescriptorForEntity(entity, oldPartitionValue);
                    IndexInteractor previousIndexInteractor = getContext().getIndexInteractor(oldDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                    // Delete old index key and insert new one in new partition
                    previousIndexInteractor.delete(((PartitionReference) referenceId).reference);
                }
            }

            long newReferenceId;

            if (referenceId instanceof PartitionReference) {
                if (updatedPartition) {
                    getContext().getDescriptorForEntity(entity);

                    PartitionReference ref = (PartitionReference) referenceId;
                    getRecordInteractorForPartition(ref.partition).delete(entity);

                    if (possibleNewRecordInteractorForPartition == null) {
                        possibleNewRecordInteractorForPartition = getContext().getRecordInteractor(getContext().getDescriptorForEntity(entity));
                    }
                    possibleNewRecordInteractorForPartition.save(entity); // Re-partitioning this will be slooooowwww
                    newReferenceId = possibleNewRecordInteractorForPartition.getReferenceId(identifier);
                } else {
                    PartitionReference ref = (PartitionReference) referenceId;
                    RecordInteractor partitionRecordInteractor = getRecordInteractorForPartition(ref.partition);
                    partitionRecordInteractor.save(entity);
                    newReferenceId = partitionRecordInteractor.getReferenceId(identifier);
                }
            } else {
                recordInteractor.save(entity);
                newReferenceId = recordInteractor.getReferenceId(identifier);
            }

            // SAVE NEW INDEXES
            for (AttributeUpdate updateInstruction : query.getUpdates()) {

                if (!updatedPartition && updateInstruction.getIndexInteractor() != null) {
                    // Save index values
                    final Object indexValue = DefaultRecordInteractor.Companion.getIndexValueFromEntity(entity, updateInstruction.getIndexInteractor().getIndexDescriptor());
                    updateInstruction.getIndexInteractor().save(indexValue, 0, newReferenceId);
                } else if (updatedPartition && updateInstruction.getIndexInteractor() != null && referenceId instanceof PartitionReference) {

                    EntityDescriptor newDescriptor = getContext().getDescriptorForEntity(entity);
                    IndexInteractor newIndexInteractor = getContext().getIndexInteractor(newDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                    final Object indexValue = DefaultRecordInteractor.Companion.getIndexValueFromEntity(entity, newDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                    // Delete old index key and insert new one in new partition
                    newIndexInteractor.save(indexValue, 0, newReferenceId);
                }
            }


            recordsUpdated++;

            i++;
        }

        return recordsUpdated;
    }

    /**
     * Cleanup the query controller references so that we do not have memory leaks.
     * The most important part of this is to recycle the temporary map builders.
     */
    public void cleanup() {
        // Changed to recycle map builders rather than destroy
        if (this.temporaryDataFile != null) {
            getContext().releaseMapBuilder(this.temporaryDataFile);
        }
        this.contextId = null;
        this.descriptor = null;
        this.recordInteractor = null;
        this.temporaryDataFile = null;
        this.persistenceManager = null;
        this.defaultRecordInteractor = null;
        this.defaultDescriptor = null;
    }

    /**
     * Get the count for a query.  This is used to get the count without actually executing the query.  It is lighter weight
     * than the entire query and in most cases will use the longSize on the disk map data structure if it is
     * for the entire table.
     *
     * @param query Query to identify count for
     * @return The number of records matching query criterium
     * @throws OnyxException Excaption ocurred while executing query
     * @since 1.3.0 Added as enhancement #71
     */
    public long getCountForQuery(Query query) throws OnyxException {
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
                    DiskMap recs = (DiskMap) dataFile.getHashMap(partitionDescriptor.getEntityClass().getName(), partitionDescriptor.getIdentifier().getLoadFactor());

                    resultCount.addAndGet(recs.longSize());
                }
                return resultCount.get();
            } else if (query.getPartition() == null || query.getPartition().equals("")) {
                final EntityDescriptor partitionDescriptor = getContext().getBaseDescriptorForEntity(query.getEntityType());
                final MapBuilder dataFile = getContext().getDataFile(partitionDescriptor);
                DiskMap recs = (DiskMap) dataFile.getHashMap(partitionDescriptor.getEntityClass().getName(), partitionDescriptor.getIdentifier().getLoadFactor());
                return recs.longSize();
            } else {
                final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), query.getPartition());
                final MapBuilder dataFile = getContext().getDataFile(partitionDescriptor);
                DiskMap recs = (DiskMap) dataFile.getHashMap(partitionDescriptor.getEntityClass().getName(), partitionDescriptor.getIdentifier().getLoadFactor());
                return recs.longSize();
            }
        } else {
            Map results = this.getReferencesForQuery(query);
            return results.size();
        }
    }
}
