@file:Suppress("ProtectedInFinal")

package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.extension.common.async
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.record.FullTextRecordInteractor
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import java.util.concurrent.Future

/**
 * Scanner for Lucene full-text record searches across partitions.
 */
class PartitionLuceneRecordScanner @Throws(OnyxException::class) constructor(
    criteria: QueryCriteria,
    classToScan: Class<*>,
    descriptor: EntityDescriptor,
    query: Query,
    context: SchemaContext,
    persistenceManager: PersistenceManager
) : LuceneRecordScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        if (query.partition != QueryPartitionMode.ALL) {
            return super.scan(existingValues)
        }

        val context = Contexts.get(contextId)!!
        val allMatchingReferences = scanAllPartitionsConcurrently(invokeCollector = false)
        val intersection = existingValues.intersect(allMatchingReferences)

        collector?.let { resultsCollector ->
            intersection.forEach { reference ->
                resultsCollector.collect(reference, reference.toManagedEntity(context, descriptor))
            }
        }

        return if (collector == null) intersection.toMutableSet() else mutableSetOf()
    }

    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!

        return if (query.partition == QueryPartitionMode.ALL) {
            scanAllPartitionsConcurrently(invokeCollector = true).toMutableSet()
        } else {
            val partitionId = context.getPartitionWithValue(query.entityType!!, query.partition)?.index ?: 0L
            if (partitionId == 0L) {
                return mutableSetOf()
            }

            val partitionDescriptor = context.getDescriptorForEntity(query.entityType, query.partition)
            val interactor = context.getRecordInteractor(partitionDescriptor) as? FullTextRecordInteractor
                ?: throw IllegalStateException(
                    "Full-text search requested, but the record interactor does not support it. " +
                        "Ensure the onyx-lucene-index module is available and the entity uses Lucene records."
                )
            scanSinglePartition(interactor, partitionId)
        }
    }

    private fun scanAllPartitionsConcurrently(invokeCollector: Boolean): Set<Reference> {
        val context = Contexts.get(contextId)!!
        val partitions = context.getAllPartitions(query.entityType!!)

        val scanFutures: List<Future<Set<Reference>>> = partitions.map { entry ->
            async {
                val partitionDescriptor = context.getDescriptorForEntity(query.entityType, entry.value)
                val interactor = context.getRecordInteractor(partitionDescriptor) as? FullTextRecordInteractor
                    ?: throw IllegalStateException(
                        "Full-text search requested, but the record interactor does not support it. " +
                            "Ensure the onyx-lucene-index module is available and the entity uses Lucene records."
                    )

                if (invokeCollector) {
                    scanSinglePartition(interactor, entry.index)
                } else {
                    val results = findMatches(criteria.value?.toString().orEmpty(), interactor)
                    results.map { (recordId, _) -> Reference(entry.index, recordId) }.toMutableSet()
                }
            }
        }

        return scanFutures.flatMap { it.get() }.toSet()
    }

    @Throws(OnyxException::class)
    private fun scanSinglePartition(interactor: FullTextRecordInteractor, partitionId: Long): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality
        val matchingReferences = HashSet<Reference>()

        val results = findMatches(criteria.value?.toString().orEmpty(), interactor)
        results.forEach { (recordId, _) ->
            if (matchingReferences.size > maxCardinality) {
                throw MaxCardinalityExceededException(context.maxCardinality)
            }
            val reference = Reference(partitionId, recordId)
            collector?.collect(reference, reference.toManagedEntity(context, descriptor))
            if (collector == null) {
                matchingReferences.add(reference)
            }
        }

        return matchingReferences
    }

    private fun findMatches(queryText: String, interactor: FullTextRecordInteractor): Map<Long, Float> {
        val context = Contexts.get(contextId)!!
        val limit = if (query.maxResults > 0) query.maxResults else context.maxCardinality - 1
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) return emptyMap()
        return interactor.searchAll(trimmed, limit)
    }
}
