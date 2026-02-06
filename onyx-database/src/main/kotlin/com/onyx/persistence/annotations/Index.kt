package com.onyx.persistence.annotations

import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.annotations.values.VectorQuantization

/**
 * This annotation is used to indicate a class property as an indexed attribute
 *
 * Note: This must also include an com.onyx.persistence.annotations.Attribute annotation
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @Index
 * @Attribute(nullable = false, size = 200)
 * public long personID;
 *
 * @see com.onyx.persistence.annotations.Identifier
 *
 * All of these properties only apply to Vector Index which is used for text fuzzy search
 *
 * @param type The type of index.  Default is standard, or a vector index
 *        Vector indexes are useful for fuzzy map searches or search indexing.
 * @param embeddingDimensions The dimensionality of the embedding vectors for vector indexes.
 *        Once defined, this value cannot be changed as it affects the storage format.
 *        Default is 512.
 * @param minimumScore Search results with a cosine similarity score below this threshold will be discarded.
 *        Default is 0.18f.
 * @param maxNeighbors The HNSW M parameter - number of bi-directional links per node per layer.
 *        Higher values improve recall but increase memory usage and construction time.
 *        Default is 16.
 * @param searchRadius The HNSW efConstruction parameter - search width during index construction.
 *        Higher values improve index quality but increase construction time.
 *        Default is 128.
 * @param quantization The vector quantization mode for storage and similarity computation.
 *        NONE uses full float32 precision, INT8 uses 8-bit quantization, INT4 uses 4-bit quantization.
 *        Lower precision reduces memory usage but may slightly reduce accuracy.
 *        Default is NONE.
 */
@Target(AnnotationTarget.FIELD)
annotation class Index(
    val type: IndexType = IndexType.DEFAULT,
    val embeddingDimensions: Int = -1,
    val minimumScore: Float = -1f,
    val maxNeighbors: Int = 16,
    val searchRadius: Int = 128,
    val quantization: VectorQuantization = VectorQuantization.NONE
)
