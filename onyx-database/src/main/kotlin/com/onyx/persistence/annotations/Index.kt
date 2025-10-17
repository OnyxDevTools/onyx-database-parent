package com.onyx.persistence.annotations

import com.onyx.persistence.annotations.values.IndexType

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
 * @param hashTableCount The number of LSH hash tables to use for vector indexes.
 *        More tables improve accuracy but increase index size.
 *        Default is 12.
 */
@Target(AnnotationTarget.FIELD)
annotation class Index(
    val type: IndexType = IndexType.DEFAULT,
    val embeddingDimensions: Int = -1,
    val minimumScore: Float = -1f,
    val hashTableCount: Int = -1
)
