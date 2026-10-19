package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.record.descriptorForReference
import com.onyx.exception.OnyxException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.scanner.TableScanner
import com.onyx.interactors.index.IndexInteractor
import com.onyx.interactors.query.impl.collectors.IndexedEntityQueryCollector
import com.onyx.lang.map.SortedLongNullMap
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan index values for given criteria
 */
open class IndexScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner, RangeScanner {

    private var indexInteractor: IndexInteractor = context.getIndexInteractor(descriptor.indexes[criteria.attribute]!!)
    override var isBetween: Boolean = false
    override var rangeFrom: Any? = null
    override var rangeTo: Any? = null
    override var fromOperator:QueryCriteriaOperator? = null
    override var toOperator:QueryCriteriaOperator? = null

    /**
     * Scan indexes
     *
     * @return Indexes meeting criteria
     * @throws OnyxException Cannot scan index
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> = scan(false)

    /**
     * Scan indexes
     *
     * @return Indexes meeting criteria
     * @throws OnyxException Cannot scan index
     */
    fun scan(includesExisting:Boolean = false): MutableSet<Reference> {
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality

        // If it is an in clause
        if (criteria.value is List<*>) {
            (criteria.value as List<*>).forEach { it ->
                find(it).forEach {
                    if(!includesExisting)
                        collectReference(it)
                    if (matching.size > maxCardinality)
                        throw MaxCardinalityExceededException(context.maxCardinality)
                    if(collector == null)
                        matching.add(it)
                }
            }
        } else {
            find(criteria.value).forEach {
                if(!includesExisting)
                    collectReference(it)
                if (matching.size > maxCardinality)
                    throw MaxCardinalityExceededException(context.maxCardinality)
                if(collector == null)
                    matching.add(it)
            }
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
        scanExistingPostings(existingValues)?.let { return it }
        val matching = scan(true)
        return existingValues.filterTo(HashSet()) {
            if(matching.contains(it)) {
                collectReference(it)
                return@filterTo collector == null
            }
            return@filterTo false
        }
    }

    /** Probe a small incoming domain instead of enumerating a broad equality posting list. */
    protected fun scanExistingPostings(existingValues: Set<Reference>): MutableSet<Reference>? {
        if (isBetween || criteria.isNot || criteria.flip || criteria.isOr ||
            criteria.operator != QueryCriteriaOperator.EQUAL && criteria.operator != QueryCriteriaOperator.IN
        ) return null
        val index = descriptor.indexes[criteria.attribute] ?: return null
        val type = index.type
        if (index.indexType != IndexType.DEFAULT || type.isArray ||
            Iterable::class.java.isAssignableFrom(type) || Map::class.java.isAssignableFrom(type) ||
            Pair::class.java.isAssignableFrom(type)
        ) return null
        val values = (criteria.value as? List<*> ?: listOf(criteria.value)).distinct()
        if (existingValues.isEmpty() || values.isEmpty()) return hashSetOf()
        // Without selectivity statistics, cap point work so broad intersections can still scan.
        if (existingValues.size.toLong() * values.size > MAX_EXISTING_POSTING_PROBES) return null

        val matching = HashSet<Reference>()
        val indexesByPartition = HashMap<Long, IndexInteractor>()
        try {
            for (reference in existingValues) {
                if (query.isTerminated) return hashSetOf()
                val interactor = indexesByPartition.getOrPut(reference.partition) {
                    val source = context.descriptorForReference(reference, query.entityType!!, descriptor)
                    context.getIndexInteractor(source.indexes.getValue(requireNotNull(criteria.attribute)))
                }
                if (values.any { interactor.containsExactPosting(it, reference.reference) }) {
                    matching.add(reference)
                    if (matching.size > context.maxCardinality) {
                        throw MaxCardinalityExceededException(context.maxCardinality)
                    }
                }
            }
        } catch (_: UnsupportedOperationException) {
            // Finish all probes before collection so custom indexes can retry without duplicates.
            return null
        }
        matching.forEach(::collectReference)
        return if (collector == null) matching else hashSetOf()
    }

    /** Exact entity pages can collect known matches without decoding discarded records. */
    protected fun collectReference(reference: Reference) {
        val target = collector ?: return
        if (target is IndexedEntityQueryCollector) {
            target.collectReference(reference)
        } else {
            target.collect(reference, reference.toManagedEntity(context, descriptor))
        }
    }

    /**
     * Find all references within an index matching the value for this query criteria
     * @param indexValue Index value to find references for
     * @return List of Partition References
     *
     * @since 2.0.0
     */
    protected fun find(indexValue:Any?, interactor: IndexInteractor = indexInteractor, partition: Long = partitionId):List<Reference> {
        val references = if(isBetween) {
            interactor.findAllBetween(rangeFrom, fromOperator === QueryCriteriaOperator.GREATER_THAN_EQUAL, rangeTo, toOperator === QueryCriteriaOperator.LESS_THAN_EQUAL)
        } else {
            when {
                criteria.operator === QueryCriteriaOperator.GREATER_THAN -> interactor.findAllAbove(indexValue, false)
                criteria.operator === QueryCriteriaOperator.GREATER_THAN_EQUAL -> interactor.findAllAbove(indexValue, true)
                criteria.operator === QueryCriteriaOperator.LESS_THAN -> interactor.findAllBelow(indexValue, false)
                criteria.operator === QueryCriteriaOperator.LESS_THAN_EQUAL -> interactor.findAllBelow(indexValue, true)
                criteria.operator === QueryCriteriaOperator.BETWEEN ->
                    interactor.findAllBetween((indexValue as? Pair<*,*>)?.first,
                        true,
                        (indexValue as? Pair<*,*>)?.second,
                        true)
                criteria.operator === QueryCriteriaOperator.NOT_BETWEEN -> {
                    val pair = indexValue as? Pair<*,*>
                    if (pair != null) {
                        interactor.findAllBelow(pair.first, false)
                            .union(interactor.findAllAbove(pair.second, false))
                    } else {
                        emptySet()
                    }
                }
                criteria.operator === QueryCriteriaOperator.LIKE || criteria.operator === QueryCriteriaOperator.MATCHES -> {
                    val maxCandidates = context.maxCardinality - 1
                    val limit = if (query.maxResults > 0) query.maxResults else maxCandidates
                    val matched = interactor.matchAll(indexValue, limit, maxCandidates)
                    collectScores(matched, partition)
                    matched.keys
                }
                else -> {
                    val matched = interactor.findAll(indexValue)
                    collectScores(matched, partition)
                    matched.keys
                }
            }
        }

        return references.map {
            Reference(partition, it)
        }
    }

    private fun collectScores(matches: Map<Long, *>, partition: Long) {
        // Native scalar snapshots contain only implicit null values; do not allocate entries
        // merely to discover that none of their postings carry a search score.
        if (matches is SortedLongNullMap || matches.isEmpty()) return

        val numericScores = matches.entries.mapNotNull { (recordId, rawScore) ->
            val score = (rawScore as? Number)?.toFloat() ?: return@mapNotNull null
            Reference(partition, recordId) to score
        }

        if (numericScores.isEmpty()) return

        synchronized(query) {
            val scores = (query.fullTextScores?.toMutableMap() ?: hashMapOf())
            numericScores.forEach { (reference, score) ->
                val previous = scores[reference]
                if (previous == null || score > previous) {
                    scores[reference] = score
                }
            }
            query.fullTextScores = scores
        }
    }

    private companion object {
        const val MAX_EXISTING_POSTING_PROBES = 4_096L
    }
}
