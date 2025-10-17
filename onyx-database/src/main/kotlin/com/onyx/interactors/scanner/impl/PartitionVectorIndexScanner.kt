@file:Suppress("ProtectedInFinal")

package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.extension.common.async
import com.onyx.extension.toManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.extension.meetsCriteria
import java.util.concurrent.Future
import kotlin.collections.HashSet

/**
 * A table scanner that executes vector index queries across multiple database partitions.
 *
 * This scanner is specialized for queries where `QueryPartitionMode` is set to `ALL`,
 * enabling it to perform parallel searches across all partitions for a given entity type.
 * For queries targeting a single partition, it delegates to the parent `VectorIndexScanner`.
 *
 * The scanner can operate in two modes depending on whether a `collector` is present:
 * 1.  **Collection Mode**: If a collector is provided, matching references are passed to it,
 * and the scan methods return an empty set.
 * 2.  **Return Mode**: If no collector is provided, the scan methods return a set of
 * all matching references.
 *
 * @param criteria The query criteria containing the attribute and value for the index search.
 * @param classToScan The entity class being queried.
 * @param descriptor The descriptor for the entity being scanned.
 * @param query The query object containing details like partitioning mode and result limits.
 * @param context The schema context providing access to database resources.
 * @param persistenceManager The manager for persistence operations.
 */
