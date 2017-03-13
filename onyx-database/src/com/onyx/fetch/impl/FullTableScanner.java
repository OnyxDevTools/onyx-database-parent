package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.util.map.CompatHashMap;
import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.record.RecordController;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.util.CompareUtil;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * It can either scan the entire table or a subset of index values
 */
public class FullTableScanner extends AbstractTableScanner implements TableScanner
{

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     */
    public FullTableScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
    }


    /**
     * Full Table Scan
     *
     * @return Map of identifiers.  The key is the partition reference and the value is the reference within file.
     * @throws EntityException Query exception while trying to scan elements
     */
    @SuppressWarnings("unckecked")
    public Map<Long, Long> scan() throws EntityException
    {
        final Map<Long, Long> allResults = new CompatHashMap<>();

        // We need to do a full scan
        final Iterator iterator = records.referenceSet().iterator();

        SkipListNode entry;
        Object attributeValue;

        while (iterator.hasNext()) {
            if (query.isTerminated())
                return allResults;

            entry = (SkipListNode)iterator.next();

            attributeValue = records.getAttributeWithRecID(fieldToGrab, entry);

            // Compare and add
            if (CompareUtil.compare(criteria.getValue(), attributeValue, criteria.getOperator())) {
                long recId = entry.position;
                allResults.put(recId, recId);
            }

        }

        return allResults;
    }

    /**
     * Scan records with existing values
     *
     * @param existingValues Existing values to scan from
     * @throws EntityException Exception while scanning entity records
     * @return Remaining values that meet the criteria
     */
    @SuppressWarnings("unchecked")
    public Map scan(Map existingValues) throws EntityException
    {
        final Map allResults = new CompatHashMap();

        final Iterator iterator = existingValues.keySet().iterator();
        Object entityAttribute;
        Object keyValue;

        while(iterator.hasNext())
        {
            if(query.isTerminated())
                return allResults;

            keyValue = iterator.next();

            if(keyValue instanceof PartitionReference)
            {
                final RecordController recordController = this.getRecordControllerForPartition(((PartitionReference)keyValue).partition);
                entityAttribute = recordController.getAttributeWithReferenceId(fieldToGrab, ((PartitionReference) keyValue).reference);
            }
            else
            {
                entityAttribute = records.getAttributeWithRecID(fieldToGrab, (long)keyValue);
            }

            // Compare and add
            if (CompareUtil.compare(criteria.getValue(), entityAttribute, criteria.getOperator()))
            {
                allResults.put(keyValue, keyValue);
            }

        }

        return allResults;
    }
}