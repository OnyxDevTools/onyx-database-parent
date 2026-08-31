package com.onyx.interactors.index.impl

import com.onyx.diskmap.DiskMap
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.vector.QuantizedCosineVector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.zip.CRC32
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.min

/** Result and physical-work counters from one native HNSW traversal. */
internal data class PersistentHnswSearchResult(
    val scores: LinkedHashMap<Long, Float>,
    val distanceEvaluations: Int,
    val upperLayerDistanceEvaluations: Int,
    val exactFilteredScan: Boolean,
    val concurrentSearchesObserved: Int,
)

/** Aggregate bounded work from the most recent native HNSW node removal. */
data class HnswRemovalWork(
    val recordId: Long,
    val layersVisited: Int,
    val distinctPeersRewritten: Int,
    val repairPairEvaluations: Int,
    val repairEdgesAdded: Int,
    val nodeWrites: Int,
    val metadataWrites: Int,
)

/**
 * Persistent, deterministic HNSW graph over normalized signed-int8 vectors.
 *
 * Nodes and per-calibration entry points live in ordinary Onyx DiskMaps, so opening an index is
 * constant-time and a query never rebuilds or materializes the graph. Every mutation and search
 * has a dataset-size-independent distance-evaluation bound. Searches share a read lock while
 * mutations take the write lock. The small decoded-node LRU has its own mutex, so cache activity
 * never serializes complete traversals.
 */
