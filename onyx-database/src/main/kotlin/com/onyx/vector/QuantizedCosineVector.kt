package com.onyx.vector

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic signed-int8 representation of a vector's direction.
 *
 * HNSW only needs a stable distance function while finding candidates. Keeping one byte per
 * component avoids retaining full dense embeddings in every database record, and callers can
 * still rerank the bounded result set with their original embedding representation.
 */
internal class QuantizedCosineVector private constructor(values: ByteArray) {
    private val content = values.copyOf()
    private val magnitude = sqrt(content.sumOf { value ->
        val integer = value.toInt()
        integer.toDouble() * integer.toDouble()
    })

    val dimensions: Int
        get() = content.size

    fun toByteArray(): ByteArray = content.copyOf()

    fun cosineSimilarity(other: QuantizedCosineVector): Float {
        require(dimensions == other.dimensions) {
            "HNSW vector has ${other.dimensions} dimensions; expected $dimensions"
        }
        var dot = 0L
        for (index in content.indices) {
            dot += content[index].toLong() * other.content[index].toLong()
        }
        return (dot.toDouble() / (magnitude * other.magnitude))
            .coerceIn(-1.0, 1.0)
            .toFloat()
    }

    companion object {
        const val MAX_DIMENSIONS: Int = 16_384
        /** Included in computed-vector index configuration so getters rebuild older encodings. */
        const val QUANTIZATION_VERSION: Int = 2

        /** Legacy encoder. Keep its arithmetic stable for existing records and query scores. */
        fun fromDense(vector: FloatArray): QuantizedCosineVector {
            val magnitude = sqrt(squaredMagnitude(vector))
            val quantized = ByteArray(vector.size) { index ->
                ((vector[index] / magnitude) * QUANTIZATION_SCALE)
                    .roundToInt()
                    .coerceIn(-QUANTIZATION_SCALE, QUANTIZATION_SCALE)
                    .toByte()
            }
            require(quantized.any { it.toInt() != 0 }) { "HNSW vector quantized to zero" }
            return QuantizedCosineVector(quantized)
        }

        /** Validates dense input without allocating a quantized representation. */
        fun validateDense(vector: FloatArray) {
            squaredMagnitude(vector)
        }

        private fun squaredMagnitude(vector: FloatArray): Double {
            require(vector.size in 1..MAX_DIMENSIONS) {
                "HNSW vector dimensions must be between 1 and $MAX_DIMENSIONS"
            }
            var squaredMagnitude = 0.0
            vector.forEach { value ->
                require(value.isFinite()) { "HNSW vector values must be finite" }
                squaredMagnitude += value.toDouble() * value.toDouble()
            }
            require(squaredMagnitude.isFinite() && squaredMagnitude > 0.0) {
                "HNSW vector must have a non-zero finite norm"
            }
            return squaredMagnitude
        }

        /** Full-range encoder, used only by explicitly configured getter vector spaces. */
        fun fromDenseMaxAbsolute(vector: FloatArray): QuantizedCosineVector {
            require(vector.size in 1..MAX_DIMENSIONS) {
                "HNSW vector dimensions must be between 1 and $MAX_DIMENSIONS"
            }
            var maximumMagnitude = 0.0
            vector.forEach { value ->
                require(value.isFinite()) { "HNSW vector values must be finite" }
                maximumMagnitude = maxOf(maximumMagnitude, abs(value.toDouble()))
            }
            require(maximumMagnitude > 0.0) {
                "HNSW vector must have a non-zero finite norm"
            }
            // Cosine scoring divides by the byte vectors' magnitudes, so this positive scale
            // cancels. Using the complete int8 range avoids losing most precision on dense,
            // high-dimensional unit vectors. Divide in Double before multiplying: both the
            // smallest subnormal Float and Float.MAX_VALUE remain safe without reciprocals.
            val quantized = ByteArray(vector.size) { index ->
                ((vector[index].toDouble() / maximumMagnitude) * QUANTIZATION_SCALE)
                    .roundToInt()
                    .coerceIn(-QUANTIZATION_SCALE, QUANTIZATION_SCALE)
                    .toByte()
            }
            // At least one maximal component is now exactly +/-127. Keep validation independent
            // of that arithmetic guarantee because persisted bytes are accepted separately.
            require(quantized.any { it.toInt() != 0 }) { "HNSW vector quantized to zero" }
            return QuantizedCosineVector(quantized)
        }

        fun fromBytes(vector: ByteArray): QuantizedCosineVector {
            require(vector.size in 1..MAX_DIMENSIONS) {
                "HNSW vector dimensions must be between 1 and $MAX_DIMENSIONS"
            }
            require(vector.any { it.toInt() != 0 }) { "HNSW vector must not be all zero" }
            return QuantizedCosineVector(vector)
        }

        private const val QUANTIZATION_SCALE: Int = Byte.MAX_VALUE.toInt()
    }
}
