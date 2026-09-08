package com.onyx.persistence.annotations

/**
 * Declares the frozen numeric vector space returned by
 * [com.onyx.persistence.SearchVectorManagedEntity.searchVector].
 *
 * Change [version] whenever feature order, scaling, weights, or resolver behavior changes.
 * This changes the index configuration and isolates queries from the previous vector space.
 * [resolver] is descriptive source metadata for schema generators.
 */
@Target(AnnotationTarget.CLASS)
annotation class SearchVector(
    val dimensions: Int,
    val version: String,
    val resolver: String = "searchVector",
)