class PartitionVectorIndexScanner @Throws(OnyxException::class) constructor(
    criteria: QueryCriteria,
    classToScan: Class<*>,
    descriptor: EntityDescriptor,
    query: Query,
    context: SchemaContext,
    persistenceManager: PersistenceManager
) : VectorIndexScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    /**
     * Scans all partitions for matching vector data and intersects the results with an existing set of references.
     *
     * If the query's partition mode is not `ALL`, this method delegates to the parent implementation.
     * Otherwise, it fetches results from all partitions concurrently and finds the common elements
     * with the `existingValues` set.
     *
     * @param existingValues A set of references to filter the scan results against.
     * @return A mutable set containing the intersection of scan results and `existingValues` if no collector is used.
     * Returns an empty set if a collector is used.
     */
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        if (query.partition != QueryPartitionMode.ALL) {
            return super.scan(existingValues)
        }

        val context = Contexts.get(contextId)!!

        // First, perform the concurrent scan to gather all possible matching references without collection.
        val allMatchingReferences = scanAllPartitionsConcurrently(invokeCollector = false)

        // Find the intersection between the concurrently fetched results and the pre-existing values.
        val intersection = existingValues.intersect(allMatchingReferences)

        // If a collector is present, process each reference in the intersection.
        collector?.let { resultsCollector ->
            intersection.forEach { reference ->
                resultsCollector.collect(reference, reference.toManagedEntity(context, descriptor))
            }
        }

        // Return the intersection only if no collector was used; otherwise, return an empty set.
        return if (collector == null) intersection.toMutableSet() else mutableSetOf()
    }

    /**
     * Performs a full table scan for vector data across one or all partitions.
     *
     * If `query.partition` is `ALL`, it scans all partitions concurrently. Otherwise, it identifies
     * the single target partition and scans only its index.
     *
     * @return A set of matching references if no collector is used, or an empty set if a collector is used.
     * @throws OnyxException if scanning a partition fails.
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!

        return if (query.partition == QueryPartitionMode.ALL) {
            // Scan all partitions and invoke the collector directly within each concurrent task.
            scanAllPartitionsConcurrently(invokeCollector = true).toMutableSet()
        } else {
            // Logic for scanning a single, specified partition.
            val partitionId = context.getPartitionWithValue(query.entityType!!, query.partition)?.index ?: 0L
            if (partitionId == 0L) {
                return mutableSetOf()
            }

            val partitionDescriptor = context.getDescriptorForEntity(query.entityType, query.partition)
            val indexInteractor = context.getIndexInteractor(partitionDescriptor.indexes[criteria.attribute]!!)
            scanSinglePartition(indexInteractor, partitionId)
        }
    }

    /**
     * Scans all partitions for the entity type concurrently and aggregates the results.
     *
     * @param invokeCollector A boolean flag to determine if the collector should be called for each match.
     * If `false`, the method simply returns the found references.
     * @return A set of all matching [Reference] objects found across all partitions.
     */
    private fun scanAllPartitionsConcurrently(invokeCollector: Boolean): Set<Reference> {
        val context = Contexts.get(contextId)!!
        val partitions = context.getAllPartitions(query.entityType!!)

        val scanFutures: List<Future<Set<Reference>>> = partitions.map { entry ->
            async {
                val partitionDescriptor = context.getDescriptorForEntity(query.entityType, entry.value)
                val indexInteractor = context.getIndexInteractor(partitionDescriptor.indexes[criteria.attribute]!!)

                if (invokeCollector) {
                    scanSinglePartition(indexInteractor, entry.index)
                } else {
                    // When not collecting, just get the raw matches to be processed later.
                    val results = findMatches(criteria.value, indexInteractor)
                    val filteredResults = filterResults(results, criteria)
                    filteredResults.map { (recordId, _) -> Reference(entry.index, recordId) }
                        .toMutableSet()
                }
            }
        }

        // Wait for all async tasks to complete and flatten their results into a single set.
        return scanFutures.flatMap { it.get() }.toSet()
    }

    /**
     * Scans the indexes of a single partition for matching vector data.
     *
     * @param indexInteractor The index interactor for the target attribute.
     * @param partitionId The ID of the partition to scan.
     * @return A set of matching references found within the partition if no collector is used.
     * @throws OnyxException if the maximum cardinality is exceeded.
     */
    @Throws(OnyxException::class)
    private fun scanSinglePartition(indexInteractor: IndexInteractor, partitionId: Long): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality
        val matchingReferences = HashSet<Reference>()

        val results = findMatches(criteria.value, indexInteractor)
        
        // Filter results based on the operator
        val filteredResults = filterResults(results, criteria)

        filteredResults.forEach { (recordId, _) ->
            if (matchingReferences.size > maxCardinality) {
                throw MaxCardinalityExceededException(context.maxCardinality)
            }
            val reference = Reference(partitionId, recordId)

            collector?.collect(reference, reference.toManagedEntity(context, descriptor))

            // Only add to the return set if a collector is not being used.
            if (collector == null) {
                matchingReferences.add(reference)
            }
        }

        return matchingReferences
    }

    /**
     * Executes a vector similarity search using the provided index interactor.
     *
     * It determines the number of results (`k`) and candidates to consider based on the
     * query parameters and the context's maximum cardinality setting.
     *
     * @param queryValue The value to search for (e.g., a query string or vector).
     * @param interactor The [IndexInteractor] to use for the search.
     * @return A map of matching record IDs to their similarity scores.
     */
    protected fun findMatches(queryValue: Any?, interactor: IndexInteractor): Map<Long, Any?> {
        val context = Contexts.get(contextId)!!

        // Determine 'k' (number of nearest neighbors) from the query limit or fallback to max cardinality.
        val k = if (query.maxResults > 0) query.maxResults else context.maxCardinality

        // The maximum number of candidates to evaluate during the search.
        val maxCandidates = context.maxCardinality

        return interactor.matchAll(queryValue, k, maxCandidates)
    }
    
    /**
     * Filter results based on the query criteria operator
     * @param results Results from vector similarity search
     * @param criteria Query criteria with operator and value
     * @return Filtered results
     */
    override fun filterResults(results: Map<Long, Any?>, criteria: QueryCriteria): Map<Long, Any?> {
        val context = Contexts.get(contextId)!!
        val operator = criteria.operator ?: return results

        // For operators that should use the default index interactor, return all results
        // as they will be filtered by the default index interactor
        return when (operator) {
            com.onyx.persistence.query.QueryCriteriaOperator.LIKE -> {
                // For MATCHES and LIKE operators, we use the vector similarity search directly
                // No additional filtering is needed
                results
            }
            com.onyx.persistence.query.QueryCriteriaOperator.MATCHES,
            com.onyx.persistence.query.QueryCriteriaOperator.CONTAINS,
            com.onyx.persistence.query.QueryCriteriaOperator.CONTAINS_IGNORE_CASE,
            com.onyx.persistence.query.QueryCriteriaOperator.NOT_CONTAINS,
            com.onyx.persistence.query.QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
            com.onyx.persistence.query.QueryCriteriaOperator.STARTS_WITH,
            com.onyx.persistence.query.QueryCriteriaOperator.NOT_STARTS_WITH,
            com.onyx.persistence.query.QueryCriteriaOperator.NOT_MATCHES,
            com.onyx.persistence.query.QueryCriteriaOperator.NOT_LIKE -> {
                // For these operators, we need to filter the results using the query comparison functionality
                results.filter { (recordId, _) ->
                    // Use the partitionId from the outer scope
                    val reference = Reference(this@PartitionVectorIndexScanner.partitionId, recordId)
                    val entity = reference.toManagedEntity(context, descriptor)
                    query.meetsCriteria(entity, reference, context, descriptor)
                }
            }
            else -> results
        }
    }
}
