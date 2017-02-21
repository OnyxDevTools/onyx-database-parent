package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.helpers.PartitionContext;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by timothy.osborn on 1/3/15.
 *
 * This contains the abstract inforamtion for a table scanner.
 */
abstract class AbstractTableScanner extends PartitionContext
{
    ExecutorService executorService = Executors.newFixedThreadPool(8);

    protected QueryCriteria criteria;
    protected Class classToScan;
    protected EntityDescriptor descriptor;
    OffsetField fieldToGrab = null;

    protected DiskMap<Object, IManagedEntity> records = null;
    protected MapBuilder temporaryDataFile = null;
    protected Query query;
    protected PersistenceManager persistenceManager;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     */
    @SuppressWarnings("unchecked")
    AbstractTableScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(context, descriptor);
        this.criteria = criteria;
        this.classToScan = classToScan;
        this.descriptor = descriptor;
        this.query = query;

        // Get the data file
        final MapBuilder dataFile = context.getDataFile(descriptor);
        records = (DiskMap)dataFile.getHashMap(descriptor.getClazz().getName(), descriptor.getIdentifier().getLoadFactor());

        this.temporaryDataFile = temporaryDataFile;

        // Ensure it is not a relationship
        if(!criteria.getAttribute().contains("."))
        {
            // Get the reflection field to grab the key to compare
            fieldToGrab = ReflectionUtil.getOffsetField(classToScan, criteria.getAttribute());
        }

        this.persistenceManager = persistenceManager;
    }

}
