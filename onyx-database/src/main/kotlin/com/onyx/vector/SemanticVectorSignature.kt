package com.onyx.vector

import java.io.Serializable
import kotlin.math.abs

/**
 * Persistent semantic routing data produced by a [VectorCalibration].
 *
 * The source embedding is intentionally not retained. Array arguments are copied on
 * construction and array properties return copies, so a signature remains safe to use
 * as a map key or to share between readers.
 */
class SemanticVectorSignature @JvmOverloads constructor(
    val calibrationId: Long = DEFAULT_CALIBRATION_ID,
    val bucketId: Int = DEFAULT_BUCKET_ID,
    cells: IntArray = intArrayOf(DEFAULT_CELL),
    cellCounts: IntArray = intArrayOf(DEFAULT_CELL_COUNT),
    fingerprint: LongArray = longArrayOf(DEFAULT_FINGERPRINT_WORD),
    bands: LongArray = longArrayOf(0L, 0L, 0L, 0L),
    val boundaryConfidence: Float = DEFAULT_BOUNDARY_CONFIDENCE,
) : Serializable {

    private val cellContent = cells.copyOf()
    private val cellCountContent = cellCounts.copyOf()
    private val fingerprintContent = fingerprint.copyOf()
    private val bandContent = bands.copyOf()

    /** Quantized PCA product-cell coordinates. */
    val cells: IntArray
        get() = cellContent.copyOf()

    /** Mixed-radix cardinality of each quantized PCA axis. */
    val cellCounts: IntArray
        get() = cellCountContent.copyOf()

    /** SimHash words, from 64 to 256 bits. */
    val fingerprint: LongArray
        get() = fingerprintContent.copyOf()

    /** Alias useful to persistence code that describes the physical representation. */
    val fingerprintWords: LongArray
        get() = fingerprintContent.copyOf()

    /** Exactly four equal-width, low-bit-aligned portions of [fingerprint]. */
    val bands: LongArray
        get() = bandContent.copyOf()

    val bitCount: Int
        get() = fingerprintContent.size * Long.SIZE_BITS

    init {
        require(calibrationId != NO_CALIBRATION) { "calibrationId must be non-zero" }
        require(bucketId >= 0) { "bucketId must be non-negative" }
        require(cellContent.isNotEmpty()) { "At least one product cell is required" }
        require(cellCountContent.size == cellContent.size) {
            "cellCounts must contain one cardinality per product cell"
        }
        var packedBucket = 0L
        var bucketSpace = 1L
        for (axis in cellContent.indices) {
            val count = cellCountContent[axis]
            require(count >= MIN_CELLS_PER_AXIS) {
                "Each component must contain at least $MIN_CELLS_PER_AXIS cells"
            }
            require(cellContent[axis] in 0 until count) {
                "Product cell ${cellContent[axis]} is outside axis $axis bounds 0..${count - 1}"
            }
            bucketSpace *= count.toLong()
            require(bucketSpace <= Int.MAX_VALUE.toLong()) {
                "Product-cell space exceeds the supported Int bucket domain"
            }
            packedBucket = packedBucket * count.toLong() + cellContent[axis].toLong()
        }
        require(bucketId == packedBucket.toInt()) {
            "bucketId does not match the mixed-radix product cells"
        }
        require(fingerprintContent.size in MIN_WORD_COUNT..MAX_WORD_COUNT) {
            "Fingerprint size must be between 64 and 256 bits"
        }
        require(bandContent.size == BAND_COUNT) {
            "A semantic fingerprint must have exactly $BAND_COUNT bands"
        }
        require(boundaryConfidence.isFinite() && boundaryConfidence in 0f..1f) {
            "boundaryConfidence must be finite and between zero and one"
        }
        require(bandContent.contentEquals(splitIntoFourBands(fingerprintContent))) {
            "Bands do not represent four equal portions of the fingerprint"
        }
    }

    /** Number of bits that differ from [other]'s fingerprint. */
    fun hammingDistance(other: SemanticVectorSignature): Int {
        requireComparable(other)
        return hammingDistance(fingerprintContent, other.fingerprintContent)
    }

    /** `1 - HammingDistance / bitCount`, in the inclusive range 0..1. */
    fun hammingSimilarity(other: SemanticVectorSignature): Double =
        1.0 - hammingDistance(other).toDouble() / bitCount.toDouble()

    /** Number of the four locality-sensitive bands shared with [other]. */
    fun matchingBandCount(other: SemanticVectorSignature): Int {
        requireComparable(other)
        var matches = 0
        for (index in bandContent.indices) {
            if (bandContent[index] == other.bandContent[index]) matches++
        }
        return matches
    }

    /**
     * Similarity of product-cell coordinates normalized for each axis' cardinality.
     * Identical cells score 1; opposite extremes on every axis score 0.
     */
    fun normalizedBucketSimilarity(
        other: SemanticVectorSignature,
    ): Double {
        require(calibrationId == other.calibrationId) {
            "Semantic signatures from different calibrations are not comparable"
        }
        require(cellContent.size == other.cellContent.size) {
            "Semantic signatures must have the same component count"
        }
        require(cellCountContent.contentEquals(other.cellCountContent)) {
            "Semantic signatures must use the same product-cell cardinalities"
        }

        var normalizedDistance = 0.0
        for (index in cellContent.indices) {
            val count = cellCountContent[index]
            val left = cellContent[index]
            val right = other.cellContent[index]
            normalizedDistance += abs(left - right).toDouble() / (count - 1).toDouble()
        }
        return (1.0 - normalizedDistance / cellContent.size.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun requireComparable(other: SemanticVectorSignature) {
        require(calibrationId == other.calibrationId) {
            "Semantic signatures from different calibrations are not comparable"
        }
        require(fingerprintContent.size == other.fingerprintContent.size) {
            "Semantic fingerprints must have the same bit count"
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is SemanticVectorSignature &&
            calibrationId == other.calibrationId &&
            bucketId == other.bucketId &&
            boundaryConfidence == other.boundaryConfidence &&
            cellContent.contentEquals(other.cellContent) &&
            cellCountContent.contentEquals(other.cellCountContent) &&
            fingerprintContent.contentEquals(other.fingerprintContent) &&
            bandContent.contentEquals(other.bandContent)

    override fun hashCode(): Int {
        var result = calibrationId.hashCode()
        result = 31 * result + bucketId
        result = 31 * result + boundaryConfidence.hashCode()
        result = 31 * result + cellContent.contentHashCode()
        result = 31 * result + cellCountContent.contentHashCode()
        result = 31 * result + fingerprintContent.contentHashCode()
        result = 31 * result + bandContent.contentHashCode()
        return result
    }

    override fun toString(): String =
        "SemanticVectorSignature(calibrationId=$calibrationId, bucketId=$bucketId, " +
            "cells=${cellContent.contentToString()}, cellCounts=${cellCountContent.contentToString()}, " +
            "bitCount=$bitCount, " +
            "boundaryConfidence=$boundaryConfidence)"

    companion object {
        const val BAND_COUNT: Int = 4
        const val NO_CALIBRATION: Long = 0L
        private const val DEFAULT_CALIBRATION_ID: Long = 1L
        private const val DEFAULT_BUCKET_ID: Int = 0
        private const val DEFAULT_CELL: Int = 0
        private const val DEFAULT_CELL_COUNT: Int = 2
        private const val DEFAULT_FINGERPRINT_WORD: Long = 0L
        private const val DEFAULT_BOUNDARY_CONFIDENCE: Float = 0f
        private const val MIN_CELLS_PER_AXIS: Int = 2
        private const val MIN_WORD_COUNT: Int = 1
        private const val MAX_WORD_COUNT: Int = 4
        private const val serialVersionUID: Long = 1L

        /** Splits 64, 128, 192, or 256 bits into exactly four equal bands. */
        @JvmStatic
        fun splitIntoFourBands(fingerprint: LongArray): LongArray {
            require(fingerprint.size in MIN_WORD_COUNT..MAX_WORD_COUNT) {
                "Fingerprint size must be between 64 and 256 bits"
            }
            val bitCount = fingerprint.size * Long.SIZE_BITS
            val bandBits = bitCount / BAND_COUNT
            return LongArray(BAND_COUNT) { bandIndex ->
                var band = 0L
                val firstBit = bandIndex * bandBits
                for (bandBit in 0 until bandBits) {
                    val sourceBit = firstBit + bandBit
                    val isSet = (fingerprint[sourceBit / Long.SIZE_BITS] ushr
                        (sourceBit % Long.SIZE_BITS)) and 1L
                    if (isSet != 0L) band = band or (1L shl bandBit)
                }
                band
            }
        }

        /** Raw-word helper for routing stores that have not materialized signatures. */
        @JvmStatic
        fun hammingDistance(first: LongArray, second: LongArray): Int {
            require(first.size == second.size && first.size in MIN_WORD_COUNT..MAX_WORD_COUNT) {
                "Semantic fingerprints must have the same size between 64 and 256 bits"
            }
            var distance = 0
            for (index in first.indices) {
                distance += java.lang.Long.bitCount(first[index] xor second[index])
            }
            return distance
        }

        /** Raw-word normalized Hamming similarity in the inclusive range 0..1. */
        @JvmStatic
        fun hammingSimilarity(first: LongArray, second: LongArray): Double =
            1.0 - hammingDistance(first, second).toDouble() /
                (first.size * Long.SIZE_BITS).toDouble()
    }
}
