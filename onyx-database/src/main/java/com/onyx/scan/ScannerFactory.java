package com.onyx.scan;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.OnyxException;
import com.onyx.interactors.scanner.TableScanner;
import com.onyx.interactors.scanner.impl.*;
import com.onyx.helpers.PartitionHelper;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.diskmap.MapBuilder;

/**
 * Created by timothy.osborn on 1/6/15.
 *
 * This class retrieves the correct scanner for the corresponding query criteria
 */
public class ScannerFactory
{

    private static ScannerFactory instance = null;

    private SchemaContext context;

    /**
     * Constructor, must send in the context
     */
    private ScannerFactory()
    {

    }

    /**
     * Get the instance
     *
     * @return Scanner Factory single instance
     */
    public synchronized static ScannerFactory getInstance(SchemaContext _context)
    {
        if (instance == null)
        {
            instance = new ScannerFactory();
        }
        instance.context = _context;
        return instance;
    }

    /**
     * Returns a full table scanner despite the criteria supporting an index or identifier.
     * The purpose for this is so we can force a full scan.  An instance where we want to do that
     * is if the first criteria has the .not() modifier set to true.  In that case, it is impossible
     * to determine index values because there is not an existing reference set to base on.
     *
     * @param criteria Criteria used to determine entity attribute
     * @param classToScan Entity class to scan
     * @param temporaryDataFile Query temporary data file to inject into the scanner
     * @param query Query definitions
     * @param persistenceManager Persistence manager
     * @return An implementation of a full table scanner
     * @throws OnyxException Attribute is either not supported or bad access
     *
     * @since 1.3.0
     */
    @SuppressWarnings("WeakerAccess")
    public TableScanner getFullTableScanner(QueryCriteria criteria, Class classToScan, MapBuilder temporaryDataFile, Query query, PersistenceManager persistenceManager) throws OnyxException
    {
        EntityDescriptor descriptor;

        if (query.getPartition() == QueryPartitionMode.ALL)
        {
            descriptor = context.getDescriptorForEntity(classToScan, "");
        }
        else
        {
            descriptor = context.getDescriptorForEntity(classToScan, query.getPartition());
        }
        final AttributeDescriptor attributeDescriptor = descriptor.getAttributes().get(criteria.getAttribute());
        if (attributeDescriptor != null)
        {
            if (PartitionHelper.hasPartitionField(query.getEntityType(), context))
            {
                return new PartitionFullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
            }
            else
            {
                return new FullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
            }
        }

        throw new AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE + " " + criteria.getAttribute());
    }

    /**
     * Returns the proper scanner for criteria
     * @param criteria Query Criteria
     * @param classToScan Entity class to scan
     * @return Scanner implementation
     */
    public TableScanner getScannerForQueryCriteria(QueryCriteria criteria, Class classToScan, MapBuilder temporaryDataFile, Query query, PersistenceManager persistenceManager) throws OnyxException
    {
        EntityDescriptor descriptor;

        if (query.getPartition() == QueryPartitionMode.ALL)
        {
            descriptor = context.getDescriptorForEntity(classToScan, "");
        }
        else
        {
            descriptor = context.getDescriptorForEntity(classToScan, query.getPartition());
        }

        final String attributeToScan = criteria.getAttribute();

        final String[] segments = attributeToScan.split("\\.");

        // This has a dot in it, it must be a relationship or a typo
        if (segments.length > 1)
        {
            return new RelationshipScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
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
                return new PartitionIdentifierScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
            }
            else
            {
                return new IdentifierScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
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
                return new PartitionIndexScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
            }
            else
            {
                return new IndexScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
            }
        }

        final AttributeDescriptor attributeDescriptor = descriptor.getAttributes().get(attributeToScan);
        if (attributeDescriptor != null)
        {
            if (PartitionHelper.hasPartitionField(query.getEntityType(), context))
            {
                return new PartitionFullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
            }
            else
            {
                return new FullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
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
