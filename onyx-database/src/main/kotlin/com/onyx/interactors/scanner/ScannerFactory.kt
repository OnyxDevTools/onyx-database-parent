package com.onyx.interactors.scanner

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.AttributeMissingException
import com.onyx.exception.OnyxException
import com.onyx.interactors.scanner.impl.*
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.diskmap.factory.DiskMapFactory

/**
 * Created by timothy.osborn on 1/6/15.
 *
 * This class retrieves the correct scanner for the corresponding query criteria
 */
object ScannerFactory {

    /**
     * Returns a full table scanner despite the criteria supporting an index or identifier.
     * The purpose for this is so we can force a full scan.  An instance where we want to do that
     * is if the first criteria has the .not() modifier set to true.  In that case, it is impossible
     * to determine index values because there is not an existing reference set to base on.
     *
     * @param context Context contains database resources
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
    @Throws(OnyxException::class)
    fun getFullTableScanner(context:SchemaContext, criteria: QueryCriteria, classToScan: Class<*>, temporaryDataFile: DiskMapFactory, query: Query, persistenceManager: PersistenceManager): TableScanner {
        val descriptor: EntityDescriptor = if (query.partition === QueryPartitionMode.ALL) {
            context.getDescriptorForEntity(classToScan, "")
        } else {
            context.getDescriptorForEntity(classToScan, query.partition)
        }

        val attributeDescriptor = descriptor.attributes[criteria.attribute]
        if (attributeDescriptor != null) {
            return if (descriptor.hasPartition) {
                PartitionFullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            } else {
                FullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            }
        }

        throw AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE + " " + criteria.attribute)
    }

    /**
     * Returns the proper scanner for criteria
     * @param context Context contains database resources
     * @param criteria Query Criteria
     * @param classToScan Entity class to scan
     * @return Scanner implementation
     */
    @Throws(OnyxException::class)
    fun getScannerForQueryCriteria(context:SchemaContext, criteria: QueryCriteria, classToScan: Class<*>, temporaryDataFile: DiskMapFactory, query: Query, persistenceManager: PersistenceManager): TableScanner {

        val descriptor: EntityDescriptor = if (query.partition === QueryPartitionMode.ALL) {
            context.getDescriptorForEntity(classToScan, "")
        } else {
            context.getDescriptorForEntity(classToScan, query.partition)
        }

        val attributeToScan = criteria.attribute
        val segments = attributeToScan!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        // This has a dot in it, it must be a relationship or a typo
        if (segments.size > 1) {
            return RelationshipScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
        }

        if(criteria.flip) {
            return if(descriptor.hasPartition)
                PartitionReferenceScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            else
                ReferenceScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
        }

        // Identifiers criteria must be either an equal or in so that it can make exact matches
        if (descriptor.identifier!!.name == attributeToScan && criteria.operator!!.isIndexed) {
            return if (descriptor.hasPartition) {
                PartitionIdentifierScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            } else {
                IdentifierScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            }
        }

        // Indexes must be either an equal or in so that it can make exact matches
        val indexDescriptor = descriptor.indexes[attributeToScan]
        if (indexDescriptor != null && criteria.operator!!.isIndexed) {
            return if (descriptor.hasPartition) {
                PartitionIndexScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            } else {
                IndexScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            }
        }

        val attributeDescriptor = descriptor.attributes[attributeToScan]
        if (attributeDescriptor != null) {
            return if (descriptor.hasPartition) {
                PartitionFullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            } else {
                FullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager)
            }
        }

        throw AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE + " " + attributeToScan)
    }
}
