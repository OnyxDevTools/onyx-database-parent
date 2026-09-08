package com.onyx.vector

import com.onyx.persistence.annotations.SearchVector
import com.onyx.persistence.SearchVectorManagedEntity
import com.onyx.persistence.query.HnswSearchQuery
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Frozen cosine vector contract shared by computed record vectors and query vectors. */
data class SearchVectorConfiguration(
    val dimensions: Int,
    val version: String,
) {
    init {
        require(dimensions in 1..QuantizedCosineVector.MAX_DIMENSIONS) {
            "Search vector dimensions must be between 1 and ${QuantizedCosineVector.MAX_DIMENSIONS}"
        }
        require(version.isNotBlank()) { "Search vector version must not be blank" }
    }

    /** Independent of entity class names, so cloud clients can construct matching queries. */
    val calibrationId: Long = ByteBuffer.wrap(
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(StandardCharsets.UTF_8))
    ).long.let { if (it == 0L) 1L else it }

    internal val signature: String
        get() = "onyx-search-vector-v1|COSINE|$dimensions|$version"

    /** Validates before persistence or search; missing data must be represented by a null vector. */
    fun validate(vector: FloatArray) {
        require(vector.size == dimensions) {
            "Search vector has ${vector.size} dimensions; expected $dimensions for version $version"
        }
        QuantizedCosineVector.validateDense(vector)
    }

    /** Builds a bounded native query using exactly the record's declared vector space. */
    @JvmOverloads
    fun query(
        vector: FloatArray,
        maxCandidates: Int = 1_000,
        efSearch: Int = maxOf(1_000, maxCandidates),
        minScore: Float? = null,
    ): HnswSearchQuery {
        require(vector.size == dimensions) {
            "Search vector has ${vector.size} dimensions; expected $dimensions for version $version"
        }
        return HnswSearchQuery(calibrationId, vector, maxCandidates, efSearch, minScore)
    }

    companion object {
        private val configurations = object : ClassValue<SearchVectorConfiguration?>() {
            override fun computeValue(type: Class<*>): SearchVectorConfiguration? =
                type.getAnnotation(SearchVector::class.java)?.let {
                    require(SearchVectorManagedEntity::class.java.isAssignableFrom(type)) {
                        "${type.name} declares @SearchVector but does not extend SearchVectorManagedEntity"
                    }
                    require(it.resolver.isNotBlank()) { "Search vector resolver must not be blank" }
                    SearchVectorConfiguration(it.dimensions, it.version)
                }
        }

        fun forClass(entityClass: Class<*>): SearchVectorConfiguration? =
            configurations.get(entityClass)
    }
}
