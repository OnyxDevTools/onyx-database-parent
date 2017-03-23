package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.record.RecordController;
import com.onyx.util.CompareUtil;
import com.onyx.util.map.CompatHashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by timothy.osborn on 1/3/15.
 * <p>
 * It can either scan the entire table or a subset of index values
 */
public class FullTableScanner extends AbstractTableScanner implements TableScanner {

    /**
     * Constructor
     *
     * @param criteria    Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor  Entity descriptor of entity type to scan
     */
    public FullTableScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
    }


    /**
     * Full Table Scan
     *
     * @return Map of identifiers.  The key is the partition reference and the value is the reference within file.
     * @throws EntityException Query exception while trying to scan elements
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @SuppressWarnings("unckecked")
    public Map<Long, Long> scan() throws EntityException {
        final Map<Long, Long> allResults = new CompatHashMap<>();

        // We need to do a full scan
        final Iterator iterator = records.referenceSet().iterator();

        SkipListNode entry;
        IManagedEntity entity;

        final List<QueryCriteria> allCritieria = new ArrayList<>();
        aggregateCritieria(criteria, allCritieria);

        while (iterator.hasNext()) {
            if (query.isTerminated())
                return allResults;

            entry = (SkipListNode) iterator.next();
            entity = records.getWithRecID(entry.recordId);

            if (CompareUtil.meetsCriteria(allCritieria, criteria, entity, entry, getContext(), descriptor))
                allResults.put(entry.recordId, entry.recordId);
        }

        return allResults;
    }

    /**
     * Scan records with existing values
     *
     * @param existingValues Existing values to scan from
     * @return Remaining values that meet the criteria
     * @throws EntityException Exception while scanning entity records
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @SuppressWarnings("unchecked")
    public Map scan(Map existingValues) throws EntityException {
        final Map allResults = new CompatHashMap();

        final Iterator iterator = existingValues.keySet().iterator();
        IManagedEntity entity;
        Object reference;

        final List<QueryCriteria> allCritieria = new ArrayList<>();
        aggregateCritieria(criteria, allCritieria);

        while (iterator.hasNext()) {
            if (query.isTerminated())
                return allResults;

            reference = iterator.next();

            if (reference instanceof PartitionReference) {
                final RecordController recordController = this.getRecordControllerForPartition(((PartitionReference) reference).partition);
                entity = recordController.getWithReferenceId(((PartitionReference) reference).reference);
            } else {
                entity = records.getWithRecID((long) reference);
            }

            if (CompareUtil.meetsCriteria(allCritieria, criteria, entity, reference, getContext(), descriptor)) {
                allResults.put(reference, reference);
            } else {
                allResults.remove(reference);
            }

        }

        return allResults;
    }
}