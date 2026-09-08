package com.onyx.persistence

import com.onyx.vector.SearchVectorConfiguration

/**
 * Opt-in base for a numeric vector supplied by a getter and declared with
 * [com.onyx.persistence.annotations.SearchVector]. Existing [VectorManagedEntity] subclasses
 * keep their original member names and vector encoding.
 */
abstract class SearchVectorManagedEntity : VectorManagedEntity() {
    /**
     * Evaluated once before each write and during an explicit index rebuild. Returning null
     * removes any previous vector. The getter must be deterministic and side-effect free.
     */
    open val searchVector: FloatArray?
        get() = null

    /** The immutable vector-space identity used for indexing and query construction. */
    val searchVectorConfiguration: SearchVectorConfiguration?
        get() = SearchVectorConfiguration.forClass(javaClass)
}
