package com.onyx.fetch;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityException;
import com.onyx.exception.SingletonException;
import com.onyx.fetch.impl.*;
import com.onyx.helpers.PartitionHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.structure.MapBuilder;

/**
 * Created by timothy.osborn on 1/6/15.
 */
public class ScannerFactory
{

    private static ScannerFactory instance = null;

    private SchemaContext context;

    /**
     * Constructor, must send in the context
     */
    public ScannerFactory()
    {

    }

    /**
     * Get the instance
     *
     * @return
     * @throws com.onyx.exception.SingletonException
     */
    public synchronized static ScannerFactory getInstance(SchemaContext _context) throws SingletonException
    {
        if (instance == null)
        {
            instance = new ScannerFactory();
        }
        instance.context = _context;
        return instance;
    }

    /**
     * Returns the proper
     * @param criteria
     * @param classToScan
     * @return
     * @throws EntityException
     */
    public TableScanner getScannerForQueryCriteria(QueryCriteria criteria, Class classToScan, MapBuilder temporaryDataFile, Query query, PersistenceManager persistenceManager) throws EntityException
    {
        final IManagedEntity entity = EntityDescriptor.createNewEntity(classToScan);
        EntityDescriptor descriptor = null;

        if (query.getPartition() == QueryPartitionMode.ALL)
        {
            descriptor = context.getDescriptorForEntity(entity, "");
        }
        else
        {
            descriptor = context.getDescriptorForEntity(entity, query.getPartition());
        }

        final String attributeToScan = criteria.getAttribute();

        final String[] segments = attributeToScan.split("\\.");

        // This has a dot in it, it must be a relationship or a typo
        if (segments.length > 1)
        {
            final RelationshipScanner relationshipScanner = new RelationshipScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
            return relationshipScanner;
        }

        // Identifiers criteria must be either an equal or in so that it can make exact matches
        if (descriptor.getIdentifier().getName().equals(attributeToScan) && (criteria.getOperator() == QueryCriteriaOperator.EQUAL
                || criteria.getOperator() == QueryCriteriaOperator.IN
                || criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN
                || criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL
                || criteria.getOperator() == QueryCriteriaOperator.LESS_THAN
                || criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL))
        {
            if (PartitionHelper.hasPartitionField(query.getEntityType(), context))
            {
                final PartitionIdentifierScanner indexScanner = new PartitionIdentifierScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
                return indexScanner;
            }
            else
            {
                final IdentifierScanner indexScanner = new IdentifierScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
                return indexScanner;
            }
        }

        // Indexes must be either an equal or in so that it can make exact matches
        final IndexDescriptor indexDescriptor = descriptor.getIndexes().get(attributeToScan);
        if (indexDescriptor != null && (criteria.getOperator() == QueryCriteriaOperator.EQUAL
                || criteria.getOperator() == QueryCriteriaOperator.IN
                || criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN
                || criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL
                || criteria.getOperator() == QueryCriteriaOperator.LESS_THAN
                || criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL))
        {
            if (PartitionHelper.hasPartitionField(query.getEntityType(), context))
            {
                final PartitionIndexScanner indexScanner = new PartitionIndexScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
                return indexScanner;
            }
            else
            {
                final IndexScanner indexScanner = new IndexScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
                return indexScanner;
            }
        }

        final AttributeDescriptor attributeDescriptor = descriptor.getAttributes().get(attributeToScan);
        if (attributeDescriptor != null)
        {
            if (PartitionHelper.hasPartitionField(query.getEntityType(), context))
            {
                final PartitionFullTableScanner fullTableScanner = new PartitionFullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
                return fullTableScanner;

            }
            else
            {
                final FullTableScanner fullTableScanner = new FullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
                return fullTableScanner;
            }
        }

        throw new AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE + " " + attributeToScan);
    }

    /**
     * Resets the context and singleton instance
     */
    public synchronized void reset()
    {
        context = null;
        instance = null;
    }

}
