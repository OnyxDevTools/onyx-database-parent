package com.onyx.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.IndexPostingMap
import com.onyx.exception.OnyxException
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.context.FingerprintSearchWork
import com.onyx.persistence.context.HnswSearchWork
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.QueryExecutionEvent
import com.onyx.persistence.context.reportFingerprintSearchWork
import com.onyx.persistence.context.reportHnswSearchWork
import com.onyx.persistence.context.reportQueryExecution
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.persistence.query.BoundedLexicalSearchQuery
import com.onyx.persistence.query.resolveVectorSearchQuery
import com.onyx.vector.FeatureFingerprint
import com.onyx.vector.PreparedVectorRepresentation
import com.onyx.vector.SemanticVectorSignature
import com.onyx.vector.VectorEntityEncoder
import com.onyx.vector.VectorManagedConfiguration
import com.onyx.vector.VectorRepresentation
import com.onyx.vector.VectorRepresentationCodec
import com.onyx.vector.VectorSearchEvaluator
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Persistent sparse-feature and semantic-fingerprint index.
 *
 * The index contains ordinary `(routingKey, recordId)` posting trees plus an optional persistent
 * HNSW graph over normalized signed-int8 vectors. It never stores full-precision embeddings.
 * Multiword structured fingerprints are verified against the record representation so a
 * routing-key collision can add work but can never decide predicate truth.
 */