internal class PersistentHnswIndex(
    private val nodes: DiskMap<Long, ByteArray>,
    private val metadata: DiskMap<Long, ByteArray>,
) {
    private val graphLock = ReentrantReadWriteLock(true)
    private val activeSearches = AtomicInteger()
    private val nodeCache = object : LinkedHashMap<Long, HnswNode>(NODE_CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, HnswNode>?): Boolean =
            size > NODE_CACHE_CAPACITY
    }

    @Volatile
    private var removalWork = emptyRemovalWork(0L)
    private var rebuildOwner: Thread? = null

    fun upsert(recordId: Long, calibrationId: Long, vectorBytes: ByteArray) = graphLock.write {
        upsertUnsafe(recordId, calibrationId, vectorBytes)
    }

    private fun upsertUnsafe(recordId: Long, calibrationId: Long, vectorBytes: ByteArray) {
        require(recordId > 0L) { "HNSW recordId must be positive" }
        require(calibrationId != 0L) { "HNSW calibrationId must be non-zero" }
        val vector = QuantizedCosineVector.fromBytes(vectorBytes)
        val existing = loadNode(recordId)
        if (
            existing != null &&
            existing.calibrationId == calibrationId &&
            existing.vectorBytes.contentEquals(vectorBytes)
        ) {
            return
        }
        // Validate the target graph before removing an existing node.
        loadMetadata(calibrationId)?.let { graph ->
            require(graph.dimensions == vector.dimensions) {
                "HNSW calibration $calibrationId has ${graph.dimensions} dimensions; received ${vector.dimensions}"
            }
        }
        if (existing != null) removalWork = removeUnsafe(recordId)

        val graph = loadMetadata(calibrationId)
        if (graph == null) {
            storeNode(HnswNode(recordId, calibrationId, vectorBytes.copyOf(), deterministicLevel(recordId, calibrationId)))
            storeMetadata(
                HnswMetadata(
                    calibrationId = calibrationId,
                    dimensions = vector.dimensions,
                    entryPoint = recordId,
                    maxLevel = deterministicLevel(recordId, calibrationId),
                    size = 1L,
                )
            )
            return
        }
        val newLevel = deterministicLevel(recordId, calibrationId)
        val newNode = HnswNode(recordId, calibrationId, vectorBytes.copyOf(), newLevel)
        storeNode(newNode)
        var entryPoint = requireNode(graph.entryPoint, calibrationId)
        val upperBudget = DistanceBudget(MAX_INSERT_UPPER_DISTANCE_EVALUATIONS)

        for (layer in graph.maxLevel downTo newLevel + 1) {
            entryPoint = greedyClosest(vector, entryPoint, layer, upperBudget)
        }

        for (layer in min(newLevel, graph.maxLevel) downTo 0) {
            val candidates = searchLayer(
                query = vector,
                entryPoints = listOf(entryPoint),
                layer = layer,
                breadth = EF_CONSTRUCTION,
                evaluationLimit = MAX_INSERT_LAYER_DISTANCE_EVALUATIONS,
            ).best
                .asSequence()
                .filter { it.id != recordId }
                .toList()
            val selected = selectNeighborIds(vector, candidates, neighborLimit(layer))
            selected.forEach { neighborId -> connectBidirectional(recordId, neighborId, layer) }
            if (loadNode(recordId)?.neighborsAt(layer)?.isEmpty() != false && candidates.isNotEmpty()) {
                // Strict reciprocity can reject a new edge when the peer prunes it. Retain one
                // deterministic bridge so insertion cannot create a disconnected component.
                connectBidirectional(recordId, candidates.first().id, layer, force = true)
            }
            candidates.firstOrNull()?.let { entryPoint = requireNode(it.id, calibrationId) }
        }

        storeMetadata(
            graph.copy(
                entryPoint = if (newLevel > graph.maxLevel) recordId else graph.entryPoint,
                maxLevel = maxOf(graph.maxLevel, newLevel),
                size = graph.size + 1L,
            )
        )
    }

    /** Removes a node and repairs its bounded reciprocal neighborhood. */
    fun remove(recordId: Long) = graphLock.write {
        if (loadNode(recordId) == null) {
            removalWork = emptyRemovalWork(recordId)
            return@write
        }
        removalWork = removeUnsafe(recordId)
    }

    private fun removeUnsafe(recordId: Long): HnswRemovalWork {
        val deleted = loadNode(recordId) ?: return emptyRemovalWork(recordId)
        val graph = loadMetadata(deleted.calibrationId) ?: run {
            removeNode(recordId)
            return HnswRemovalWork(recordId, 0, 0, 0, 0, 1, 0)
        }

        val rewrittenPeers = HashMap<Long, HnswNode>()
        var layersVisited = 0
        var repairPairEvaluations = 0
        var repairEdgesAdded = 0
        var remainingPairEvaluations = MAX_DELETE_REPAIR_PAIR_EVALUATIONS
        var remainingRepairEdges = MAX_DELETE_REPAIR_EDGES

        for (layer in 0..deleted.level) {
            val peers = deleted.neighborsAt(layer)
                .asSequence()
                .mapNotNull { rewrittenPeers[it] ?: loadNode(it) }
                .filter { it.calibrationId == deleted.calibrationId && it.id != recordId }
                .sortedBy(HnswNode::id)
                .toList()
            layersVisited++
            val adjacency = peers.associate { peer ->
                peer.id to peer.neighborsAt(layer)
                    .asSequence()
                    .filter { it != recordId }
                    .toCollection(LinkedHashSet())
            }
            val repair = repairDeletedNeighborhood(
                peers = peers,
                adjacency = adjacency,
                layer = layer,
                pairEvaluationBudget = remainingPairEvaluations,
                edgeBudget = remainingRepairEdges,
            )
            repairPairEvaluations += repair.pairEvaluations
            repairEdgesAdded += repair.edgesAdded
            remainingPairEvaluations -= repair.pairEvaluations
            remainingRepairEdges -= repair.edgesAdded

            peers.forEach { peer ->
                val current = rewrittenPeers[peer.id] ?: peer
                val repaired = adjacency.getValue(peer.id).sorted().toLongArray()
                if (!current.neighborsAt(layer).contentEquals(repaired)) {
                    rewrittenPeers[peer.id] = current.withNeighbors(layer, repaired)
                }
            }
        }
        rewrittenPeers.values.sortedBy(HnswNode::id).forEach(::storeNode)
        removeNode(recordId)
        val nodeWrites = rewrittenPeers.size + 1

        if (graph.size <= 1L) {
            removeMetadata(deleted.calibrationId)
            return HnswRemovalWork(
                recordId,
                layersVisited,
                rewrittenPeers.size,
                repairPairEvaluations,
                repairEdgesAdded,
                nodeWrites,
                1,
            )
        }
        if (graph.entryPoint != recordId) {
            storeMetadata(graph.copy(size = graph.size - 1L))
            return HnswRemovalWork(
                recordId,
                layersVisited,
                rewrittenPeers.size,
                repairPairEvaluations,
                repairEdgesAdded,
                nodeWrites,
                1,
            )
        }

        // The entry point's highest non-empty layer always contains nodes at that layer. Dropping
        // through empty private upper layers avoids an O(table size) replacement scan on delete.
        var replacement: HnswNode? = null
        var replacementLevel = -1
        for (layer in deleted.level downTo 0) {
            replacement = deleted.neighborsAt(layer)
                .asSequence()
                .mapNotNull(::loadNode)
                .filter { it.calibrationId == deleted.calibrationId && it.level >= layer }
                .minByOrNull(HnswNode::id)
            if (replacement != null) {
                replacementLevel = layer
                break
            }
        }
        checkNotNull(replacement) {
            "HNSW calibration ${deleted.calibrationId} lost its entry point; rebuild the vector index"
        }
        storeMetadata(
            graph.copy(
                entryPoint = replacement.id,
                maxLevel = minOf(replacement.level, maxOf(0, replacementLevel)),
                size = graph.size - 1L,
            )
        )
        return HnswRemovalWork(
            recordId,
            layersVisited,
            rewrittenPeers.size,
            repairPairEvaluations,
            repairEdgesAdded,
            nodeWrites,
            1,
        )
    }

    /**
     * Fills deletion-created vacancies without pair-connecting the complete neighborhood.
     *
     * Candidate pairs are sampled in deterministic round-robin ID order, scored once, and then
     * admitted into in-memory reciprocal adjacency lists only when both endpoints have capacity.
     * Each peer is persisted at most once after every layer has been planned. No existing edge is
     * pruned, so repair never cascades into another insertion and its vector work has one hard
     * budget across the complete deletion.
     */
    private fun repairDeletedNeighborhood(
        peers: List<HnswNode>,
        adjacency: Map<Long, MutableSet<Long>>,
        layer: Int,
        pairEvaluationBudget: Int,
        edgeBudget: Int,
    ): DeleteRepairResult {
        if (peers.size < 2 || pairEvaluationBudget <= 0 || edgeBudget <= 0) {
            return DeleteRepairResult(0, 0)
        }
        val peerById = peers.associateBy(HnswNode::id)
        val seen = HashSet<RepairEdgeKey>(minOf(pairEvaluationBudget * 2, 512))
        val candidates = ArrayList<RepairCandidate>(pairEvaluationBudget)
        var offset = 1
        while (offset < peers.size && candidates.size < pairEvaluationBudget) {
            for (index in peers.indices) {
                if (candidates.size >= pairEvaluationBudget) break
                val first = peers[index]
                val second = peers[(index + offset) % peers.size]
                val key = RepairEdgeKey(minOf(first.id, second.id), maxOf(first.id, second.id))
                if (!seen.add(key) || second.id in adjacency.getValue(first.id)) continue
                candidates += RepairCandidate(
                    firstId = key.firstId,
                    secondId = key.secondId,
                    score = first.vector.cosineSimilarity(second.vector),
                )
            }
            offset++
        }

        val additionsByPeer = HashMap<Long, Int>()
        var edgesAdded = 0
        val limit = neighborLimit(layer)
        candidates.sortedWith(REPAIR_CANDIDATE_COMPARATOR).forEach { candidate ->
            if (edgesAdded >= edgeBudget) return@forEach
            val firstNeighbors = adjacency.getValue(candidate.firstId)
            val secondNeighbors = adjacency.getValue(candidate.secondId)
            if (
                firstNeighbors.size >= limit || secondNeighbors.size >= limit ||
                candidate.secondId in firstNeighbors || candidate.firstId in secondNeighbors ||
                (additionsByPeer[candidate.firstId] ?: 0) >= MAX_DELETE_REPAIR_EDGES_PER_PEER ||
                (additionsByPeer[candidate.secondId] ?: 0) >= MAX_DELETE_REPAIR_EDGES_PER_PEER ||
                peerById[candidate.firstId] == null || peerById[candidate.secondId] == null
            ) return@forEach

            firstNeighbors += candidate.secondId
            secondNeighbors += candidate.firstId
            additionsByPeer[candidate.firstId] = (additionsByPeer[candidate.firstId] ?: 0) + 1
            additionsByPeer[candidate.secondId] = (additionsByPeer[candidate.secondId] ?: 0) + 1
            edgesAdded++
        }
        return DeleteRepairResult(candidates.size, edgesAdded)
    }

    /** Executes bounded ANN traversal, optionally admitting results only from [allowedRecordIds]. */
    fun search(
        query: HnswSearchQuery,
        allowedRecordIds: Set<Long>? = null,
    ): PersistentHnswSearchResult = graphLock.read {
        val concurrent = activeSearches.incrementAndGet()
        try {
            searchUnsafe(query, allowedRecordIds, concurrent)
        } finally {
            activeSearches.decrementAndGet()
        }
    }

    private fun searchUnsafe(
        query: HnswSearchQuery,
        allowedRecordIds: Set<Long>?,
        concurrentSearchesObserved: Int,
    ): PersistentHnswSearchResult {
        val graph = loadMetadata(query.calibrationId)
            ?: return PersistentHnswSearchResult(
                LinkedHashMap(), 0, 0, false, concurrentSearchesObserved
            )
        val queryVector = query.quantizedVector
        require(graph.dimensions == queryVector.dimensions) {
            "HNSW calibration ${query.calibrationId} has ${graph.dimensions} dimensions; query has ${queryVector.dimensions}"
        }
        if (allowedRecordIds != null && allowedRecordIds.size <= query.efSearch) {
            val scored = allowedRecordIds.asSequence()
                .mapNotNull { recordId ->
                    val node = loadNode(recordId)
                        ?.takeIf { it.calibrationId == query.calibrationId }
                        ?: return@mapNotNull null
                    ScoredNode(recordId, queryVector.cosineSimilarity(node.vector))
                }
                .filter { query.minScore == null || it.score >= query.minScore }
                .sortedWith(BEST_FIRST_COMPARATOR)
                .take(query.maxCandidates)
                .toList()
            return PersistentHnswSearchResult(
                scores = scored.toScores(),
                distanceEvaluations = allowedRecordIds.size,
                upperLayerDistanceEvaluations = 0,
                exactFilteredScan = true,
                concurrentSearchesObserved = concurrentSearchesObserved,
            )
        }

        var entryPoint = requireNode(graph.entryPoint, query.calibrationId)
        val upperBudget = DistanceBudget(
            minOf(
                MAX_QUERY_UPPER_DISTANCE_EVALUATIONS,
                maxOf(MIN_QUERY_UPPER_DISTANCE_EVALUATIONS, query.efSearch / 4)
            )
        )
        for (layer in graph.maxLevel downTo 1) {
            entryPoint = greedyClosest(queryVector, entryPoint, layer, upperBudget)
        }
        val layerResult = searchLayer(
            query = queryVector,
            entryPoints = listOf(entryPoint),
            layer = 0,
            breadth = query.efSearch,
            evaluationLimit = query.efSearch,
        )
        val scored = layerResult.evaluated.asSequence()
            .filter { allowedRecordIds == null || it.id in allowedRecordIds }
            .filter { query.minScore == null || it.score >= query.minScore }
            .sortedWith(BEST_FIRST_COMPARATOR)
            .take(query.maxCandidates)
            .toList()
        return PersistentHnswSearchResult(
            scores = scored.toScores(),
            distanceEvaluations = layerResult.evaluationCount,
            upperLayerDistanceEvaluations = upperBudget.used,
            exactFilteredScan = false,
            concurrentSearchesObserved = concurrentSearchesObserved,
        )
    }

    fun clear() = graphLock.write {
        clearGraphUnsafe()
    }

    /** Holds the exclusive graph lock for one streaming rebuild. */
    fun beginRebuild() {
        graphLock.writeLock().lock()
        try {
            check(rebuildOwner == null) { "An HNSW rebuild is already in progress" }
            rebuildOwner = Thread.currentThread()
            clearGraphUnsafe()
        } catch (failure: Throwable) {
            rebuildOwner = null
            graphLock.writeLock().unlock()
            throw failure
        }
    }

    fun upsertDuringRebuild(recordId: Long, calibrationId: Long, vectorBytes: ByteArray) {
        check(rebuildOwner === Thread.currentThread()) { "HNSW rebuild is not owned by this thread" }
        upsertUnsafe(recordId, calibrationId, vectorBytes)
    }

    fun completeRebuild() {
        check(rebuildOwner === Thread.currentThread()) { "HNSW rebuild is not owned by this thread" }
        rebuildOwner = null
        graphLock.writeLock().unlock()
    }

    fun abortRebuild() {
        check(rebuildOwner === Thread.currentThread()) { "HNSW rebuild is not owned by this thread" }
        rebuildOwner = null
        graphLock.writeLock().unlock()
    }

    /** Explicit maintenance check; never runs on the prompt-time search path. */
    fun validateGraph(calibrationId: Long): Long = graphLock.read {
        val graph = loadMetadata(calibrationId) ?: return@read 0L
        var count = 0L
        nodes.forEach { (id, bytes) ->
            val node = HnswNodeCodec.decode(id, bytes)
            if (node.calibrationId != calibrationId) return@forEach
            count++
            require(node.vector.dimensions == graph.dimensions) { "HNSW node $id has the wrong dimensions" }
            node.neighbors.forEachIndexed { layer, neighbors ->
                require(neighbors.size <= neighborLimit(layer)) { "HNSW node $id exceeds its degree bound" }
                neighbors.forEach { neighborId ->
                    val neighbor = requireNotNull(loadNode(neighborId)) {
                        "HNSW node $id contains dead neighbor $neighborId"
                    }
                    require(neighbor.calibrationId == calibrationId && neighbor.level >= layer) {
                        "HNSW node $id contains an incompatible neighbor $neighborId"
                    }
                    require(id in neighbor.neighborsAt(layer)) {
                        "HNSW edge $id -> $neighborId at layer $layer is not reciprocal"
                    }
                }
            }
        }
        require(count == graph.size) {
            "HNSW calibration $calibrationId metadata size ${graph.size} does not match $count nodes"
        }
        requireNode(graph.entryPoint, calibrationId)
        count
    }

    /** Bounded physical work from the most recent removal/update of an existing node. */
    fun lastRemovalWork(): HnswRemovalWork = graphLock.read { removalWork }

    /** Maintenance/test diagnostic that does not scan the graph. */
    fun nodeLevel(recordId: Long): Int? = graphLock.read { loadNode(recordId)?.level }

    /** Maintenance/test diagnostic that reads one compact graph node. */
    fun nodeDegree(recordId: Long, layer: Int = 0): Int? = graphLock.read {
        loadNode(recordId)?.takeIf { layer in 0..it.level }?.neighborsAt(layer)?.size
    }

    private fun clearGraphUnsafe() {
        nodes.clear()
        metadata.clear()
        synchronized(nodeCache) { nodeCache.clear() }
    }

    private fun greedyClosest(
        query: QuantizedCosineVector,
        start: HnswNode,
        layer: Int,
        budget: DistanceBudget,
    ): HnswNode {
        var current = start
        var currentScore = if (budget.consume()) query.cosineSimilarity(current.vector) else return current
        val visited = HashSet<Long>()
        visited += current.id
        while (budget.canContinue) {
            var improved: HnswNode? = null
            var improvedScore = currentScore
            for (neighborId in current.neighborsAt(layer)) {
                if (!visited.add(neighborId) || !budget.consume()) continue
                val neighbor = loadNode(neighborId)
                    ?.takeIf { it.calibrationId == current.calibrationId && it.level >= layer }
                    ?: continue
                val score = query.cosineSimilarity(neighbor.vector)
                if (score > improvedScore || score == improvedScore && neighbor.id < (improved?.id ?: current.id)) {
                    improved = neighbor
                    improvedScore = score
                }
            }
            current = improved ?: break
            currentScore = improvedScore
        }
        return current
    }

    private fun searchLayer(
        query: QuantizedCosineVector,
        entryPoints: List<HnswNode>,
        layer: Int,
        breadth: Int,
        evaluationLimit: Int,
    ): LayerSearchResult {
        val candidates = PriorityQueue(BEST_FIRST_COMPARATOR)
        val best = PriorityQueue(WORST_FIRST_COMPARATOR)
        val evaluated = ArrayList<ScoredNode>(minOf(evaluationLimit, INITIAL_SEARCH_CAPACITY))
        val visited = HashSet<Long>(minOf(evaluationLimit, INITIAL_SEARCH_CAPACITY))
        var evaluationCount = 0

        entryPoints.sortedBy(HnswNode::id).forEach { entry ->
            if (evaluationCount >= evaluationLimit || !visited.add(entry.id)) return@forEach
            val scored = ScoredNode(entry.id, query.cosineSimilarity(entry.vector))
            evaluationCount++
            candidates += scored
            best += scored
            evaluated += scored
        }

        while (candidates.isNotEmpty() && evaluationCount < evaluationLimit) {
            val current = candidates.remove()
            val worst = best.peek()
            if (best.size >= breadth && worst != null && isBetter(worst, current)) break
            val currentNode = loadNode(current.id) ?: continue
            for (neighborId in currentNode.neighborsAt(layer)) {
                if (evaluationCount >= evaluationLimit) break
                if (!visited.add(neighborId)) continue
                val neighbor = loadNode(neighborId)
                    ?.takeIf { it.calibrationId == currentNode.calibrationId && it.level >= layer }
                    ?: continue
                val scored = ScoredNode(neighbor.id, query.cosineSimilarity(neighbor.vector))
                evaluationCount++
                evaluated += scored
                val currentWorst = best.peek()
                if (best.size < breadth || currentWorst == null || isBetter(scored, currentWorst)) {
                    candidates += scored
                    best += scored
                    if (best.size > breadth) best.remove()
                }
            }
        }
        return LayerSearchResult(
            best = best.toList().sortedWith(BEST_FIRST_COMPARATOR),
            evaluated = evaluated,
            evaluationCount = evaluationCount,
        )
    }

    /**
     * Adds one strictly reciprocal edge and performs bounded removal-only propagation for edges
     * evicted by diversity pruning. No replacement insertion is recursively triggered here.
     */
    private fun connectBidirectional(
        firstId: Long,
        secondId: Long,
        layer: Int,
        force: Boolean = false,
    ) {
        if (firstId == secondId) return
        val first = loadNode(firstId) ?: return
        val second = loadNode(secondId) ?: return
        if (
            first.calibrationId != second.calibrationId ||
            first.level < layer ||
            second.level < layer
        ) return

        val oldFirst = first.neighborsAt(layer)
        val oldSecond = second.neighborsAt(layer)
        if (secondId in oldFirst && firstId in oldSecond) return
        var newFirst = selectForNode(first, oldFirst + secondId, layer)
        var newSecond = selectForNode(second, oldSecond + firstId, layer)
        if (force) {
            newFirst = forceNeighbor(newFirst, secondId, neighborLimit(layer))
            newSecond = forceNeighbor(newSecond, firstId, neighborLimit(layer))
        }
        val accepted = secondId in newFirst && firstId in newSecond
        if (!accepted) {
            // Candidate consideration is speculative. If either endpoint rejects the link, retain
            // both complete old lists; otherwise unrelated edges could erode on every rejection.
            return
        }

        if (!oldFirst.contentEquals(newFirst)) storeNode(first.withNeighbors(layer, newFirst))
        if (!oldSecond.contentEquals(newSecond)) storeNode(second.withNeighbors(layer, newSecond))

        // Reloading inside removeOneWayNeighbor avoids overwriting either endpoint when the other
        // endpoint's pruning changed it above. At most M + M0 removals are propagated.
        oldFirst.filterNot { it in newFirst || it == secondId }
            .forEach { removeOneWayNeighbor(it, firstId, layer) }
        oldSecond.filterNot { it in newSecond || it == firstId }
            .forEach { removeOneWayNeighbor(it, secondId, layer) }
    }

    private fun selectForNode(node: HnswNode, candidateIds: LongArray, layer: Int): LongArray {
        val scored = candidateIds.asSequence()
            .distinct()
            .filter { it != node.id }
            .mapNotNull { candidateId ->
                loadNode(candidateId)
                    ?.takeIf { it.calibrationId == node.calibrationId && it.level >= layer }
                    ?.let { candidate ->
                        ScoredNode(candidate.id, node.vector.cosineSimilarity(candidate.vector))
                    }
            }
            .sortedWith(BEST_FIRST_COMPARATOR)
            .toList()
        return selectNeighborIds(node.vector, scored, neighborLimit(layer))
    }

    private fun forceNeighbor(values: LongArray, neighborId: Long, limit: Int): LongArray {
        if (neighborId in values) return values
        return (if (values.size < limit) values + neighborId else values.copyOf(limit - 1) + neighborId)
            .distinct()
            .sorted()
            .toLongArray()
    }

    private fun removeOneWayNeighbor(nodeId: Long, removedId: Long, layer: Int) {
        val node = loadNode(nodeId) ?: return
        if (node.level < layer || removedId !in node.neighborsAt(layer)) return
        storeNode(node.withNeighbors(layer, node.neighborsAt(layer).filterNot { it == removedId }.toLongArray()))
    }

    /**
     * Standard HNSW diversity heuristic: retain a candidate when it is closer to the base vector
     * than to every already selected neighbor. Remaining slots are filled by score so sparse
     * layers retain connectivity. Candidate sets are capped by construction/search bounds.
     */
    private fun selectNeighborIds(
        base: QuantizedCosineVector,
        candidates: List<ScoredNode>,
        limit: Int,
    ): LongArray {
        val ordered = candidates.distinctBy(ScoredNode::id).sortedWith(BEST_FIRST_COMPARATOR)
        val selected = ArrayList<ScoredNode>(limit)
        val rejected = ArrayList<ScoredNode>()
        ordered.forEach { candidate ->
            if (selected.size >= limit) {
                rejected += candidate
                return@forEach
            }
            val candidateNode = loadNode(candidate.id) ?: return@forEach
            val baseScore = base.cosineSimilarity(candidateNode.vector)
            val diverse = selected.all { chosen ->
                val chosenNode = loadNode(chosen.id) ?: return@all true
                candidateNode.vector.cosineSimilarity(chosenNode.vector) <= baseScore
            }
            if (diverse) selected += candidate.copy(score = baseScore) else rejected += candidate.copy(score = baseScore)
        }
        if (selected.size < limit) {
            rejected.sortedWith(BEST_FIRST_COMPARATOR)
                .take(limit - selected.size)
                .forEach(selected::add)
        }
        return selected.map(ScoredNode::id).toLongArray()
    }

    private fun loadNode(recordId: Long): HnswNode? {
        synchronized(nodeCache) { nodeCache[recordId] }?.let { return it }
        val decoded = nodes[recordId]?.let { HnswNodeCodec.decode(recordId, it) } ?: return null
        synchronized(nodeCache) { nodeCache[recordId] = decoded }
        return decoded
    }

    private fun requireNode(recordId: Long, calibrationId: Long): HnswNode =
        requireNotNull(loadNode(recordId)?.takeIf { it.calibrationId == calibrationId }) {
            "HNSW calibration $calibrationId entry point $recordId is missing; rebuild the vector index"
        }

    private fun storeNode(node: HnswNode) {
        nodes[node.id] = HnswNodeCodec.encode(node)
        synchronized(nodeCache) { nodeCache[node.id] = node }
    }

    private fun removeNode(recordId: Long) {
        nodes.remove(recordId)
        synchronized(nodeCache) { nodeCache.remove(recordId) }
    }

    private fun loadMetadata(calibrationId: Long): HnswMetadata? =
        metadata[calibrationId]?.let(HnswMetadataCodec::decode)

    private fun storeMetadata(value: HnswMetadata) {
        metadata[value.calibrationId] = HnswMetadataCodec.encode(value)
    }

    private fun removeMetadata(calibrationId: Long) {
        metadata.remove(calibrationId)
    }

    private fun deterministicLevel(recordId: Long, calibrationId: Long): Int {
        var random = mix64(recordId xor calibrationId xor LEVEL_SEED)
        var level = 0
        while (level < MAX_LEVEL && random and LEVEL_MASK == 0L) {
            level++
            random = mix64(random + LEVEL_SEED)
        }
        return level
    }

    private fun neighborLimit(layer: Int): Int = if (layer == 0) MAX_LEVEL_ZERO_NEIGHBORS else MAX_NEIGHBORS

    private fun List<ScoredNode>.toScores(): LinkedHashMap<Long, Float> =
        LinkedHashMap<Long, Float>(size).also { result -> forEach { result[it.id] = it.score } }

    private data class HnswNode(
        val id: Long,
        val calibrationId: Long,
        val vectorBytes: ByteArray,
        val level: Int,
        val neighbors: Array<LongArray> = Array(level + 1) { longArrayOf() },
    ) {
        val vector: QuantizedCosineVector by lazy { QuantizedCosineVector.fromBytes(vectorBytes) }

        fun neighborsAt(layer: Int): LongArray = neighbors.getOrNull(layer)?.copyOf() ?: longArrayOf()

        fun withNeighbors(layer: Int, values: LongArray): HnswNode {
            require(layer in 0..level) { "HNSW layer $layer exceeds node level $level" }
            val copy = Array(neighbors.size) { neighbors[it].copyOf() }
            copy[layer] = values.distinct().filter { it != id }.sorted().toLongArray()
            return copy(neighbors = copy)
        }
    }

    private data class HnswMetadata(
        val calibrationId: Long,
        val dimensions: Int,
        val entryPoint: Long,
        val maxLevel: Int,
        val size: Long,
    )

    private data class ScoredNode(val id: Long, val score: Float)

    private data class LayerSearchResult(
        val best: List<ScoredNode>,
        val evaluated: List<ScoredNode>,
        val evaluationCount: Int,
    )

    private data class RepairEdgeKey(val firstId: Long, val secondId: Long)

    private data class RepairCandidate(
        val firstId: Long,
        val secondId: Long,
        val score: Float,
    )

    private data class DeleteRepairResult(
        val pairEvaluations: Int,
        val edgesAdded: Int,
    )

    private class DistanceBudget(private val limit: Int) {
        var used: Int = 0
            private set
        val canContinue: Boolean
            get() = used < limit

        fun consume(): Boolean {
            if (!canContinue) return false
            used++
            return true
        }
    }

    private object HnswNodeCodec {
        private const val MAGIC = 0x4f484e44 // OHND
        private const val VERSION: Short = 1

        fun encode(node: HnswNode): ByteArray {
            var payloadSize = 4 + 2 + 8 + 4 + node.vectorBytes.size + 4
            node.neighbors.forEach { values ->
                payloadSize = Math.addExact(payloadSize, Math.addExact(4, Math.multiplyExact(values.size, 8)))
            }
            val bytes = ByteArray(Math.addExact(payloadSize, 4))
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            buffer.putInt(MAGIC)
            buffer.putShort(VERSION)
            buffer.putLong(node.calibrationId)
            buffer.putInt(node.vectorBytes.size)
            buffer.put(node.vectorBytes)
            buffer.putInt(node.neighbors.size)
            node.neighbors.forEach { neighbors ->
                buffer.putInt(neighbors.size)
                neighbors.forEach(buffer::putLong)
            }
            putChecksum(buffer, bytes, payloadSize)
            return bytes
        }

        fun decode(recordId: Long, bytes: ByteArray): HnswNode {
            val buffer = checkedPayload(bytes, MIN_NODE_PAYLOAD_BYTES)
            require(buffer.int == MAGIC && buffer.short == VERSION) { "Unsupported HNSW node format" }
            val calibrationId = buffer.long
            require(calibrationId != 0L) { "Invalid HNSW node calibration" }
            val vectorSize = readSize(buffer, QuantizedCosineVector.MAX_DIMENSIONS)
            require(buffer.remaining() >= vectorSize + 4) { "HNSW node vector is truncated" }
            val vector = ByteArray(vectorSize).also(buffer::get)
            QuantizedCosineVector.fromBytes(vector)
            val levelCount = readSize(buffer, MAX_LEVEL + 1)
            require(levelCount > 0) { "HNSW node must contain level zero" }
            val neighbors = Array(levelCount) { layer ->
                val size = readSize(buffer, neighborLimit(layer))
                require(buffer.remaining() >= size * 8) { "HNSW neighbor list is truncated" }
                LongArray(size) { buffer.long }.also { values ->
                    require(values.none { it <= 0L || it == recordId }) { "Invalid HNSW neighbor reference" }
                    require(values.toSet().size == values.size) { "Duplicate HNSW neighbor reference" }
                }
            }
            require(!buffer.hasRemaining()) { "Unexpected trailing HNSW node data" }
            return HnswNode(recordId, calibrationId, vector, levelCount - 1, neighbors)
        }
    }

    private object HnswMetadataCodec {
        private const val MAGIC = 0x4f484d44 // OHMD
        private const val VERSION: Short = 1
        private const val PAYLOAD_BYTES = 4 + 2 + 8 + 4 + 8 + 4 + 8

        fun encode(value: HnswMetadata): ByteArray {
            val bytes = ByteArray(PAYLOAD_BYTES + 4)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            buffer.putInt(MAGIC)
            buffer.putShort(VERSION)
            buffer.putLong(value.calibrationId)
            buffer.putInt(value.dimensions)
            buffer.putLong(value.entryPoint)
            buffer.putInt(value.maxLevel)
            buffer.putLong(value.size)
            putChecksum(buffer, bytes, PAYLOAD_BYTES)
            return bytes
        }

        fun decode(bytes: ByteArray): HnswMetadata {
            val buffer = checkedPayload(bytes, PAYLOAD_BYTES)
            require(buffer.remaining() == PAYLOAD_BYTES) { "Invalid HNSW metadata length" }
            require(buffer.int == MAGIC && buffer.short == VERSION) { "Unsupported HNSW metadata format" }
            val result = HnswMetadata(
                calibrationId = buffer.long,
                dimensions = buffer.int,
                entryPoint = buffer.long,
                maxLevel = buffer.int,
                size = buffer.long,
            )
            require(result.calibrationId != 0L) { "Invalid HNSW metadata calibration" }
            require(result.dimensions in 1..QuantizedCosineVector.MAX_DIMENSIONS) { "Invalid HNSW metadata dimensions" }
            require(result.entryPoint > 0L && result.maxLevel in 0..MAX_LEVEL && result.size > 0L) {
                "Invalid HNSW metadata state"
            }
            return result
        }
    }

    companion object {
        private val BEST_FIRST_COMPARATOR =
            compareByDescending<ScoredNode>(ScoredNode::score).thenBy(ScoredNode::id)
        private val WORST_FIRST_COMPARATOR =
            compareBy<ScoredNode>(ScoredNode::score).thenByDescending(ScoredNode::id)
        private val REPAIR_CANDIDATE_COMPARATOR =
            compareByDescending<RepairCandidate>(RepairCandidate::score)
                .thenBy(RepairCandidate::firstId)
                .thenBy(RepairCandidate::secondId)

        private const val MAX_NEIGHBORS = 16
        private const val MAX_LEVEL_ZERO_NEIGHBORS = 32
        private const val EF_CONSTRUCTION = 200
        private const val MAX_INSERT_LAYER_DISTANCE_EVALUATIONS = 1_600
        private const val MAX_INSERT_UPPER_DISTANCE_EVALUATIONS = 512
        private const val MAX_DELETE_REPAIR_PAIR_EVALUATIONS = 192
        private const val MAX_DELETE_REPAIR_EDGES = 32
        private const val MAX_DELETE_REPAIR_EDGES_PER_PEER = 2
        private const val MIN_QUERY_UPPER_DISTANCE_EVALUATIONS = 64
        private const val MAX_QUERY_UPPER_DISTANCE_EVALUATIONS = 512
        private const val MAX_LEVEL = 16
        private const val LEVEL_MASK = 0x0fL
        private const val LEVEL_SEED = -7046029254386353131L
        private const val NODE_CACHE_CAPACITY = 4_096
        private const val INITIAL_SEARCH_CAPACITY = 4_096
        private const val MIN_NODE_PAYLOAD_BYTES = 4 + 2 + 8 + 4 + 1 + 4
        private fun emptyRemovalWork(recordId: Long) =
            HnswRemovalWork(recordId, 0, 0, 0, 0, 0, 0)

        private fun isBetter(first: ScoredNode, second: ScoredNode): Boolean =
            first.score > second.score || first.score == second.score && first.id < second.id

        private fun mix64(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
            mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
            return mixed xor (mixed ushr 31)
        }

        private fun neighborLimit(layer: Int): Int =
            if (layer == 0) MAX_LEVEL_ZERO_NEIGHBORS else MAX_NEIGHBORS

        private fun putChecksum(buffer: ByteBuffer, bytes: ByteArray, payloadSize: Int) {
            check(buffer.position() == payloadSize) { "HNSW codec size calculation drifted" }
            buffer.putInt(CRC32().apply { update(bytes, 0, payloadSize) }.value.toInt())
        }

        private fun checkedPayload(bytes: ByteArray, minimumPayloadSize: Int): ByteBuffer {
            require(bytes.size >= minimumPayloadSize + 4) { "HNSW record is truncated" }
            val expected = ByteBuffer.wrap(bytes, bytes.size - 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val actual = CRC32().apply { update(bytes, 0, bytes.size - 4) }.value.toInt()
            require(expected == actual) { "HNSW record checksum mismatch" }
            return ByteBuffer.wrap(bytes, 0, bytes.size - 4).order(ByteOrder.BIG_ENDIAN)
        }

        private fun readSize(buffer: ByteBuffer, maximum: Int): Int {
            require(buffer.remaining() >= 4) { "HNSW collection length is missing" }
            return buffer.int.also { require(it in 0..maximum) { "Invalid HNSW collection length $it" } }
        }
    }
}
