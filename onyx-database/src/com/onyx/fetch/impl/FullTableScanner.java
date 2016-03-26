package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.record.RecordController;
import com.onyx.util.CompareUtil;
import com.onyx.util.ObjectUtil;
import gnu.trove.THashMap;

import java.util.*;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * It can either scan the entire table or a subset of index values
 */
public class FullTableScanner extends AbstractTableScanner implements TableScanner
{

    private static ObjectUtil reflection = ObjectUtil.getInstance();

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     */
    public FullTableScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
    }


    /**
     * Full Table Scan
     *
     * @return
     * @throws EntityException
     */
    public Map<Long, Long> scan() throws EntityException
    {
        final Map<Long, Long> allResults = new THashMap();

        // We need to do a full scan
        final Iterator<Map.Entry<Object, IManagedEntity>> iterator = records.entrySet().iterator();

        Map.Entry<Object, IManagedEntity> entry = null;
        Object attributeValue = null;

        while(iterator.hasNext())
        {
            if(query.isTerminated())
                return allResults;

            entry = iterator.next();

            attributeValue = reflection.getAttribute(fieldToGrab, entry.getValue());

            // Compare and add
            if (CompareUtil.compare(criteria.getValue(), attributeValue, criteria.getOperator()))
            {
                long recId = records.getRecID(entry.getKey());
                allResults.put(recId, recId);
            }

        }

        return allResults;
    }

    /**
     * Scan records with existing values
     *
     * @param existingValues
     * @return
     * @throws EntityException
     */
    public Map scan(Map existingValues) throws EntityException
    {
        final Map allResults = new THashMap();

        final Iterator<Long> iterator = existingValues.keySet().iterator();
        IManagedEntity entity = null;
        Object attributeValue = null;
        Object keyValue = null;

        while(iterator.hasNext())
        {
            if(query.isTerminated())
                return allResults;

            keyValue = iterator.next();

            if(keyValue instanceof PartitionReference)
            {
                final RecordController recordController = this.getRecordControllerForPartition(((PartitionReference)keyValue).partition);
                entity = recordController.getWithReferenceId(((PartitionReference) keyValue).reference);
            }
            else
            {
                entity = records.getWithRecID((long)keyValue);
            }

            // Ensure entity still exists
            if(entity == null)
            {
                continue;
            }

            // Get the attribute value
            attributeValue = reflection.getAttribute(fieldToGrab, entity);

            // Compare and add
            if (CompareUtil.compare(criteria.getValue(), attributeValue, criteria.getOperator()))
            {
                allResults.put(keyValue, keyValue);
            }

        }

        return allResults;
    }
}