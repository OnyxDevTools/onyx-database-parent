package com.onyx.fetch;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.fetch.impl.FullTableScanner;
import com.onyx.fetch.impl.PartitionFullTableScanner;
import com.onyx.persistence.annotations.RelationshipType;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.util.map.CompatHashMap;
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
    protected RecordController recordController;
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
        this.recordController = context.getRecordController(descriptor);
        this.persistenceManager = persistenceManager;

        temporaryDataFile = context.createTemporaryMapBuilder();
    }

    @SuppressWarnings("unchecked")
    private Map getReferencesForCritieria(Query query, QueryCriteria criteria, Map filteredReferences, boolean forceFullScan) throws EntityException {
        // Ensure query is still valid
        if (query.isTerminated()) {
            return new CompatHashMap();
        }

        // Optimize the sub criteria
        sortCritieria(criteria);

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
        Map critieriaResults;

        // If there are existing references, use those to whiddle it down.  Otherwise
        // start from a clean slate
        if (filteredReferences == null) {
            critieriaResults = scanner.scan();
        } else {
            if(criteria.isOr() || criteria.isNot())
            {
                critieriaResults = scanner.scan();
            }
            else {
                critieriaResults = scanner.scan(filteredReferences);
            }
        }

        // If it is a full table scanner.  No need to go any further, we have all we need since
        // The full table scanner compares all critieria
        if (scanner instanceof FullTableScanner
                || scanner instanceof PartitionFullTableScanner)
            return critieriaResults;

        // Go through and ensure all the sub criteria is met
        for (QueryCriteria subCriteria : criteria.getSubCriteria()) {
            Map subCritieriaResults = getReferencesForCritieria(query, subCriteria, critieriaResults, false);
            aggrigateFilteredReferences(subCriteria, critieriaResults, subCritieriaResults);
        }

        return critieriaResults;
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
    private void aggrigateFilteredReferences(QueryCriteria criteria, Map totalResults, Map criteriaResults)
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
            for (Object index : totalResults.keySet()) {
                if(!criteriaResults.containsKey(index))
                {
                    itemsToRemove.add(index);
                }
            }
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
     * @throws com.onyx.exception.EntityException General query exception
     * @since 1.3.0 This has been refactored to remove the logic for meeting critieria.  That has
     * been moved to CompareUtil
     */
    @SuppressWarnings("unchecked")
    public Map getReferencesForQuery(Query query) throws EntityException {

        // If there are no critieria, add a dummy critieria to the list
        if (query.getCriteria() == null) {
            query.setCriteria(new QueryCriteria(descriptor.getIdentifier().getName(), QueryCriteriaOperator.NOT_EQUAL));
        }

        return getReferencesForCritieria(query, query.getCriteria(), null, query.getCriteria().isNot());
    }

    /**
     * This method is used to optimize the criteria.  If an identifier is included, that will move that
     * criteria to the top.  Next if an index is included, that will be moved to the top.
     * <p>
     * This was added as an enhancement so that the query is self optimized
     *
     * @param criteria Critieria to sort
     * @since 1.3.0 An effort to cleanup query results in preparation for query caching.
     */
    private void sortCritieria(QueryCriteria criteria) {
        Collections.sort(criteria.getSubCriteria(), (o1, o2) -> {

            // Check identifiers first
            boolean o1isIdentifier = descriptor.getIdentifier().getName().equals(o1.getAttribute());
            boolean o2isIdentifier = descriptor.getIdentifier().getName().equals(o2.getAttribute());

            if (o1isIdentifier && !o2isIdentifier)
                return 1;
            else if (o2isIdentifier && !o1isIdentifier)
                return -1;

            // Check indexes next
            boolean o1isIndex = descriptor.getIndexes().get(o1.getAttribute()) != null;
            boolean o2isIndex = descriptor.getIndexes().get(o2.getAttribute()) != null;

            if (o1isIndex && !o2isIndex)
                return 1;
            else if (o2isIndex && !o1isIndex)
                return -1;

            // Check relationships last.  A full table scan is prefered before a relationship
            boolean o1isRelationship = descriptor.getRelationships().get(o1.getAttribute()) != null;
            boolean o2isRelationship = descriptor.getRelationships().get(o2.getAttribute()) != null;

            if (o1isRelationship && !o2isRelationship)
                return -1;
            else if (o2isRelationship && !o1isRelationship)
                return 1;

            if(o1.getOperator().isIndexed() && !o2.getOperator().isIndexed())
                return 1;
            else if(o2.getOperator().isIndexed() && !o1.getOperator().isIndexed())
                return -1;

            // Lastly check for operators.  EQUAL has priority since it is less granular
            if (o1.getOperator() == QueryCriteriaOperator.EQUAL
                    && o2.getOperator() == QueryCriteriaOperator.EQUAL)
                return 0;
            else if (o1.getOperator() == QueryCriteriaOperator.EQUAL)
                return 1;
            else if (o2.getOperator() == QueryCriteriaOperator.EQUAL)
                return -1;
            else
                return 0;
        });

    }

    /**
     * Hydrate a subset of records with the given identifiers
     *
     * @param query      Query containing all the munging instructions
     * @param references References from query results
     * @return Hydrated entities
     * @throws EntityException Error hydrating entities
     */
    @SuppressWarnings("unchecked")
    public List hydrateResultsWithReferences(Query query, Map references) throws EntityException {

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

        Iterator<PartitionReference> iterator = references.keySet().iterator();
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

            index = iterator.next();

            if (index instanceof PartitionReference) {
                PartitionReference ref = (PartitionReference) index;
                value = getRecordControllerForPartition(ref.partition).getWithReferenceId(ref.reference);
            } else if (index != null && index instanceof Long) {
                value = recordController.getWithReferenceId((long) index);
            }
            if (value != null) {
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
     * @param query           Query containing order instructions
     * @param referenceValues Query reference values from result of scan
     * @return Sorted references
     * @throws EntityException Error sorting objects
     */
    @SuppressWarnings({"unchecked", "RedundantThrows"})
    public Map sort(Query query, Map referenceValues) throws EntityException {
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
     * @throws EntityException Cannot hydrate entities
     */
    @SuppressWarnings("unchecked")
    public Map<Object, Map<String, Object>> hydrateQuerySelections(Query query, Map references) throws EntityException {
        final List<ScannerProperties> scanObjects = ScannerProperties.getScannerProperties(query.getSelections().toArray(new String[query.getSelections().size()]), descriptor, query, getContext());

        if (references.size() == 0) {
            return new HashMap<>();
        }

        Map<Object, Map<String, Object>> results;
        int TEMPORARY_MAP_LOAD_FACTOR = 1;
        results = temporaryDataFile.getHashMap("sortingValues", TEMPORARY_MAP_LOAD_FACTOR);

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
     * @param query   Query object
     * @return Number of entities deleted
     * @throws EntityException Cannot delete entities
     */
    @SuppressWarnings("unchecked")
    public int deleteRecordsWithReferences(Map records, Query query) throws EntityException {

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
                entity = getRecordControllerForPartition(ref.partition).getWithReferenceId(ref.reference);
                IndexHelper.deleteAllIndexesForEntity(getContext(), getDescriptorWithPartitionId(ref.partition), ref.reference);
            } else {
                entity = recordController.getWithReferenceId((long) referenceId);
                IndexHelper.deleteAllIndexesForEntity(getContext(), descriptor, (long) referenceId);
            }


            RelationshipHelper.deleteAllRelationshipsForEntity(entity, new EntityRelationshipManager(), getContext());

            if (referenceId instanceof PartitionReference) {
                PartitionReference ref = (PartitionReference) referenceId;
                getRecordControllerForPartition(ref.partition).delete(entity);
            } else {
                recordController.delete(entity);
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
     * @throws EntityException Exception why trying to retrieve object
     */
    private Object hydrateEntityMap(Object entry, ScannerProperties properties) throws EntityException {
        if (entry instanceof PartitionReference) {
            PartitionReference ref = (PartitionReference) entry;
            return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(properties.attributeDescriptor.getField(), ref.reference);
        } else {
            return properties.recordController.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), (long) entry);
        }
    }

    /**
     * Hydrates a to one relationship and formats in the shape of a map
     *
     * @param entry      Query reference entry
     * @param properties Scanner properties
     * @return To one relationship as a map
     * @throws EntityException General exception
     * @since 1.3.0
     */
    private Object hydrateRelationshipToOneMap(Object entry, ScannerProperties properties) throws EntityException {
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
     * @throws EntityException General exception
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    private List hydrateRelationshipToManyMap(Object entry, ScannerProperties properties) throws EntityException {
        // Get Relationship controller
        final RelationshipController relationshipController = getContext().getRelationshipController(properties.relationshipDescriptor);

        List<RelationshipReference> relationshipReferences;

        // Get relationship references
        if (entry instanceof PartitionReference) {
            relationshipReferences = relationshipController.getRelationshipIdentifiersWithReferenceId((PartitionReference) entry);
        } else {
            relationshipReferences = relationshipController.getRelationshipIdentifiersWithReferenceId((long) entry);
        }


        List value = new ArrayList();

        // Iterate through relationship references and get the values of the relationship
        for (RelationshipReference ref : relationshipReferences) {
            Object relationshipMapValue;

            if (ref.partitionId > 0) {
                final PartitionContext partitionContext = new PartitionContext(getContext(), properties.descriptor);
                final RecordController recordController = partitionContext.getRecordControllerForPartition(ref.partitionId);
                if (properties.attributeDescriptor != null) {
                    relationshipMapValue = recordController.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), recordController.getReferenceId(ref.identifier));
                } else {
                    relationshipMapValue = recordController.getMapWithReferenceId(recordController.getReferenceId(ref.identifier));
                }
            } else {
                if (properties.attributeDescriptor != null) {
                    relationshipMapValue = properties.recordController.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), properties.recordController.getReferenceId(ref.identifier));
                } else {
                    relationshipMapValue = properties.recordController.getMapWithReferenceId(properties.recordController.getReferenceId(ref.identifier));
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
     * @throws EntityException Cannot update entity
     */
    @SuppressWarnings("unchecked")
    public int performUpdatsForQuery(Query query, Map results) throws EntityException {
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

        RecordController possibleNewRecordControllerForPartition = null;

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
                entity = getRecordControllerForPartition(ref.partition).getWithReferenceId(ref.reference);
            } else {
                entity = recordController.getWithReferenceId((long) referenceId);
            }
            if (entity == null)
                continue;

            boolean updatedPartition = false;
            Object oldPartitionValue = null;

            final Object identifier = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());

            // DELETE THE OLD INDEXES
            for (AttributeUpdate updateInstruction : query.getUpdates()) {
                // Identify whether the partition has changed
                if (referenceId instanceof PartitionReference
                        && PartitionHelper.isPartitionField(updateInstruction.getFieldName(), descriptor)
                        && !CompareUtil.compare(oldPartitionValue = PartitionHelper.getPartitionFieldValue(entity, getContext()), updateInstruction.getValue(), QueryCriteriaOperator.EQUAL)) {
                    updatedPartition = true;
                }


                ReflectionUtil.setAny(entity, updateInstruction.getValue(), updateInstruction.getAttributeDescriptor().getField());

                if (!updatedPartition && updateInstruction.getIndexController() != null) {
                    // Save index values

                    if (referenceId instanceof PartitionReference && query.getPartition() == QueryPartitionMode.ALL) // This is in the case it is a mixed bag of partitioned data.  NOT EFFICIENT
                    {
                        EntityDescriptor oldDescriptor = getContext().getDescriptorForEntity(entity, PartitionHelper.getPartitionFieldValue(entity, getContext()));
                        IndexController previousIndexController = getContext().getIndexController(oldDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                        previousIndexController.delete(((PartitionReference) referenceId).reference);
                    } else {
                        updateInstruction.getIndexController().delete((long) referenceId);
                    }
                } else if (updatedPartition && updateInstruction.getIndexController() != null && referenceId instanceof PartitionReference) {
                    EntityDescriptor oldDescriptor = getContext().getDescriptorForEntity(entity, oldPartitionValue);
                    IndexController previousIndexController = getContext().getIndexController(oldDescriptor.getIndexes().get(updateInstruction.getFieldName()));

                    // Delete old index key and insert new one in new partition
                    previousIndexController.delete(((PartitionReference) referenceId).reference);
                }
            }

            long newReferenceId;

            if (referenceId instanceof PartitionReference) {
                if (updatedPartition) {
                    getContext().getDescriptorForEntity(entity);

                    PartitionReference ref = (PartitionReference) referenceId;
                    getRecordControllerForPartition(ref.partition).delete(entity);

                    if (possibleNewRecordControllerForPartition == null) {
                        possibleNewRecordControllerForPartition = getContext().getRecordController(getContext().getDescriptorForEntity(entity));
                    }
                    possibleNewRecordControllerForPartition.save(entity); // Re-partitioning this will be slooooowwww
                    newReferenceId = possibleNewRecordControllerForPartition.getReferenceId(identifier);
                } else {
                    PartitionReference ref = (PartitionReference) referenceId;
                    RecordController partitionRecordController = getRecordControllerForPartition(ref.partition);
                    partitionRecordController.save(entity);
                    newReferenceId = partitionRecordController.getReferenceId(identifier);
                }
            } else {
                recordController.save(entity);
                newReferenceId = recordController.getReferenceId(identifier);
            }

            // SAVE NEW INDEXES
            for (AttributeUpdate updateInstruction : query.getUpdates()) {

                if (!updatedPartition && updateInstruction.getIndexController() != null) {
                    // Save index values
                    final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, updateInstruction.getIndexController().getIndexDescriptor());
                    updateInstruction.getIndexController().save(indexValue, 0, newReferenceId);
                } else if (updatedPartition && updateInstruction.getIndexController() != null && referenceId instanceof PartitionReference) {

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
        this.recordController = null;
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
            Map results = this.getReferencesForQuery(query);
            return results.size();
        }
    }
}
