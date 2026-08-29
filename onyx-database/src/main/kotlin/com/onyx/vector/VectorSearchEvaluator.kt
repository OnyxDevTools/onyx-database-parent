package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.query.VectorSearchQuery
import kotlin.math.abs

/**
 * Evaluates one vector search clause from the compact representation stored with an entity.
 *
 * This is intentionally independent of index postings. Posting trees find candidates during a
 * database query; this evaluator lets query caches and listeners decide whether a newly inserted
 * or updated entity belongs to an already-running query. No dense embedding is reconstructed.
 */
object VectorSearchEvaluator {

    /**
     * Evaluates the entity's current attribute values while preserving any installed semantic
     * signature. Regenerating sparse features in memory is important during pre-update listener
     * checks, which occur before the new representation is persisted.
     */
    fun evaluate(
        entity: VectorManagedEntity,
        descriptor: EntityDescriptor,
        query: VectorSearchQuery
    ): Float? = evaluate(
        VectorEntityEncoder.encode(entity, descriptor, entity.vectorRepresentation()),
        descriptor,
        query
    )

    /** Returns the same score used by the fingerprint index, or `null` for a non-match. */
    fun evaluate(
        representation: VectorRepresentation,
        descriptor: EntityDescriptor,
        query: VectorSearchQuery
    ): Float? {
        val configuration = VectorManagedConfiguration.forClass(descriptor.entityClass)
        if (
            representation.configurationId != configuration.configurationId ||
            representation.featureHashBits != configuration.entropy.bitCount
        ) {
            return null
        }

        val lexicalScore = lexicalScore(representation, descriptor, configuration, query)
        val semanticScore = semanticScore(representation, query)
        val score = when {
            lexicalScore != null && semanticScore != null ->
                lexicalScore * LEXICAL_WEIGHT + semanticScore * SEMANTIC_WEIGHT
            semanticScore != null -> semanticScore
            lexicalScore != null -> lexicalScore
            else -> return null
        }.toFloat()

        return score.takeIf { query.minScore == null || it >= query.minScore }
    }

    internal fun containsFeature(
        representation: VectorRepresentation,
        feature: FeatureFingerprint
    ): Boolean {
        if (representation.featureWordCount != feature.wordCount) return false
        val words = representation.featureWords
        var offset = 0
        while (offset < words.size) {
            var equal = true
            for (word in 0 until feature.wordCount) {
                if (words[offset + word] != feature[word]) {
                    equal = false
                    break
                }
            }
            if (equal) return true
            offset += feature.wordCount
        }
        return false
    }

    internal fun semanticNearbyBucketIds(
        signature: SemanticVectorSignature,
        radius: Int
    ): IntArray {
        if (radius <= 0) return intArrayOf(signature.bucketId)
        val counts = signature.cellCounts
        val origin = signature.cells
        val results = LinkedHashSet<Int>()

        fun visit(axis: Int, remaining: Int, cells: IntArray) {
            if (axis == cells.size) {
                results += packCells(cells, counts)
                return
            }
            val start = maxOf(0, origin[axis] - remaining)
            val end = minOf(counts[axis] - 1, origin[axis] + remaining)
            for (cell in start..end) {
                val distance = abs(cell - origin[axis])
                cells[axis] = cell
                visit(axis + 1, remaining - distance, cells)
                if (results.size >= MAX_NEARBY_BUCKETS) return
            }
        }

        visit(0, radius, origin.copyOf())
        return results.toIntArray()
    }

    private fun lexicalScore(
        representation: VectorRepresentation,
        descriptor: EntityDescriptor,
        configuration: VectorManagedConfiguration,
        query: VectorSearchQuery
    ): Double? {
        val text = query.text?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val terms = VectorEntityEncoder.tokens(text).distinct()
        if (terms.isEmpty()) return null
        val matches = terms.count { term ->
            containsFeature(
                representation,
                VectorEntityEncoder.fingerprint(
                    descriptor,
                    configuration,
                    "text/term:${VectorEntityEncoder.escape(term)}"
                )
            )
        }
        val accepted = if (query.requireAllTerms) matches == terms.size else matches > 0
        return if (accepted) matches.toDouble() / terms.size.toDouble() else null
    }

    private fun semanticScore(
        representation: VectorRepresentation,
        query: VectorSearchQuery
    ): Double? {
        val requested = query.semantic ?: return null
        val record = representation.toSemanticSignature() ?: return null
        if (
            record.calibrationId != requested.calibrationId ||
            record.bitCount != requested.bitCount ||
            !record.cellCounts.contentEquals(requested.cellCounts) ||
            record.cells.size != requested.cells.size
        ) {
            return null
        }

        val routed = record.bucketId == requested.bucketId ||
            record.matchingBandCount(requested) > 0 ||
            record.cells.indices.any { record.cells[it] == requested.cells[it] } ||
            record.bucketId in semanticNearbyBucketIds(requested, query.nearbyBucketRadius)
        if (!routed) return null

        return FINGERPRINT_WEIGHT * requested.hammingSimilarity(record) +
            BUCKET_WEIGHT * requested.normalizedBucketSimilarity(record)
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

    private fun packCells(cells: IntArray, counts: IntArray): Int {
        var bucket = 0
        cells.indices.forEach { bucket = bucket * counts[it] + cells[it] }
        return bucket
    }

    private const val MAX_NEARBY_BUCKETS = 512
    private const val FINGERPRINT_WEIGHT = 0.70
    private const val BUCKET_WEIGHT = 0.30
    private const val SEMANTIC_WEIGHT = 0.80
    private const val LEXICAL_WEIGHT = 0.20
}
