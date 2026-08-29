package com.onyx.vector

/**
 * Compact, persistence-safe routing representation for one entity.
 *
 * Full-precision dense embeddings are deliberately absent. [semanticFingerprint] and product-cell
 * coordinates support LSH routing; [hnswVector] optionally retains a normalized one-byte-per-
 * dimension embedding for native HNSW candidate traversal. [featureWords] contains sparse
 * interval/categorical/text feature fingerprints in row-major order.
 */
class VectorRepresentation(
    val encodingVersion: Int,
    val featureHashBits: Int,
    val configurationId: Long,
    val calibrationId: Long = NO_CALIBRATION,
    val hnswCalibrationId: Long = NO_CALIBRATION,
    val bucketId: Int = NO_BUCKET,
    val boundaryConfidence: Float = 0f,
    cells: IntArray = intArrayOf(),
    cellCounts: IntArray = intArrayOf(),
    semanticFingerprint: LongArray = longArrayOf(),
    semanticBands: LongArray = longArrayOf(),
    hnswVector: ByteArray = byteArrayOf(),
    featureWords: LongArray = longArrayOf()
) {
    private val cellContent: IntArray = cells.copyOf()
    private val cellCountContent: IntArray = cellCounts.copyOf()
    private val semanticFingerprintContent: LongArray = semanticFingerprint.copyOf()
    private val semanticBandContent: LongArray = semanticBands.copyOf()
    private val hnswVectorContent: ByteArray = hnswVector.copyOf()
    private val featureWordContent: LongArray = featureWords.copyOf()

    val cells: IntArray
        get() = cellContent.copyOf()

    val cellCounts: IntArray
        get() = cellCountContent.copyOf()

    val semanticFingerprint: LongArray
        get() = semanticFingerprintContent.copyOf()

    val semanticBands: LongArray
        get() = semanticBandContent.copyOf()

    /** Normalized signed-int8 embedding used only for bounded HNSW candidate traversal. */
    val hnswVector: ByteArray
        get() = hnswVectorContent.copyOf()

    val featureWords: LongArray
        get() = featureWordContent.copyOf()

    val featureWordCount: Int
        get() = featureHashBits / Long.SIZE_BITS

    val featureCount: Int
        get() = if (featureWordCount == 0) 0 else featureWordContent.size / featureWordCount

    val hasSemanticSignature: Boolean
        get() = calibrationId != NO_CALIBRATION && bucketId != NO_BUCKET && semanticFingerprintContent.isNotEmpty()

    val hasHnswVector: Boolean
        get() = hnswCalibrationId != NO_CALIBRATION && hnswVectorContent.isNotEmpty()

    init {
        require(encodingVersion > 0) { "encodingVersion must be positive" }
        require(featureHashBits in 64..256 && featureHashBits % Long.SIZE_BITS == 0) {
            "featureHashBits must be 64, 128, 192, or 256"
        }
        require(featureWordContent.size % featureWordCount == 0) {
            "featureWords must contain complete $featureWordCount-word fingerprints"
        }
        require(boundaryConfidence.isFinite() && boundaryConfidence in 0f..1f) {
            "boundaryConfidence must be finite and between zero and one"
        }
        if (semanticFingerprintContent.isEmpty()) {
            require(
                cellContent.isEmpty() && cellCountContent.isEmpty() && semanticBandContent.isEmpty() &&
                    bucketId == NO_BUCKET && calibrationId == NO_CALIBRATION
            ) {
                "Incomplete semantic signature"
            }
        } else {
            SemanticVectorSignature(
                calibrationId = calibrationId,
                bucketId = bucketId,
                cells = cellContent,
                cellCounts = cellCountContent,
                fingerprint = semanticFingerprintContent,
                bands = semanticBandContent,
                boundaryConfidence = boundaryConfidence,
            )
        }
        if (hnswVectorContent.isEmpty()) {
            require(hnswCalibrationId == NO_CALIBRATION) { "Incomplete HNSW vector" }
        } else {
            require(hnswCalibrationId != NO_CALIBRATION) { "HNSW calibrationId must be non-zero" }
            QuantizedCosineVector.fromBytes(hnswVectorContent)
        }
    }

    fun copy(
        encodingVersion: Int = this.encodingVersion,
        featureHashBits: Int = this.featureHashBits,
        configurationId: Long = this.configurationId,
        calibrationId: Long = this.calibrationId,
        hnswCalibrationId: Long = this.hnswCalibrationId,
        bucketId: Int = this.bucketId,
        boundaryConfidence: Float = this.boundaryConfidence,
        cells: IntArray = this.cells,
        cellCounts: IntArray = this.cellCounts,
        semanticFingerprint: LongArray = this.semanticFingerprint,
        semanticBands: LongArray = this.semanticBands,
        hnswVector: ByteArray = this.hnswVector,
        featureWords: LongArray = this.featureWords
    ): VectorRepresentation = VectorRepresentation(
        encodingVersion = encodingVersion,
        featureHashBits = featureHashBits,
        configurationId = configurationId,
        calibrationId = calibrationId,
        hnswCalibrationId = hnswCalibrationId,
        bucketId = bucketId,
        boundaryConfidence = boundaryConfidence,
        cells = cells,
        cellCounts = cellCounts,
        semanticFingerprint = semanticFingerprint,
        semanticBands = semanticBands,
        hnswVector = hnswVector,
        featureWords = featureWords,
    )

    override fun equals(other: Any?): Boolean =
        other is VectorRepresentation &&
            encodingVersion == other.encodingVersion &&
            featureHashBits == other.featureHashBits &&
            configurationId == other.configurationId &&
            calibrationId == other.calibrationId &&
            hnswCalibrationId == other.hnswCalibrationId &&
            bucketId == other.bucketId &&
            boundaryConfidence == other.boundaryConfidence &&
            cellContent.contentEquals(other.cellContent) &&
            cellCountContent.contentEquals(other.cellCountContent) &&
            semanticFingerprintContent.contentEquals(other.semanticFingerprintContent) &&
            semanticBandContent.contentEquals(other.semanticBandContent) &&
            hnswVectorContent.contentEquals(other.hnswVectorContent) &&
            featureWordContent.contentEquals(other.featureWordContent)

    override fun hashCode(): Int {
        var result = encodingVersion
        result = 31 * result + featureHashBits
        result = 31 * result + configurationId.hashCode()
        result = 31 * result + calibrationId.hashCode()
        result = 31 * result + hnswCalibrationId.hashCode()
        result = 31 * result + bucketId
        result = 31 * result + boundaryConfidence.hashCode()
        result = 31 * result + cellContent.contentHashCode()
        result = 31 * result + cellCountContent.contentHashCode()
        result = 31 * result + semanticFingerprintContent.contentHashCode()
        result = 31 * result + semanticBandContent.contentHashCode()
        result = 31 * result + hnswVectorContent.contentHashCode()
        result = 31 * result + featureWordContent.contentHashCode()
        return result
    }

    companion object {
        const val NO_CALIBRATION: Long = 0L
        const val NO_BUCKET: Int = -1
        const val SEMANTIC_BAND_COUNT: Int = 4
    }
}
