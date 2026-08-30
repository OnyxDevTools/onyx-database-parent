package com.onyx.vector

/**
 * Dense query vector plus the stable identifier of its model/vector space.
 *
 * The database quantizes the vector before persistence; the full-precision array is never retained
 * on a managed entity.
 */
class SearchEmbedding(
    val calibrationId: Long,
    vector: FloatArray,
) {
    private val vectorContent = vector.copyOf()

    val vector: FloatArray
        get() = vectorContent.copyOf()

    init {
        require(calibrationId != 0L) { "Search embedding calibrationId must be non-zero" }
        QuantizedCosineVector.fromDense(vectorContent)
    }
}

/**
 * Application-supplied text embedding integration used for automatic ingestion and queries.
 *
 * [entityType] lets one provider select a model/vector space per searchable table. The same model
 * and calibration identifier must be returned for stored text and query text.
 */
fun interface SearchEmbeddingProvider {
    fun embed(text: String, entityType: Class<*>): SearchEmbedding

    /**
     * Opts one vector-managed type into high-level semantic/hybrid queries and automatic vector
     * ownership. Unsupported types retain caller-managed vectors and use the low-level HNSW API.
     */
    fun supports(entityType: Class<*>): Boolean = true
}
