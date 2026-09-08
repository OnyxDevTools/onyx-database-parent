package com.onyx.vector

import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Representation-only synthetic regression: exhaustive cosine ranking, with no ANN index,
 * candidate selection, filtering, market data, or assessment of financial predictive value.
 *
 * Uses 344 correlated tanh features, each mapped to a cosine/sine pair, 10,000 records and 32
 * queries with a fixed seed. Both paths rank the same generated feature vectors.
 * The reference accumulates the original FloatArray components in Double, without int8
 * quantization. Both int8 representations use the production Float-returning cosine scorer.
 * Rounding scores to Float creates additional ties; all ties here use record order.
 */
class QuantizedCosineRecallTest {

    @Test
    fun `max absolute quantization preserves broad and near neutral cosine neighborhoods`() {
        val broad = experiment("broad_correlated", factorScale = 0.6, noiseScale = 0.15)
        val neutral = experiment("near_neutral_correlated", factorScale = 0.02, noiseScale = 0.005)

        assertTrue(broad.current.recall >= broad.legacy.recall, broad.toString())
        assertTrue(broad.current.recall >= 0.99, broad.toString())
        assertTrue(neutral.current.recall >= 0.90, neutral.toString())
        assertTrue(neutral.current.recall - neutral.legacy.recall >= 0.60, neutral.toString())
    }

    private fun experiment(name: String, factorScale: Double, noiseScale: Double): Report {
        val random = Random(SEED)
        val loadings = Array(FEATURES) {
            val row = DoubleArray(GLOBAL_FACTORS) { random.nextGaussian() }
            val norm = sqrt(row.sumOf { it * it })
            for (index in row.indices) row[index] /= norm
            row
        }
        val dense = Array(RECORDS) { sample(random, loadings, factorScale, noiseScale) }
        val magnitudes = DoubleArray(RECORDS) { magnitude(dense[it]) }
        val legacy = Array(RECORDS) {
            QuantizedCosineVector.fromBytes(legacyUnitNormBytes(dense[it]))
        }
        // Exercise the stored record path as well as the production dense query path below.
        val current = Array(RECORDS) {
            QuantizedCosineVector.fromBytes(QuantizedCosineVector.fromDenseMaxAbsolute(dense[it]).toByteArray())
        }
        val legacyStats = Statistics()
        val currentStats = Statistics()

        repeat(QUERIES) { queryIndex ->
            val query = sample(random, loadings, factorScale, noiseScale)
            val queryMagnitude = magnitude(query)
            val legacyQuery = QuantizedCosineVector.fromBytes(legacyUnitNormBytes(query))
            val currentQuery = QuantizedCosineVector.fromDenseMaxAbsolute(query)
            val exactScores = DoubleArray(RECORDS)
            val legacyScores = DoubleArray(RECORDS)
            val currentScores = DoubleArray(RECORDS)
            for (record in 0 until RECORDS) {
                exactScores[record] = cosine(query, dense[record], queryMagnitude, magnitudes[record])
                legacyScores[record] = legacyQuery.cosineSimilarity(legacy[record]).toDouble()
                currentScores[record] = currentQuery.cosineSimilarity(current[record]).toDouble()
            }
            val exactNeighbors = top(exactScores).toSet()
            legacyStats.record(queryIndex, exactNeighbors, exactScores, legacyScores)
            currentStats.record(queryIndex, exactNeighbors, exactScores, currentScores)
        }
        return Report(name, legacyStats, currentStats).also { report ->
            println(
                "Representation-only synthetic exhaustive cosine comparison: seed=$SEED " +
                    "records=$RECORDS queries=$QUERIES topK=$NEIGHBORS scalarFeatures=$FEATURES " +
                    "dimensions=$DIMENSIONS globalFactors=$GLOBAL_FACTORS intervalFactors=$INTERVAL_FACTORS " +
                    "factorScale=$factorScale noiseScale=$noiseScale; $report"
            )
        }
    }

