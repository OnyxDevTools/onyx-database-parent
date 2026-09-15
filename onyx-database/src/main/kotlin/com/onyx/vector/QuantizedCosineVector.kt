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
internal class QuantizedCosineVector private constructor(private val content: ByteArray) {
    // Factories transfer an owned array. Only fromBytes must copy caller-owned storage.
    private val magnitude = sqrt(dotProduct(content, content).toDouble())

    val dimensions: Int
        get() = content.size

    fun toByteArray(): ByteArray = content.copyOf()

    fun cosineSimilarity(other: QuantizedCosineVector): Float {
        require(dimensions == other.dimensions) {
            "HNSW vector has ${other.dimensions} dimensions; expected $dimensions"
        }
        val dot = if (vectorApiAvailable && content.size >= 32) {
            VectorizedByteDotProduct.dotProduct(content, other.content).toLong()
        } else {
            // Retain the original loop here: sharing the constructor's norm helper changes
            // HotSpot's optimization of this hot comparison on JVMs without the Vector API.
            var result = 0L
            for (index in content.indices) result += content[index].toLong() * other.content[index].toLong()
            result
        }
        // Keep the original normalization order to preserve score bits and graph tie-breaking.
        return (dot.toDouble() / (magnitude * other.magnitude))
            .coerceIn(-1.0, 1.0)
            .toFloat()
    }

    companion object {
        // Keep Vector API types in a separate class so ordinary JVMs need no extra module flags.
        private val vectorApiAvailable = try {
            // Class.forName is also available on Android, which has no java.lang.ModuleLayer.
            Class.forName("jdk.incubator.vector.IntVector", false, QuantizedCosineVector::class.java.classLoader)
            VectorizedByteDotProduct.isSupported
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        } catch (_: SecurityException) {
            false
        }

        private fun dotProduct(left: ByteArray, right: ByteArray): Long {
            if (vectorApiAvailable && left.size >= 32) return VectorizedByteDotProduct.dotProduct(left, right).toLong()
            // HotSpot optimizes the long reduction better than a scalar int reduction on JDK 23.
            var result = 0L
            for (index in left.indices) result += left[index].toLong() * right[index].toLong()
            return result
        }

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
            return QuantizedCosineVector(vector.copyOf())
        }

        private const val QUANTIZATION_SCALE: Int = Byte.MAX_VALUE.toInt()
    }
}
