package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.exception.SearchEmbeddingUnavailableException
import com.onyx.extension.meetsCriteria
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.index.impl.FingerprintIndexInteractor
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.QueryExecutionEvent
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.reportQueryExecution
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.BoundedLexicalSearchQuery
import com.onyx.persistence.query.resolveVectorSearchQuery
import com.onyx.persistence.query.resolveHnswSearchQuery
import com.onyx.persistence.query.DEFAULT_HNSW_EF_SEARCH
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.persistence.query.SearchMatch
import com.onyx.persistence.query.SearchMode
import com.onyx.persistence.query.SearchQuery
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.resolveSearchQuery
import com.onyx.vector.FingerprintQueryPlan
import com.onyx.vector.FingerprintQueryExecutor
import com.onyx.vector.FingerprintQueryPlanner
import com.onyx.vector.SearchEmbedding

/**
 * Scanner for the managed vector fingerprint index.
 *
 * Vector-managed entities keep every structured route in one hidden fingerprint index. The
 * fingerprint plan only supplies a conservative candidate set. Positive candidates are
 * authoritatively verified, while negative predicates subtract verified positive matches from
 * an indexed domain without hydrating every row merely to reject it.
 */
open class VectorIndexScanner @Throws(OnyxException::class) constructor(
    criteria: QueryCriteria,
    classToScan: Class<*>,
    descriptor: EntityDescriptor,
    query: Query,
    context: SchemaContext,
    persistenceManager: PersistenceManager
) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    private val indexInteractor: FingerprintIndexInteractor =
        (context.getIndexInteractor(indexDescriptor(descriptor)) as FingerprintIndexInteractor).also {
            context.reportQueryExecution(QueryExecutionEvent.VECTOR_INDEX_INTERACTOR_LOOKUP)
        }

    /** Exact-verification already loaded these matches; retain them for the final collector. */
    private val verifiedEntities = HashMap<Reference, IManagedEntity>()

    /**
     * A leading negative predicate can be bounded by a selective AND child before its indexed
     * domain complement is evaluated. This makes conjunction cost independent of source order.
     */
    internal fun selectiveConjunctForDomain(): QueryCriteria? {
        val leafPlan = FingerprintQueryPlanner(descriptor).compile(criteria.leafCopy())
        if (leafPlan !is FingerprintQueryPlan.Complement) return null

        for (child in criteria.subCriteria) {
            if (child.flip) continue
            if (child.isOr) break
            if (!child.isAnd) continue
            val childPlan = FingerprintQueryPlanner(descriptor).compile(child.leafCopy())
            if (childPlan !== FingerprintQueryPlan.Universe && childPlan !is FingerprintQueryPlan.Complement) {
                return child
            }
        }
        return null
    }

    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        context.reportQueryExecution(QueryExecutionEvent.VECTOR_INDEX_SCAN)
        return finishScan(scanDescriptor(descriptor, partitionId)) { descriptor }
    }

    @Throws(OnyxException::class)
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        context.reportQueryExecution(QueryExecutionEvent.VECTOR_INDEX_SCAN)
        val matching = scanDescriptor(descriptor, partitionId, existingValues)
        return finishScan(matching) { descriptor }
    }

    /** Returns exact matches for one concrete partition without invoking the collector. */
    protected fun scanDescriptor(
        targetDescriptor: EntityDescriptor,
        targetPartitionId: Long,
        existingValues: Set<Reference>? = null
    ): LinkedHashSet<Reference> = scanFingerprint(
        targetDescriptor,
        targetPartitionId,
        existingValues
    )

    /** Applies cardinality and collector behavior after all candidate verification is complete. */
    protected fun finishScan(
        references: LinkedHashSet<Reference>,
        descriptorForReference: (Reference) -> EntityDescriptor
    ): MutableSet<Reference> {
        val currentContext = Contexts.get(contextId)!!
        if (references.size > currentContext.maxCardinality) {
            throw MaxCardinalityExceededException(currentContext.maxCardinality)
        }
        val activeCollector = collector ?: return references
        if (verifiedEntities.isEmpty()) {
            references.forEach { reference ->
                val exactDescriptor = descriptorForReference(reference)
                activeCollector.collect(
                    reference,
                    reference.toManagedEntity(currentContext, exactDescriptor)
                )
            }
            return LinkedHashSet()
        }
        references.forEach { reference ->
            val exactDescriptor = descriptorForReference(reference)
            val entity = verifiedEntities.remove(reference)
                ?: reference.toManagedEntity(currentContext, exactDescriptor)
            activeCollector.collect(reference, entity)
        }
        return LinkedHashSet()
    }

    private fun scanFingerprint(
        targetDescriptor: EntityDescriptor,
        targetPartitionId: Long,
        existingValues: Set<Reference>?
    ): LinkedHashSet<Reference> {
        context.reportQueryExecution(QueryExecutionEvent.VECTOR_FINGERPRINT_SCAN)
        val currentContext = Contexts.get(contextId)!!
        val interactor = if (targetDescriptor === descriptor) {
            indexInteractor
        } else {
            currentContext.getIndexInteractor(indexDescriptor(targetDescriptor)) as FingerprintIndexInteractor
        }
        val leafCriteria = criteria.leafCopy()
        val restrictedIds = existingValues?.asSequence()
            ?.filter { it.partition == targetPartitionId }
            ?.mapTo(LinkedHashSet(), Reference::reference)

        if (leafCriteria.operator == QueryCriteriaOperator.SEARCH) {
            val cachedAdmission = query.vectorSearchMatches?.get(criteria)
            if (cachedAdmission != null) {
                return cachedAdmission.asSequence()
                    .filter { reference ->
                        reference.partition == targetPartitionId &&
                            (restrictedIds == null || reference.reference in restrictedIds)
                    }
                    .toCollection(LinkedHashSet())
            }
            val scores = executeSearch(
                resolveSearchQuery(leafCriteria.value),
                targetDescriptor,
                interactor,
                restrictedIds,
            )
            val referenceScores = LinkedHashMap<Reference, Float>(scores.size)
            val matches = LinkedHashSet<Reference>(scores.size)
            scores.forEach { (recordId, score) ->
                val reference = Reference(targetPartitionId, recordId)
                matches += reference
                referenceScores[reference] = score
            }
            mergeScores(referenceScores)
            return matches
        }

        if (leafCriteria.operator == QueryCriteriaOperator.HNSW_CANDIDATES) {
            val hnswQuery = resolveHnswSearchQuery(leafCriteria.value)
            val scores = interactor.findHnswCandidates(hnswQuery, restrictedIds)
            val referenceScores = LinkedHashMap<Reference, Float>(scores.size)
            val matches = LinkedHashSet<Reference>(scores.size)
            scores.forEach { (recordId, score) ->
                val reference = Reference(targetPartitionId, recordId)
                matches += reference
                referenceScores[reference] = score
            }
            mergeScores(referenceScores)
            return matches
        }

        val executor = FingerprintQueryExecutor(targetDescriptor, interactor) { searchPlan ->
            val searchValue = if (searchPlan.operator == QueryCriteriaOperator.SEARCH_CANDIDATES) {
                BoundedLexicalSearchQuery(
                    requireNotNull(resolveVectorSearchQuery(searchPlan.value)) {
                        "SEARCH_CANDIDATES requires a lexical VectorSearchQuery"
                    }
                )
            } else {
                searchPlan.value
            }
            interactor.matchAll(
                searchValue,
                limit = currentContext.maxCardinality,
                maxCandidates = currentContext.maxCardinality
            ).keys
        }
        val plan = executor.plan(leafCriteria)

        // Probing a bounded existing AND-domain avoids materializing a much broader global
        // posting. Account for every feature arm: a small domain multiplied by a very wide IN,
        // range, or text plan can still produce too many random B-tree membership probes.
        val candidateRestriction = restrictedIds?.takeIf {
            val featureProbes = plan.featureProbeCount()
            it.isEmpty() || (
                featureProbes in 1..RESTRICTED_CANDIDATE_PROBE_BUDGET &&
                    it.size.toLong() <= RESTRICTED_CANDIDATE_PROBE_BUDGET / featureProbes
            )
        }

        fun candidateIds(candidatePlan: FingerprintQueryPlan): Set<Long> =
            candidateRestriction?.let { executor.candidateIds(candidatePlan, it) }
                ?: executor.candidateIds(candidatePlan)

        fun verify(
            candidateIds: Set<Long>,
            exactCriteria: QueryCriteria,
            retainVerifiedEntities: Boolean = false,
            mergeVerifiedScores: Boolean = true
        ): LinkedHashSet<Long> {
            val exactQuery = Query(requireNotNull(query.entityType), exactCriteria)
            val verified = LinkedHashSet<Long>()
            candidateIds.forEach { recordId ->
                if (restrictedIds != null && recordId !in restrictedIds) return@forEach
                val reference = Reference(targetPartitionId, recordId)
                val entity = reference.toManagedEntity(currentContext, targetDescriptor)
                if (
                    entity != null &&
                    exactQuery.meetsCriteria(entity, reference, currentContext, targetDescriptor)
                ) {
                    verified += recordId
                    if (retainVerifiedEntities && collector != null) {
                        verifiedEntities[reference] = entity
                    }
                }
            }
            if (mergeVerifiedScores) exactQuery.fullTextScores?.let(::mergeScores)
            return verified
        }

        val matching: LinkedHashSet<Reference> = when (plan) {
            is FingerprintQueryPlan.Complement -> {
                check(plan.operand !== FingerprintQueryPlan.Universe) {
                    "${leafCriteria.attribute} ${leafCriteria.operator} cannot be routed by the fingerprint index"
                }
                val positiveCriteria = QueryCriteria(
                    requireNotNull(leafCriteria.attribute),
                    requireNotNull(leafCriteria.operator).inverse,
                    leafCriteria.value
                )
                val positiveMatches = verify(
                    candidateIds(plan.operand),
                    positiveCriteria,
                    mergeVerifiedScores = false
                )
                val domain = restrictedIds ?: interactor.allRecordIds()
                LinkedHashSet<Reference>(domain.size).apply {
                    domain.forEach { recordId ->
                        if (recordId !in positiveMatches) {
                            add(Reference(targetPartitionId, recordId))
                        }
                    }
                }
            }
            FingerprintQueryPlan.Universe -> error(
                "${leafCriteria.attribute} ${leafCriteria.operator} cannot be routed by the fingerprint index"
            )
            else -> verify(candidateIds(plan), leafCriteria, retainVerifiedEntities = true)
                .mapTo(LinkedHashSet()) { recordId -> Reference(targetPartitionId, recordId) }
        }

        if (
            plan !is FingerprintQueryPlan.Complement &&
            leafCriteria.attribute != Query.FULL_TEXT_ATTRIBUTE &&
            leafCriteria.operator in setOf(QueryCriteriaOperator.LIKE, QueryCriteriaOperator.MATCHES)
        ) {
            mergeUniformScores(matching, 1.0f)
        }
        return matching
    }

    protected fun executeSearch(
        search: SearchQuery,
        targetDescriptor: EntityDescriptor,
        interactor: FingerprintIndexInteractor,
        restrictedIds: Set<Long>?,
        preparedSemanticEmbedding: SearchEmbedding? = null,
    ): LinkedHashMap<Long, Float> {
        val lexicalBudget = when (search.mode) {
            SearchMode.LEXICAL -> search.maxCandidates
            SearchMode.SEMANTIC -> 0
            SearchMode.HYBRID -> search.maxCandidates / 2
        }
        val semanticBudget = when (search.mode) {
            SearchMode.LEXICAL -> 0
            SearchMode.SEMANTIC -> search.maxCandidates
            SearchMode.HYBRID -> search.maxCandidates - lexicalBudget
        }

        val lexicalScores = if (lexicalBudget == 0) {
            emptyMap()
        } else {
            val lexicalQuery = VectorSearchQuery(
                text = search.text,
                maxCandidates = lexicalBudget,
                requireAllTerms = search.match == SearchMatch.ALL,
            )
            interactor.matchAll(
                BoundedLexicalSearchQuery(lexicalQuery),
                limit = lexicalBudget,
                maxCandidates = lexicalBudget,
            ).mapNotNull { (recordId, score) ->
                val numericScore = (score as? Number)?.toFloat() ?: return@mapNotNull null
                recordId to numericScore.coerceIn(0f, 1f)
            }.toMap(LinkedHashMap())
        }

        val semanticScores = if (semanticBudget == 0) {
            emptyMap()
        } else {
            val embedding = preparedSemanticEmbedding ?: run {
                val provider = persistenceManager.searchEmbeddingProvider
                    ?: throw SearchEmbeddingUnavailableException(
                        "${search.mode.name.lowercase()} search requires a SearchEmbeddingProvider " +
                            "configured on the database server"
                    )
                if (!provider.supports(targetDescriptor.entityClass)) {
                    throw SearchEmbeddingUnavailableException(
                        "SearchEmbeddingProvider does not support ${targetDescriptor.entityClass.name}",
                    )
                }
                provider.embed(search.text, targetDescriptor.entityClass)
            }
            interactor.findHnswCandidates(
                HnswSearchQuery(
                    calibrationId = embedding.calibrationId,
                    vector = embedding.vector,
                    maxCandidates = semanticBudget,
                    efSearch = maxOf(DEFAULT_HNSW_EF_SEARCH, semanticBudget),
                ),
                restrictedIds,
            ).mapValuesTo(LinkedHashMap()) { (_, score) ->
                ((score.coerceIn(-1f, 1f) + 1f) / 2f)
            }
        }

        val candidateIds = LinkedHashSet<Long>(lexicalScores.size + semanticScores.size).apply {
            addAll(lexicalScores.keys)
            addAll(semanticScores.keys)
        }
        return candidateIds.asSequence()
            .filter { restrictedIds == null || it in restrictedIds }
            .map { recordId ->
                val score = when (search.mode) {
                    SearchMode.LEXICAL -> lexicalScores.getValue(recordId)
                    SearchMode.SEMANTIC -> semanticScores.getValue(recordId)
                    SearchMode.HYBRID -> maxOf(
                        lexicalScores[recordId] ?: Float.NEGATIVE_INFINITY,
                        semanticScores[recordId] ?: Float.NEGATIVE_INFINITY,
                    )
                }
                recordId to score
            }
            .filter { (_, score) -> search.minScore == null || score >= search.minScore }
            .sortedWith(compareByDescending<Pair<Long, Float>> { it.second }.thenBy { it.first })
            .take(search.maxCandidates)
            .toMap(LinkedHashMap())
    }

    protected fun mergeScores(matches: Map<Reference, Float>) {
        if (matches.isEmpty()) return
        synchronized(query) {
            val scores = query.fullTextScores?.toMutableMap() ?: hashMapOf()
            matches.forEach { (reference, score) ->
                val previous = scores[reference]
                if (previous == null || score > previous) scores[reference] = score
            }
            query.fullTextScores = scores
        }
    }

    /** Adds one scalar LIKE/MATCHES score in a single linear pass. */
    private fun mergeUniformScores(matches: Collection<Reference>, score: Float) {
        if (matches.isEmpty()) return
        synchronized(query) {
            val scores = query.fullTextScores?.toMutableMap() ?: HashMap(matches.size)
            matches.forEach { reference ->
                val previous = scores[reference]
                if (previous == null || score > previous) scores[reference] = score
            }
            query.fullTextScores = scores
        }
    }

    private fun indexDescriptor(targetDescriptor: EntityDescriptor) =
        requireNotNull(targetDescriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD])

    private fun QueryCriteria.leafCopy(): QueryCriteria = QueryCriteria(
        requireNotNull(attribute),
        requireNotNull(operator),
        value
    ).also { it.isNot = isNot }

    private fun FingerprintQueryPlan.featureProbeCount(): Long = when (this) {
        FingerprintQueryPlan.Empty -> 0L
        FingerprintQueryPlan.Universe,
        is FingerprintQueryPlan.Search -> Long.MAX_VALUE
        is FingerprintQueryPlan.Feature -> 1L
        is FingerprintQueryPlan.Complement -> operand.featureProbeCount()
        is FingerprintQueryPlan.AllOf -> operands.featureProbeCount()
        is FingerprintQueryPlan.AnyOf -> operands.featureProbeCount()
    }

    private fun List<FingerprintQueryPlan>.featureProbeCount(): Long {
        var count = 0L
        for (operand in this) {
            val operandCount = operand.featureProbeCount()
            if (operandCount == Long.MAX_VALUE) return Long.MAX_VALUE
            count += operandCount
            if (count > RESTRICTED_CANDIDATE_PROBE_BUDGET) return count
        }
        return count
    }

    private companion object {
        const val RESTRICTED_CANDIDATE_PROBE_BUDGET = 32_768L
    }
}
