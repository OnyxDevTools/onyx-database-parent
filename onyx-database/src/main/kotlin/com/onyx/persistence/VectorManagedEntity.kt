package com.onyx.persistence

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.vector.PreparedVectorRepresentation
import com.onyx.vector.SemanticVectorSignature
import com.onyx.vector.VectorCalibration
import com.onyx.vector.VectorEntityEncoder
import com.onyx.vector.VectorManagedConfiguration
import com.onyx.vector.VectorRepresentation
import com.onyx.vector.VectorRepresentationCodec

/**
 * Base entity for managed sparse-vector search.
 *
 * Only compact routing data is persisted. Full-precision dense embeddings supplied during
 * ingestion are discarded; native HNSW optionally retains a normalized signed-int8 copy.
 */
abstract class VectorManagedEntity : ManagedEntity() {

    @Attribute(nullable = false, internal = true)
    @Index(type = IndexType.VECTOR)
    private var __vectorRepresentation: ByteArray = byteArrayOf()

    @Transient
    private var preparedVectorRepresentation: PreparedVectorRepresentation? = null

    /** Returns a defensive, decoded view of the persisted routing representation. */
    fun vectorRepresentation(): VectorRepresentation? =
        VectorRepresentationCodec.decodeOrNull(__vectorRepresentation)

    /**
     * Installs semantic routing metadata produced by a frozen calibration. Structured features
     * are regenerated from current entity attributes immediately before persistence.
     */
    fun vectorRepresentation(representation: VectorRepresentation?) {
        preparedVectorRepresentation = null
        __vectorRepresentation = representation?.let(VectorRepresentationCodec::encode) ?: byteArrayOf()
    }

    /** Encodes LSH metadata and an int8 HNSW vector, then discards the full-precision input. */
    fun semanticVector(embedding: FloatArray, calibration: VectorCalibration) {
        val configuration = VectorManagedConfiguration.forClass(javaClass)
        semanticSignature(calibration.encode(embedding, configuration.entropy))
        hnswVector(embedding, calibration.calibrationId)
    }

    /**
     * Installs a compact HNSW vector independently of semantic fingerprint calibration.
     *
     * The full-precision input is normalized, scalar-quantized to signed int8, and discarded.
     * [calibrationId] identifies an embedding model/vector space; HNSW never connects vectors
     * carrying different identifiers.
     */
    fun hnswVector(embedding: FloatArray, calibrationId: Long) {
        require(calibrationId != VectorRepresentation.NO_CALIBRATION) {
            "HNSW calibrationId must be non-zero"
        }
        preparedVectorRepresentation = null
        val configuration = VectorManagedConfiguration.forClass(javaClass)
        val quantized = com.onyx.vector.QuantizedCosineVector.fromDense(embedding).toByteArray()
        val existing = VectorRepresentationCodec.decodeOrNull(__vectorRepresentation)
            ?.takeIf {
                it.configurationId == configuration.configurationId &&
                    it.featureHashBits == configuration.entropy.bitCount
            }
        __vectorRepresentation = VectorRepresentationCodec.encode(
            (existing ?: VectorRepresentation(
                encodingVersion = configuration.encodingVersion,
                featureHashBits = configuration.entropy.bitCount,
                configurationId = configuration.configurationId,
            )).copy(
                hnswCalibrationId = calibrationId,
                hnswVector = quantized,
            )
        )
    }

    /** Installs already encoded semantic routing data, for example from an external embedder. */
    fun semanticSignature(signature: SemanticVectorSignature) {
        preparedVectorRepresentation = null
        val configuration = VectorManagedConfiguration.forClass(javaClass)
        require(signature.bitCount == configuration.entropy.bitCount) {
            "Semantic signature has ${signature.bitCount} bits; expected ${configuration.entropy.bitCount}"
        }
        val existing = VectorRepresentationCodec.decodeOrNull(__vectorRepresentation)
            ?.takeIf {
                it.configurationId == configuration.configurationId &&
                    it.featureHashBits == configuration.entropy.bitCount
            }
        __vectorRepresentation = VectorRepresentationCodec.encode(
            VectorRepresentation(
                encodingVersion = configuration.encodingVersion,
                featureHashBits = configuration.entropy.bitCount,
                configurationId = configuration.configurationId,
                calibrationId = signature.calibrationId,
                hnswCalibrationId = existing?.hnswCalibrationId ?: VectorRepresentation.NO_CALIBRATION,
                bucketId = signature.bucketId,
                boundaryConfidence = signature.boundaryConfidence,
                cells = signature.cells,
                cellCounts = signature.cellCounts,
                semanticFingerprint = signature.fingerprint,
                semanticBands = signature.bands,
                hnswVector = existing?.hnswVector ?: byteArrayOf(),
                featureWords = existing?.featureWords ?: longArrayOf()
            )
        )
    }

    fun semanticSignature(): SemanticVectorSignature? = vectorRepresentation()
        ?.takeIf { it.hasSemanticSignature }
        ?.let {
            SemanticVectorSignature(
                calibrationId = it.calibrationId,
                bucketId = it.bucketId,
                cells = it.cells,
                cellCounts = it.cellCounts,
                fingerprint = it.semanticFingerprint,
                bands = it.semanticBands,
                boundaryConfidence = it.boundaryConfidence,
            )
        }

    internal fun prepareVectorRepresentation(descriptor: EntityDescriptor): PreparedVectorRepresentation {
        val existing = VectorRepresentationCodec.decodeOrNull(__vectorRepresentation)
        return VectorEntityEncoder.prepare(this, descriptor, existing).also { prepared ->
            __vectorRepresentation = VectorRepresentationCodec.encode(prepared.representation)
            preparedVectorRepresentation = prepared
        }
    }

    /** Supplies prepared state once, then falls back to the persisted index value. */
    internal fun consumePreparedVectorIndexValue(): Any =
        preparedVectorRepresentation?.also { preparedVectorRepresentation = null }
            ?: __vectorRepresentation

    /** Peeks at prepared write state without consuming it during pre-record index validation. */
    internal fun preparedVectorIndexValue(): Any =
        preparedVectorRepresentation ?: __vectorRepresentation

    companion object {
        const val REPRESENTATION_FIELD: String = "__vectorRepresentation"
    }
}
