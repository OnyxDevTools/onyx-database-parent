package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.record.FullTextRecordInteractor
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.resolveFullTextQuery

/**
 * Scanner for Lucene full-text record searches.
 */
open class LuceneRecordScanner @Throws(OnyxException::class) constructor(
    criteria: QueryCriteria,
    classToScan: Class<*>,
    descriptor: EntityDescriptor,
    query: Query,
    context: SchemaContext,
    persistenceManager: PersistenceManager
) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    private val fullTextInteractor: FullTextRecordInteractor = context.getRecordInteractor(descriptor) as? FullTextRecordInteractor
        ?: throw IllegalStateException(
            "Full-text search requested, but the record interactor does not support it. " +
                "Ensure the onyx-lucene-index module is available and the entity uses Lucene records."
        )

    /**
     * Scan records using Lucene full-text search.
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality
        val limit = resolveLimit(maxCardinality)
        val fullTextQuery = resolveFullTextQuery(criteria.value)
        val queryText = fullTextQuery?.queryText?.trim().orEmpty()
        if (queryText.isEmpty()) return mutableSetOf()

        val results = fullTextInteractor.searchAll(queryText, limit)
        val minScore = fullTextQuery?.minScore
        val matching = HashSet<Reference>()

        results.forEach { (recordId, score) ->
            if (minScore != null && score < minScore) return@forEach
            if (matching.size > maxCardinality) {
                throw MaxCardinalityExceededException(context.maxCardinality)
            }
            val reference = Reference(partitionId, recordId)
            collector?.collect(reference, reference.toManagedEntity(context, descriptor))
            if (collector == null) {
                matching.add(reference)
            }
        }

        return matching
    }

    /**
     * Scan records with existing values.
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val matching = scan()

        return existingValues.filterTo(HashSet()) { reference ->
            if (matching.contains(reference)) {
                collector?.collect(reference, reference.toManagedEntity(context, descriptor))
                return@filterTo collector == null
            }
            false
        }
    }

    private fun resolveLimit(maxCardinality: Int): Int {
        return if (query.maxResults > 0) {
            query.maxResults
        } else {
            maxCardinality - 1
        }
    }
}
