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
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.extension.meetsCriteria

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
        
        // Filter results based on the operator
        val filteredResults = filterResults(results, criteria)
        
        filteredResults.forEach { (id, score) ->
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
        // Use maxCardinality as maxCandidates
        val maxCandidates = context.maxCardinality - 1

        return indexInteractor.matchAll(queryValue, if (query.maxResults > 0) query.maxResults else maxCandidates, maxCandidates)
    }
    
    /**
     * Filter results based on the query criteria operator
     * @param results Results from vector similarity search
     * @param criteria Query criteria with operator and value
     * @return Filtered results
     */
    protected open fun filterResults(results: Map<Long, Any?>, criteria: QueryCriteria): Map<Long, Any?> {
        val context = Contexts.get(contextId)!!
        val operator = criteria.operator ?: return results
        
        // For operators that should use the default index interactor, return all results
        // as they will be filtered by the default index interactor
        return when (operator) {
            QueryCriteriaOperator.MATCHES,
            QueryCriteriaOperator.LIKE -> {
                // For MATCHES and LIKE operators, we use the vector similarity search directly
                // No additional filtering is needed
                results
            }
            QueryCriteriaOperator.CONTAINS,
            QueryCriteriaOperator.CONTAINS_IGNORE_CASE,
            QueryCriteriaOperator.NOT_CONTAINS,
            QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
            QueryCriteriaOperator.STARTS_WITH,
            QueryCriteriaOperator.NOT_STARTS_WITH,
            QueryCriteriaOperator.NOT_MATCHES,
            QueryCriteriaOperator.NOT_LIKE -> {
                // For these operators, we need to filter the results using the query comparison functionality
                results.filter { (recordId, _) ->
                    val reference = Reference(partitionId, recordId)
                    val entity = reference.toManagedEntity(context, descriptor)
                    query.meetsCriteria(entity, reference, context, descriptor)
                }
            }
            else -> results
        }
    }
}
