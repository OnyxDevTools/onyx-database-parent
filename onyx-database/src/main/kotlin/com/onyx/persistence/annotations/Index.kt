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
 * @param type The index implementation. Vector indexes are created internally for
 * [com.onyx.persistence.VectorManagedEntity] and configured by the entity's entropy.
 */
@Target(AnnotationTarget.FIELD)
annotation class Index(
    val type: IndexType = IndexType.DEFAULT
)