    private fun sample(
        random: Random,
        loadings: Array<DoubleArray>,
        factorScale: Double,
        noiseScale: Double
    ): FloatArray {
        val factors = DoubleArray(GLOBAL_FACTORS) { random.nextGaussian() }
        val intervals = DoubleArray(INTERVAL_FACTORS) { random.nextGaussian() }
        return FloatArray(DIMENSIONS).also { result ->
            for (feature in 0 until FEATURES) {
                var factor = 0.0
                for (index in 0 until GLOBAL_FACTORS) {
                    factor += loadings[feature][index] * factors[index]
                }
                val scalar = tanh(
                    factorScale * (factor + 0.4 * intervals[feature / FEATURES_PER_INTERVAL]) +
                        noiseScale * random.nextGaussian()
                )
                val angle = Math.PI * scalar / 2.0
                result[feature * 2] = cos(angle).toFloat()
                result[feature * 2 + 1] = sin(angle).toFloat()
            }
        }
    }

    private fun legacyUnitNormBytes(vector: FloatArray): ByteArray {
        val norm = magnitude(vector)
        return ByteArray(vector.size) { Math.round(vector[it] / norm * 127).toByte() }
    }

    private fun magnitude(vector: FloatArray): Double =
        sqrt(vector.sumOf { it.toDouble() * it.toDouble() })

    private fun cosine(a: FloatArray, b: FloatArray, aMagnitude: Double, bMagnitude: Double): Double {
        var dot = 0.0
        for (index in a.indices) dot += a[index].toDouble() * b[index].toDouble()
        return dot / (aMagnitude * bMagnitude)
    }

    private fun top(scores: DoubleArray): List<Int> = scores.indices.sortedWith { left, right ->
        val scoreOrder = scores[right].compareTo(scores[left])
        if (scoreOrder != 0) scoreOrder else left.compareTo(right)
    }.take(NEIGHBORS)

    private inner class Statistics {
        private val overlaps = IntArray(QUERIES)
        private val bestScoreTies = IntArray(QUERIES)
        private var errorSum = 0.0
        private var maxError = 0.0

        val recall: Double
            get() = overlaps.sum().toDouble() / (QUERIES * NEIGHBORS)

        fun record(queryIndex: Int, exactNeighbors: Set<Int>, exact: DoubleArray, scores: DoubleArray) {
            val neighbors = top(scores)
            overlaps[queryIndex] = neighbors.count { it in exactNeighbors }
            val best = scores[neighbors.first()]
            bestScoreTies[queryIndex] = scores.count { it == best }
            for (index in scores.indices) {
                val error = abs(exact[index] - scores[index])
                errorSum += error
                maxError = maxOf(maxError, error)
            }
        }

        override fun toString(): String {
            val sorted = overlaps.sorted()
            val sortedTies = bestScoreTies.sorted()
            return String.format(
                Locale.ROOT,
                "recall@50=%.4f%% overlap(mean/min/median/max)=%.4f/%d/%.1f/%d " +
                    "meanAbsCosineError=%.10f maxAbsCosineError=%.10f bestScoreTies(median/max)=%.1f/%d",
                recall * 100.0,
                overlaps.average(), sorted.first(), (sorted[15] + sorted[16]) / 2.0, sorted.last(),
                errorSum / (QUERIES.toLong() * RECORDS), maxError,
                (sortedTies[15] + sortedTies[16]) / 2.0, sortedTies.last()
            )
        }
    }

    private data class Report(val name: String, val legacy: Statistics, val current: Statistics) {
        override fun toString(): String = "$name legacyUnitNormInt8Float=[$legacy] maxAbsoluteInt8Float=[$current]"
    }

    private companion object {
        const val SEED = 20260907L
        const val FEATURES_PER_INTERVAL = 43
        const val INTERVAL_FACTORS = 8
        const val FEATURES = FEATURES_PER_INTERVAL * INTERVAL_FACTORS
        const val DIMENSIONS = FEATURES * 2
        const val GLOBAL_FACTORS = 12
        const val RECORDS = 10_000
        const val QUERIES = 32
        const val NEIGHBORS = 50
    }
}
