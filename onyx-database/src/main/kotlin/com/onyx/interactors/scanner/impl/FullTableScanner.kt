package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.QueryExecutionEvent
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.reportQueryExecution
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * It can either scan the entire table or a subset of index values
 */
open class FullTableScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    /**
     * Full Table Scan
     *
     * @return Map of identifiers.  The key is the partition reference and the value is the reference within file.
     * @throws OnyxException Query exception while trying to scan elements
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        context.reportQueryExecution(QueryExecutionEvent.FULL_TABLE_SCAN)
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality

        records.visitReferencesWhile { recordId, value ->
            val reference = Reference(partitionId, recordId)
            if(query.meetsCriteria(value, reference, context, descriptor)) {
                collector?.collect(reference, value)
                if (matching.size > maxCardinality)
                    throw MaxCardinalityExceededException(context.maxCardinality)
                if(collector == null)
                    matching.add(reference)
            }
            shouldContinueBoundedMutationScan()
        }

        return matching
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
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        context.reportQueryExecution(QueryExecutionEvent.FULL_TABLE_SCAN)
        val context = Contexts.get(contextId)!!
        val matching = HashSet<Reference>()
        val iterator = existingValues.iterator()
        while (iterator.hasNext() && shouldContinueBoundedMutationScan()) {
            val reference = iterator.next()
            val entity = reference.toManagedEntity(context, descriptor)
            val meetsCriteria = query.meetsCriteria(entity, reference, context, descriptor)
            if(meetsCriteria)
                collector?.collect(reference, entity)
            if (collector == null && meetsCriteria) matching += reference
        }
        return matching
    }

    /** Bounded update/delete scans can stop as soon as their collector is full. */
    protected fun shouldContinueBoundedMutationScan(): Boolean =
        !query.isUpdateOrDelete || query.maxResults <= 0 ||
            (collector?.references?.size ?: 0) < query.maxResults
}
