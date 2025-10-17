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
 * @param type The type of index.  Default is standard, or a vector index
 *        Vector indexes are useful for fuzzy map searches or search indexing.
 */
@Target(AnnotationTarget.FIELD)
annotation class Index(
    val type: IndexType = IndexType.DEFAULT
)
