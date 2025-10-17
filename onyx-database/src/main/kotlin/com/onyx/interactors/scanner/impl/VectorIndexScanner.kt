package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.interactors.record.data.Reference
import com.onyx.exception.OnyxException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.scanner.TableScanner
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria

/**
 * Scanner for vector index matching operations
 */
open class VectorIndexScanner @Throws(OnyxException::class) constructor(
    criteria: QueryCriteria, 
    classToScan: Class<*>, 
    descriptor: EntityDescriptor, 
    query: Query, 
    context: SchemaContext, 
    persistenceManager: PersistenceManager
) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    private var indexInteractor: IndexInteractor = context.getIndexInteractor(descriptor.indexes[criteria.attribute]!!)

    /**
     * Scan indexes using vector similarity matching
     *
     * @return Indexes meeting criteria
     * @throws OnyxException Cannot scan index
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality

        // Use matchAll for vector similarity search
        val results = findMatches(criteria.value)
        
        results.forEach { (id, score) ->
            val reference = Reference(partitionId, id)
            collector?.collect(reference, reference.toManagedEntity(context, descriptor))
            if (matching.size > maxCardinality)
                throw MaxCardinalityExceededException(context.maxCardinality)
            if(collector == null)
                matching.add(reference)
        }

        return matching
    }

    /**
     * Scan indexes that are within the existing values
     *
     * @param existingValues Existing values to check
     * @return Existing values matching criteria
     * @throws OnyxException Cannot scan index
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val matching = scan()
        return existingValues.filterTo(HashSet()) {
            if(matching.contains(it)) {
                collector?.collect(it, it.toManagedEntity(context, descriptor))
                return@filterTo collector == null
            }
            return@filterTo false
        }
    }

    /**
     * Find matches using vector similarity search
     * @param queryValue Query value to find matches for
     * @return Map of record IDs to similarity scores
     */
    protected fun findMatches(queryValue: Any?): Map<Long, Any?> {
        val context = Contexts.get(contextId)!!
        // Get k from query limit if it exists, otherwise use maxCardinality
        val k = if (query.maxResults > 0) query.maxResults else context.maxCardinality
        // Use maxCardinality as maxCandidates
        val maxCandidates = context.maxCardinality
        
        return indexInteractor.matchAll(queryValue, k, maxCandidates)
    }
}
