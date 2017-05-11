package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.exception.EntityException;
import com.onyx.helpers.PartitionContext;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.util.OffsetField;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * This contains the abstract inforamtion for a table scanner.
 */
abstract class AbstractTableScanner extends PartitionContext
{
    final ExecutorService executorService = Executors.newFixedThreadPool(8);

    @SuppressWarnings("WeakerAccess")
    protected final QueryCriteria criteria;
    @SuppressWarnings("unused")
    protected final Class classToScan;
    @SuppressWarnings("WeakerAccess")
    protected final EntityDescriptor descriptor;

    @SuppressWarnings("WeakerAccess unused")
    OffsetField fieldToGrab = null;

    @SuppressWarnings("WeakerAccess")
    protected DiskMap<Object, IManagedEntity> records = null;
    @SuppressWarnings("WeakerAccess")
    protected MapBuilder temporaryDataFile = null;
    @SuppressWarnings("WeakerAccess")
    protected final Query query;
    @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
    protected PersistenceManager persistenceManager;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     */
    @SuppressWarnings({"unchecked", "RedundantThrows"})
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
            fieldToGrab = criteria.getAttributeDescriptor().getField();
        }

        this.persistenceManager = persistenceManager;
    }


}
