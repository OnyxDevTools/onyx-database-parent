package com.onyx.persistence.context

/**
 * Optional, synchronous diagnostics for identifying the physical query path that actually ran.
 *
 * A [SchemaContext] may implement this interface in a test or benchmark. Normal contexts do not,
 * so reporting an event is only a type check and a nullable call on the query boundary; there is
 * no global listener, thread-local state, stack inspection, or per-record instrumentation.
 */
internal interface QueryExecutionObserver {
    fun onQueryExecution(event: QueryExecutionEvent)
}

/** Aggregate work performed by one bounded semantic fingerprint search. */
internal data class FingerprintSearchWork(
    val candidateLimit: Int,
    val postingVisitLimit: Int,
    val routeLookupLimit: Int,
    val postingVisits: Int,
    val routeLookups: Int,
    val candidateCount: Int,
    val evaluatedCandidateCount: Int
)

/** Optional aggregate diagnostics; unlike [QueryExecutionObserver], this is reported once per search. */
internal fun interface FingerprintSearchWorkObserver {
    fun onFingerprintSearchWork(work: FingerprintSearchWork)
}

/** Aggregate bounded work performed by one native HNSW candidate traversal. */
internal data class HnswSearchWork(
    val efSearch: Int,
    val maxCandidates: Int,
    val distanceEvaluations: Int,
    val upperLayerDistanceEvaluations: Int,
    val resultCount: Int,
    val exactFilteredScan: Boolean,
    val concurrentSearchesObserved: Int,
)

/** Optional aggregate diagnostics, reported once per HNSW search. */
internal fun interface HnswSearchWorkObserver {
    fun onHnswSearchWork(work: HnswSearchWork)
}

/** Aggregate work performed by one exact posting-driven guarded delete page. */
internal data class GuardedDeleteWork(
    val pageLimit: Int,
    val eligibleIndexCount: Int,
    val cardinalityProbePostingVisits: Int,
    val postingVisits: Int,
    val recordLookups: Int,
    val matchedReferenceCount: Int,
    val deletedCount: Int,
    val drivingAttribute: String,
)

/** Optional aggregate diagnostics, reported once for each posting-driven guarded delete page. */
internal fun interface GuardedDeleteWorkObserver {
    fun onGuardedDeleteWork(work: GuardedDeleteWork)
}

/** Physical query operations exposed to [QueryExecutionObserver]. */
internal enum class QueryExecutionEvent {
    VECTOR_INDEX_INTERACTOR_LOOKUP,
    VECTOR_INDEX_SCAN,
    VECTOR_FINGERPRINT_SCAN,
    FINGERPRINT_FEATURE_LOOKUP,
    FINGERPRINT_DOMAIN_LOOKUP,
    FINGERPRINT_MATCH_ALL,
    HNSW_SEARCH,
    FULL_TABLE_SCAN
}

/** Keeps the observer completely off the normal [SchemaContext] contract. */
internal fun SchemaContext.reportQueryExecution(event: QueryExecutionEvent) {
    (this as? QueryExecutionObserver)?.onQueryExecution(event)
}

internal fun SchemaContext.reportFingerprintSearchWork(work: FingerprintSearchWork) {
    (this as? FingerprintSearchWorkObserver)?.onFingerprintSearchWork(work)
}

internal fun SchemaContext.reportHnswSearchWork(work: HnswSearchWork) {
    (this as? HnswSearchWorkObserver)?.onHnswSearchWork(work)
}

internal fun SchemaContext.reportGuardedDeleteWork(work: GuardedDeleteWork) {
    (this as? GuardedDeleteWorkObserver)?.onGuardedDeleteWork(work)
}