class FingerprintIndexInteractor @Throws(OnyxException::class) constructor(
    private val descriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : IndexInteractor {
    private val contextReference = WeakReference(context)
    private val context: SchemaContext
        get() = requireNotNull(contextReference.get()) { "Schema context is no longer available" }
    private val dataFile
        get() = context.getDataFile(descriptor)
    private val configuration by lazy { VectorManagedConfiguration.forClass(descriptor.entityClass) }
    private val mapBaseName: String
        get() = descriptor.entityClass.name + indexDescriptor.name + "_fingerprint_v${indexDescriptor.encodingVersion}"

    private val featurePostings: IndexPostingMap
        get() = dataFile.getIndexMap(Long::class.java, "${mapBaseName}_features")
    private val bucketPostings: IndexPostingMap
        get() = dataFile.getIndexMap(Long::class.java, "${mapBaseName}_buckets")
    private val cellPostings: IndexPostingMap
        get() = dataFile.getIndexMap(Long::class.java, "${mapBaseName}_cells")
    private val bandPostings: IndexPostingMap
        get() = dataFile.getIndexMap(Long::class.java, "${mapBaseName}_bands")
    private val universePostings: IndexPostingMap
        get() = dataFile.getIndexMap(Long::class.java, "${mapBaseName}_universe")
    private val hnswNodes: DiskMap<Long, ByteArray>
        get() = dataFile.getHashMap(Long::class.java, "${mapBaseName}_hnsw_nodes")
    private val hnswMetadata: DiskMap<Long, ByteArray>
        get() = dataFile.getHashMap(Long::class.java, "${mapBaseName}_hnsw_metadata")
    private val hnswIndex by lazy {
        PersistentHnswIndex(hnswNodes, hnswMetadata)
    }
    private val records: DiskMap<Any, IManagedEntity>
        get() = dataFile.getHashMap(descriptor.identifier!!.type, descriptor.entityClass.name)

    @Synchronized
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        require(oldReferenceId <= 0L) {
            "The previous fingerprint representation is required for an update"
        }
        save(null, indexValue, oldReferenceId, newReferenceId)
    }

    @Synchronized
    override fun save(oldIndexValue: Any?, indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        val oldRepresentation = prepare(oldIndexValue)
        val representation = prepare(indexValue)
        representation?.representation?.validateConfiguration()

        if (oldReferenceId > 0L && oldReferenceId != newReferenceId) {
            removeRoutes(oldRepresentation, oldReferenceId)
            universePostings.remove(UNIVERSE_KEY, oldReferenceId)
        } else if (oldReferenceId > 0L) {
            updateRoutes(oldRepresentation, representation, newReferenceId)
        }

        if (newReferenceId > 0L) {
            if (oldReferenceId <= 0L || oldReferenceId != newReferenceId) addRoutes(representation, newReferenceId)
            universePostings.add(UNIVERSE_KEY, newReferenceId)
        }
    }

    @Synchronized
    override fun delete(reference: Long) {
        val representation = representation(reference)?.let(PreparedVectorRepresentation::fromRepresentation)
        removeRoutes(representation, reference)
        universePostings.remove(UNIVERSE_KEY, reference)
    }

    @Synchronized
    override fun delete(indexValue: Any?, reference: Long) {
        removeRoutes(prepare(indexValue), reference)
        universePostings.remove(UNIVERSE_KEY, reference)
    }

    /** Returns records containing the complete multiword feature fingerprint. */
    fun findFeature(feature: FeatureFingerprint): Set<Long> {
        return findFeatureCandidates(feature).filterTo(LinkedHashSet()) { recordId ->
            representation(recordId)?.containsFeature(feature) == true
        }
    }

    /**
     * Returns the raw route-key posting for a feature.
     *
     * A route key is deliberately only 64 bits, so this is a conservative candidate superset,
     * never an exact predicate result. [FingerprintQueryExecutor] performs set algebra on these
     * inexpensive IDs and [com.onyx.interactors.scanner.impl.VectorIndexScanner] authoritatively
     * verifies the surviving records before returning or subtracting them.
     */
    internal fun findFeatureCandidates(feature: FeatureFingerprint): Set<Long> {
        validateFeature(feature)
        context.reportQueryExecution(QueryExecutionEvent.FINGERPRINT_FEATURE_LOOKUP)
        return postings(featurePostings, feature.routeKey)
    }

    /** Retains only IDs whose raw posting contains [feature], without hydrating their records. */
    internal fun retainFeatureCandidates(
        feature: FeatureFingerprint,
        candidates: MutableSet<Long>
    ) {
        validateFeature(feature)
        context.reportQueryExecution(QueryExecutionEvent.FINGERPRINT_FEATURE_LOOKUP)
        if (candidates.size > DIRECT_FEATURE_PROBE_LIMIT) {
            candidates.retainAll(postings(featurePostings, feature.routeKey))
            return
        }
        val iterator = candidates.iterator()
        while (iterator.hasNext()) {
            if (!featurePostings.contains(feature.routeKey, iterator.next())) iterator.remove()
        }
    }

    fun findAnyFeature(features: Collection<FeatureFingerprint>): Set<Long> {
        val result = LinkedHashSet<Long>()
        features.forEach { result.addAll(findFeature(it)) }
        return result
    }

    fun findAllFeatures(features: Collection<FeatureFingerprint>): Set<Long> {
        val iterator = features.iterator()
        if (!iterator.hasNext()) return emptySet()
        val result = findFeature(iterator.next()).toMutableSet()
        while (iterator.hasNext() && result.isNotEmpty()) result.retainAll(findFeature(iterator.next()))
        return result
    }

    fun allRecordIds(): Set<Long> {
        context.reportQueryExecution(QueryExecutionEvent.FINGERPRINT_DOMAIN_LOOKUP)
        return postings(universePostings, UNIVERSE_KEY)
    }

    /** Returns native HNSW candidates without hydrating entity records during graph traversal. */
    fun findHnswCandidates(
        query: HnswSearchQuery,
        allowedRecordIds: Set<Long>? = null,
    ): Map<Long, Float> {
        context.reportQueryExecution(QueryExecutionEvent.HNSW_SEARCH)
        val result = hnswIndex.search(query, allowedRecordIds)
        context.reportHnswSearchWork(
            HnswSearchWork(
                efSearch = query.efSearch,
                maxCandidates = query.maxCandidates,
                distanceEvaluations = result.distanceEvaluations,
                upperLayerDistanceEvaluations = result.upperLayerDistanceEvaluations,
                resultCount = result.scores.size,
                exactFilteredScan = result.exactFilteredScan,
                concurrentSearchesObserved = result.concurrentSearchesObserved,
            )
        )
        return result.scores
    }

    /** Explicit maintenance diagnostic; prompt-time queries never invoke this graph walk. */
    fun validateHnswGraph(calibrationId: Long): Long = hnswIndex.validateGraph(calibrationId)

    /** Aggregate bounded work from the most recent HNSW node removal in this store. */
    fun lastHnswRemovalWork(): HnswRemovalWork = hnswIndex.lastRemovalWork()

    /** Reads one compact graph node rather than hydrating its entity payload. */
    fun hnswNodeLevel(recordId: Long): Int? = hnswIndex.nodeLevel(recordId)

    /** Reads one compact graph node rather than hydrating its entity payload. */
    fun hnswNodeDegree(recordId: Long, layer: Int = 0): Int? = hnswIndex.nodeDegree(recordId, layer)

    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> {
        context.reportQueryExecution(QueryExecutionEvent.FINGERPRINT_MATCH_ALL)
        val boundedLexical = indexValue is BoundedLexicalSearchQuery
        val query = resolveVectorSearchQuery(
            if (boundedLexical) indexValue.query else indexValue
        ) ?: return emptyMap()
        val requestedLimit = limit.coerceAtLeast(0)
        if (requestedLimit == 0) return emptyMap()
        if (query.semantic == null && !boundedLexical) {
            // Lexical features are exact sparse postings. Do not silently turn an exact text
            // predicate into a top-k predicate before compound criteria are evaluated.
            val candidateLimit = minOf(maxCandidates.coerceAtLeast(1), context.maxCardinality)
            return scoreCandidates(lexicalCandidates(query), query, requestedLimit, candidateLimit)
        }

        val candidateLimit = minOf(
            maxCandidates.coerceAtLeast(1),
            query.maxCandidates,
            context.maxCardinality
        )
        val budget = CandidateWorkBudget(
            candidateLimit,
            postingVisitsPerCandidate = if (boundedLexical) 1 else POSTING_VISITS_PER_CANDIDATE
        )
        return try {
            if (query.semantic == null) {
                collectBoundedLexicalCandidates(query, budget, budget.remainingCandidateCapacity)
                return scoreCandidates(
                    budget.candidates,
                    query,
                    requestedLimit,
                    candidateLimit
                ) {
                    budget.evaluatedCandidateCount++
                }
            }
            if (!query.text.isNullOrBlank()) {
                collectBoundedLexicalCandidates(
                    query,
                    budget,
                    maxOf(1, candidateLimit / HYBRID_LEXICAL_RESERVE_DIVISOR)
                )
            }
            collectBoundedSemanticCandidates(query, budget)
            if (!query.text.isNullOrBlank() && budget.hasCandidateCapacity) {
                // If semantic routes were sparse, let the independent lexical channel consume the
                // unused capacity. Re-reading a short prefix is charged to the same posting budget.
                collectBoundedLexicalCandidates(query, budget, budget.remainingCandidateCapacity)
            }
            scoreCandidates(budget.candidates, query, requestedLimit, candidateLimit) {
                budget.evaluatedCandidateCount++
            }
        } finally {
            context.reportFingerprintSearchWork(budget.snapshot())
        }
    }

    private fun scoreCandidates(
        candidates: Collection<Long>,
        query: VectorSearchQuery,
        requestedLimit: Int,
        candidateLimit: Int,
        beforeEvaluation: () -> Unit = {}
    ): Map<Long, Any?> {
        if (candidates.isEmpty()) return emptyMap()
        val scored = candidates.mapNotNull { recordId ->
            beforeEvaluation()
            val representation = representation(recordId) ?: return@mapNotNull null
            VectorSearchEvaluator.evaluate(representation, descriptor, query)?.let { recordId to it }
        }.sortedWith(compareByDescending<Pair<Long, Float>> { it.second }.thenBy { it.first })
            .take(candidateLimit)
            .take(requestedLimit)

        return LinkedHashMap<Long, Any?>(scored.size).apply {
            scored.forEach { (recordId, score) -> put(recordId, score) }
        }
    }

    override fun findAll(indexValue: Any?): Map<Long, Any?> = matchAll(indexValue, 50, 1_000)

    override fun findAllAbove(indexValue: Any?, includeValue: Boolean): Set<Long> =
        throw UnsupportedOperationException(
            "Fingerprint range lookup requires an attribute-aware FingerprintQueryPlan"
        )

    override fun findAllBetween(
        fromValue: Any?,
        includeFromValue: Boolean,
        toValue: Any?,
        includeToValue: Boolean
    ): Set<Long> = throw UnsupportedOperationException(
        "Fingerprint range lookup requires an attribute-aware FingerprintQueryPlan"
    )

    override fun findAllBelow(indexValue: Any?, includeValue: Boolean): Set<Long> =
        throw UnsupportedOperationException(
            "Fingerprint range lookup requires an attribute-aware FingerprintQueryPlan"
        )

    override fun findAllValues(): Set<Any> {
        val values = LinkedHashSet<Any>()
        featurePostings.forEachDistinctValue(values::add)
        return values
    }

    override fun rebuild() {
        val recordInteractor = context.getRecordInteractor(descriptor)
        synchronized(recordInteractor) {
            synchronized(this) { rebuildWhileRecordWritesAreExcluded() }
        }
    }

    /** Lock order is record interactor -> graph -> record B-tree, matching normal saves. */
    private fun rebuildWhileRecordWritesAreExcluded() {
        hnswIndex.beginRebuild()
        var rebuildComplete = false
        try {
            clearPostingRoutes()
            // Walk the live B-tree one entry at a time. The previous implementation retained
            // every hydrated entity in an ArrayList before rebuilding, so schema migration
            // required O(table size) heap in addition to the index. Updating an existing entry
            // only replaces its record payload; its key and leaf position remain stable.
            records.forEachMutableReference record@ { recordId, entry ->
                val entity = entry.value as? VectorManagedEntity ?: return@record
                if (recordId <= 0L) return@record
                entity.prepareVectorRepresentation(descriptor)
                entry.setValue(entity)
                val representation =
                    entity.consumePreparedVectorIndexValue() as PreparedVectorRepresentation
                representation.representation.validateConfiguration()
                addRoutes(representation, recordId, rebuildingHnsw = true)
                universePostings.add(UNIVERSE_KEY, recordId)
            }
            hnswIndex.completeRebuild()
            rebuildComplete = true
        } finally {
            if (!rebuildComplete) hnswIndex.abortRebuild()
        }
    }

    @Synchronized
    override fun clear() {
        clearPostingRoutes()
        hnswIndex.clear()
    }

    private fun clearPostingRoutes() {
        featurePostings.clear()
        bucketPostings.clear()
        cellPostings.clear()
        bandPostings.clear()
        universePostings.clear()
    }

    override fun shutdown() = Unit

    override fun deleteResources() = clear()

    private fun lexicalCandidates(query: VectorSearchQuery): Set<Long> {
        val text = query.text?.trim()?.takeIf { it.isNotEmpty() } ?: return emptySet()
        val terms = VectorEntityEncoder.tokens(text).distinct()
        if (terms.isEmpty()) return emptySet()
        val features = terms.map {
            VectorEntityEncoder.fingerprint(
                descriptor,
                configuration,
                "text/term:${VectorEntityEncoder.escape(it)}"
            )
        }
        val candidates = if (query.requireAllTerms) {
            findFeatureCandidates(features.first()).toMutableSet().apply {
                features.drop(1).forEach { retainFeatureCandidates(it, this) }
            }
        } else {
            LinkedHashSet<Long>().apply {
                features.forEach { addAll(findFeatureCandidates(it)) }
            }
        }
        return candidates
    }

    private fun collectBoundedLexicalCandidates(
        query: VectorSearchQuery,
        budget: CandidateWorkBudget,
        maxNewCandidates: Int
    ) {
        if (maxNewCandidates <= 0 || !budget.canContinue) return
        val text = query.text?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val terms = VectorEntityEncoder.tokens(text).distinct().take(MAX_HYBRID_QUERY_TERMS)
        if (terms.isEmpty()) return
        val features = terms.map {
            VectorEntityEncoder.fingerprint(
                descriptor,
                configuration,
                "text/term:${VectorEntityEncoder.escape(it)}"
            )
        }
        if (query.requireAllTerms) {
            val requiredRoutes = features.drop(1).map(FeatureFingerprint::routeKey)
            budget.collectRoute(
                featurePostings,
                features.first().routeKey,
                maxNewCandidates
            ) { recordId ->
                requiredRoutes.all { route ->
                    budget.consumeMembershipProbe() && featurePostings.contains(route, recordId)
                }
            }
            return
        }

        collectRoutesFairly(
            features.map { CandidateRoute(featurePostings, it.routeKey) },
            budget,
            maxNewCandidates
        )
    }

    private fun collectBoundedSemanticCandidates(
        query: VectorSearchQuery,
        budget: CandidateWorkBudget
    ) {
        val semantic = requireNotNull(query.semantic)
        val exactBucket = listOf(
            CandidateRoute(bucketPostings, bucketRoute(semantic.calibrationId, semantic.bucketId))
        )
        val bands = semantic.bands.mapIndexed { index, band ->
            CandidateRoute(
                bandPostings,
                bandRoute(semantic.calibrationId, semantic.bitCount, index, band)
            )
        }
        val nearbyBuckets = VectorSearchEvaluator
            .semanticNearbyBucketIds(semantic, query.nearbyBucketRadius)
            .asSequence()
            .filter { it != semantic.bucketId }
            .distinct()
            .map { bucket ->
                CandidateRoute(bucketPostings, bucketRoute(semantic.calibrationId, bucket))
            }
            .toList()
        val cells = semantic.cells.mapIndexed { index, cell ->
            CandidateRoute(cellPostings, cellRoute(semantic.calibrationId, index, cell))
        }

        // Give every useful route family an admission share before a precise route can consume the
        // remainder. This avoids broad cells or one large band monopolizing the bounded set.
        collectRoutesFairly(exactBucket, budget, fractionOfCandidateLimit(budget, 1, 4))
        collectRoutesFairly(bands, budget, fractionOfCandidateLimit(budget, 2, 5))
        collectRoutesFairly(nearbyBuckets, budget, fractionOfCandidateLimit(budget, 1, 5))
        collectRoutesFairly(cells, budget, fractionOfCandidateLimit(budget, 3, 20))

        if (budget.hasCandidateCapacity && budget.canContinue) {
            collectRoutesFairly(
                exactBucket + bands + nearbyBuckets + cells,
                budget,
                budget.remainingCandidateCapacity
            )
        }
    }

    private fun collectRoutesFairly(
        routes: List<CandidateRoute>,
        budget: CandidateWorkBudget,
        maxNewCandidates: Int
    ) {
        if (routes.isEmpty() || maxNewCandidates <= 0 || !budget.canContinue) return
        val targetSize = minOf(
            budget.candidateLimit.toLong(),
            budget.candidates.size.toLong() + maxNewCandidates.toLong()
        ).toInt()
        routes.forEachIndexed { index, route ->
            if (budget.candidates.size >= targetSize || !budget.canContinue) return
            val remainingRoutes = routes.size - index
            val remainingCandidates = targetSize - budget.candidates.size
            val routeShare = maxOf(
                1,
                ((remainingCandidates.toLong() + remainingRoutes.toLong() - 1L) / remainingRoutes.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            )
            budget.collectRoute(route.postings, route.key, routeShare)
        }
    }

    private fun fractionOfCandidateLimit(
        budget: CandidateWorkBudget,
        numerator: Int,
        denominator: Int
    ): Int = maxOf(
        1,
        (budget.candidateLimit.toLong() * numerator.toLong() / denominator.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    )

    private data class CandidateRoute(
        val postings: IndexPostingMap,
        val key: Long
    )

    private class CandidateWorkBudget(
        val candidateLimit: Int,
        postingVisitsPerCandidate: Int = POSTING_VISITS_PER_CANDIDATE
    ) {
        val candidates = LinkedHashSet<Long>(minOf(candidateLimit, INITIAL_CANDIDATE_CAPACITY))
        val postingVisitLimit = saturatingMultiply(candidateLimit, postingVisitsPerCandidate)
        private val membershipProbeLimit =
            saturatingMultiply(candidateLimit, POSTING_VISITS_PER_CANDIDATE)
        val routeLookupLimit = minOf(
            MAX_ROUTE_LOOKUPS,
            maxOf(MIN_ROUTE_LOOKUPS, saturatingAdd(candidateLimit, MIN_ROUTE_LOOKUPS))
        )
        var postingVisits: Int = 0
            private set
        var routeLookups: Int = 0
            private set
        var membershipProbes: Int = 0
            private set
        var evaluatedCandidateCount: Int = 0

        val remainingCandidateCapacity: Int
            get() = candidateLimit - candidates.size
        val hasCandidateCapacity: Boolean
            get() = candidates.size < candidateLimit
        private val canVisitCurrentRoute: Boolean
            get() = hasCandidateCapacity && postingVisits < postingVisitLimit
        val canContinue: Boolean
            get() = canVisitCurrentRoute && routeLookups < routeLookupLimit

        fun collectRoute(
            postings: IndexPostingMap,
            key: Long,
            maxNewCandidates: Int,
            accept: (Long) -> Boolean = { true }
        ) {
            if (maxNewCandidates <= 0 || !canContinue) return
            routeLookups++
            var added = 0
            postings.visitRecordIdsInRange(
                key,
                Long.MIN_VALUE,
                true,
                key,
                Long.MAX_VALUE,
                true,
                postingVisitLimit - postingVisits
            ) { recordId ->
                postingVisits++
                if (accept(recordId) && candidates.add(recordId)) added++
                canVisitCurrentRoute && added < maxNewCandidates
            }
        }

        fun consumeMembershipProbe(): Boolean {
            if (membershipProbes >= membershipProbeLimit) return false
            membershipProbes++
            return true
        }

        fun snapshot(): FingerprintSearchWork = FingerprintSearchWork(
            candidateLimit = candidateLimit,
            postingVisitLimit = postingVisitLimit,
            routeLookupLimit = routeLookupLimit,
            postingVisits = postingVisits,
            routeLookups = routeLookups,
            candidateCount = candidates.size,
            evaluatedCandidateCount = evaluatedCandidateCount
        )

        companion object {
            private fun saturatingMultiply(value: Int, multiplier: Int): Int =
                (value.toLong() * multiplier.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            private fun saturatingAdd(value: Int, increment: Int): Int =
                (value.toLong() + increment.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun addRoutes(
        prepared: PreparedVectorRepresentation?,
        recordId: Long,
        rebuildingHnsw: Boolean = false,
    ) {
        prepared ?: return
        prepared.featureRouteKeys.forEach {
            featurePostings.add(it, recordId)
        }
        val representation = prepared.representation
        representation.toSemanticSignature()?.let { signature ->
            bucketPostings.add(bucketRoute(signature.calibrationId, signature.bucketId), recordId)
            signature.cells.forEachIndexed { index, cell ->
                cellPostings.add(cellRoute(signature.calibrationId, index, cell), recordId)
            }
            signature.bands.forEachIndexed { index, band ->
                bandPostings.add(bandRoute(signature.calibrationId, signature.bitCount, index, band), recordId)
            }
        }
        if (representation.hasHnswVector) {
            if (rebuildingHnsw) {
                hnswIndex.upsertDuringRebuild(
                    recordId,
                    representation.hnswCalibrationId,
                    representation.hnswVector,
                )
            } else {
                hnswIndex.upsert(recordId, representation.hnswCalibrationId, representation.hnswVector)
            }
        }
    }

    private fun removeRoutes(prepared: PreparedVectorRepresentation?, recordId: Long) {
        prepared ?: return
        prepared.featureRouteKeys.forEach {
            featurePostings.remove(it, recordId)
        }
        val representation = prepared.representation
        representation.toSemanticSignature()?.let { signature ->
            bucketPostings.remove(bucketRoute(signature.calibrationId, signature.bucketId), recordId)
            signature.cells.forEachIndexed { index, cell ->
                cellPostings.remove(cellRoute(signature.calibrationId, index, cell), recordId)
            }
            signature.bands.forEachIndexed { index, band ->
                bandPostings.remove(bandRoute(signature.calibrationId, signature.bitCount, index, band), recordId)
            }
        }
        if (representation.hasHnswVector) hnswIndex.remove(recordId)
    }

    private fun updateRoutes(
        oldRepresentation: PreparedVectorRepresentation?,
        representation: PreparedVectorRepresentation?,
        recordId: Long
    ) {
        val oldFeatures = oldRepresentation?.featureRouteKeys?.toSet().orEmpty()
        val newFeatures = representation?.featureRouteKeys?.toSet().orEmpty()
        (oldFeatures - newFeatures).forEach { featurePostings.remove(it, recordId) }
        (newFeatures - oldFeatures).forEach { featurePostings.add(it, recordId) }

        val oldSemantic = oldRepresentation?.representation?.semanticRoutes().orEmpty()
        val newSemantic = representation?.representation?.semanticRoutes().orEmpty()
        (oldSemantic - newSemantic).forEach { (kind, route) -> postingFor(kind).remove(route, recordId) }
        (newSemantic - oldSemantic).forEach { (kind, route) -> postingFor(kind).add(route, recordId) }

        val oldHnsw = oldRepresentation?.representation?.takeIf(VectorRepresentation::hasHnswVector)
        val newHnsw = representation?.representation?.takeIf(VectorRepresentation::hasHnswVector)
        when {
            newHnsw == null && oldHnsw != null -> hnswIndex.remove(recordId)
            newHnsw != null && (
                oldHnsw == null ||
                    oldHnsw.hnswCalibrationId != newHnsw.hnswCalibrationId ||
                    !oldHnsw.hnswVector.contentEquals(newHnsw.hnswVector)
                ) -> hnswIndex.upsert(recordId, newHnsw.hnswCalibrationId, newHnsw.hnswVector)
        }
    }

    private fun postingFor(kind: RouteKind): IndexPostingMap = when (kind) {
        RouteKind.BUCKET -> bucketPostings
        RouteKind.CELL -> cellPostings
        RouteKind.BAND -> bandPostings
    }

    private fun VectorRepresentation.validateConfiguration() {
        require(configurationId == configuration.configurationId) {
            "Vector representation configuration $configurationId does not match ${configuration.configurationId}"
        }
        require(featureHashBits == configuration.entropy.bitCount) {
            "Vector representation uses $featureHashBits feature bits; expected ${configuration.entropy.bitCount}"
        }
    }

    private fun VectorRepresentation.containsFeature(feature: FeatureFingerprint): Boolean {
        return VectorSearchEvaluator.containsFeature(this, feature)
    }

    private fun VectorRepresentation.toSemanticSignature(): SemanticVectorSignature? =
        if (!hasSemanticSignature) null else SemanticVectorSignature(
            calibrationId = calibrationId,
            bucketId = bucketId,
            cells = cells,
            cellCounts = cellCounts,
            fingerprint = semanticFingerprint,
            bands = semanticBands,
            boundaryConfidence = boundaryConfidence,
        )

    private fun VectorRepresentation.semanticRoutes(): Set<Pair<RouteKind, Long>> {
        val signature = toSemanticSignature() ?: return emptySet()
        val routes = LinkedHashSet<Pair<RouteKind, Long>>()
        routes += RouteKind.BUCKET to bucketRoute(signature.calibrationId, signature.bucketId)
        signature.cells.forEachIndexed { index, cell ->
            routes += RouteKind.CELL to cellRoute(signature.calibrationId, index, cell)
        }
        signature.bands.forEachIndexed { index, band ->
            routes += RouteKind.BAND to bandRoute(signature.calibrationId, signature.bitCount, index, band)
        }
        return routes
    }

    private fun representation(recordId: Long): VectorRepresentation? =
        (records.getWithRecID(recordId) as? VectorManagedEntity)?.vectorRepresentation()

    private fun validateFeature(feature: FeatureFingerprint) {
        require(feature.wordCount == configuration.entropy.wordCount) {
            "Feature fingerprint uses ${feature.wordCount} words; expected ${configuration.entropy.wordCount}"
        }
    }

    private fun prepare(value: Any?): PreparedVectorRepresentation? = when (value) {
        null -> null
        is PreparedVectorRepresentation -> value
        is ByteArray -> VectorRepresentationCodec.decodeOrNull(value)
            ?.let(PreparedVectorRepresentation::fromRepresentation)
        is VectorRepresentation -> PreparedVectorRepresentation.fromRepresentation(value)
        else -> throw IllegalArgumentException("Fingerprint index values must be encoded vector representations")
    }

    private fun postings(index: IndexPostingMap, key: Long): Set<Long> {
        val result = LinkedHashSet<Long>()
        index.forEachRecordIdInRange(key, Long.MIN_VALUE, true, key, Long.MAX_VALUE, true, result::add)
        return result
    }

    private fun bucketRoute(calibrationId: Long, bucket: Int): Long =
        route("bucket", calibrationId, bucket.toLong())

    private fun cellRoute(calibrationId: Long, axis: Int, cell: Int): Long =
        route("cell:$axis", calibrationId, cell.toLong())

    private fun bandRoute(calibrationId: Long, bitCount: Int, band: Int, value: Long): Long =
        route("band:$bitCount:$band", calibrationId, value)

    private fun route(domain: String, first: Long, second: Long): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ROUTE_DOMAIN)
        digest.update(domain.toByteArray(StandardCharsets.UTF_8))
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES * 2).putLong(first).putLong(second).array())
        return ByteBuffer.wrap(digest.digest()).long
    }

    private enum class RouteKind { BUCKET, CELL, BAND }

    companion object {
        private const val DIRECT_FEATURE_PROBE_LIMIT = 4_096
        private const val HYBRID_LEXICAL_RESERVE_DIVISOR = 4
        private const val MAX_HYBRID_QUERY_TERMS = 64
        private const val POSTING_VISITS_PER_CANDIDATE = 8
        private const val INITIAL_CANDIDATE_CAPACITY = 4_096
        private const val MIN_ROUTE_LOOKUPS = 16
        private const val MAX_ROUTE_LOOKUPS = 128
        private const val UNIVERSE_KEY = 1L
        private val ROUTE_DOMAIN = "onyx-fingerprint-routing-v1".toByteArray(StandardCharsets.UTF_8)
    }
}
