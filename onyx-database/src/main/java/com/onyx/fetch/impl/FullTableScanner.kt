package com.onyx.fetch.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.MapBuilder
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.fetch.PartitionReference
import com.onyx.fetch.TableScanner
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * It can either scan the entire table or a subset of index values
 */
open class FullTableScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria<*>, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: MapBuilder, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    /**
     * Full Table Scan
     *
     * @return Map of identifiers.  The key is the partition reference and the value is the reference within file.
     * @throws OnyxException Query exception while trying to scan elements
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @Throws(OnyxException::class)
    override fun scan(): Map<PartitionReference, PartitionReference> {
        val allResults = HashMap<PartitionReference, PartitionReference>()
        val context = Contexts.get(contextId)!!

        records.referenceSet().filter {
            val entity = records.getWithRecID(it.recordId)
            val reference = PartitionReference(partitionId, it.recordId)
            query.meetsCriteria(entity, reference, context, descriptor)
        }.forEach {
            val reference = PartitionReference(partitionId, it.recordId)
            allResults.put(reference, reference)
        }

        return allResults
    }

    /**
     * Scan records with existing values
     *
     * @param existingValues Existing values to scan from
     * @return Remaining values that meet the criteria
     * @throws OnyxException Exception while scanning entity records
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: Map<PartitionReference, PartitionReference>): Map<PartitionReference, PartitionReference> {
        val context = Contexts.get(contextId)!!
        return existingValues.filter {
            val entity = it.key.toManagedEntity(context, descriptor)
            query.meetsCriteria(entity, it.key, context, descriptor)
        }
    }
}