package com.onyx.persistence.query

import java.io.Serializable
import java.lang.reflect.Array as ReflectArray
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Explicitly approximate admission from one ordinary secondary index.
 *
 * A single [values] item is the bounded equivalent of `EQUAL`; multiple items are the bounded
 * equivalent of `IN`. Values are visited in fair request-order rounds and the shared
 * [maxCandidates] budget caps physical posting visits across the whole list. Returned records and `resultsCount` describe
 * only the admitted candidate set, never the complete number of matching index rows. Exact
 * `EQUAL` and `IN` queries retain their exhaustive semantics.
 */
data class ApproximateIndexCandidateQuery @JvmOverloads constructor(
    val values: List<Any?>,
    val maxCandidates: Int = DEFAULT_APPROXIMATE_INDEX_CANDIDATES
) : Serializable {
    init {
        require(maxCandidates in 1..MAX_APPROXIMATE_INDEX_CANDIDATES) {
            "maxCandidates must be between 1 and $MAX_APPROXIMATE_INDEX_CANDIDATES"
        }
        require(values.isNotEmpty()) { "Approximate index candidates require at least one route value" }
        require(values.size <= MAX_APPROXIMATE_INDEX_ROUTE_VALUES) {
            "Approximate index candidate routes cannot exceed $MAX_APPROXIMATE_INDEX_ROUTE_VALUES values"
        }
        require(values.none { it == null }) { "Approximate index candidate route values cannot be null" }
    }

    constructor(
        value: Any,
        maxCandidates: Int = DEFAULT_APPROXIMATE_INDEX_CANDIDATES
    ) : this(listOf(value), maxCandidates)

    /** Used only by Onyx's reflective query transport decoder. */
    @Suppress("unused")
    private constructor() : this(listOf(0), 1)

    private companion object {
        const val serialVersionUID: Long = 1L
    }
}

/** Default admission budget when callers do not supply one. */
const val DEFAULT_APPROXIMATE_INDEX_CANDIDATES: Int = 1_000

/** Hard safety ceiling for admitted candidates and physical posting visits. */
const val MAX_APPROXIMATE_INDEX_CANDIDATES: Int = 5_000

/** Hard safety ceiling for `IN` route values and their point lookups. */
const val MAX_APPROXIMATE_INDEX_ROUTE_VALUES: Int = 5_000

/** Decodes generic JSON maps produced by REST and other schema-free transports. */
internal fun resolveApproximateIndexCandidateQuery(value: Any?): ApproximateIndexCandidateQuery =
    when (value) {
        // Reflective query transport assigns fields after running the valid placeholder ctor.
        is ApproximateIndexCandidateQuery -> ApproximateIndexCandidateQuery(
            values = value.values.toList(),
            maxCandidates = value.maxCandidates,
        )
        is Map<*, *> -> value.decodeApproximateIndexCandidateQuery()
        else -> throw IllegalArgumentException(
            "CANDIDATES requires an ApproximateIndexCandidateQuery object"
        )
    }

private fun Map<*, *>.decodeApproximateIndexCandidateQuery(): ApproximateIndexCandidateQuery {
    val wire = buildMap<String, Any?>(size) {
        this@decodeApproximateIndexCandidateQuery.forEach { (key, value) ->
            require(key is String) { "ApproximateIndexCandidateQuery field names must be strings" }
            put(key, value)
        }
    }
    val rawValues = when {
        wire.containsKey("values") -> wire["values"].asRouteValues()
        wire.containsKey("routeValues") -> wire["routeValues"].asRouteValues()
        wire.containsKey("route_values") -> wire["route_values"].asRouteValues()
        wire.containsKey("value") -> listOf(
            requireNotNull(wire["value"]) { "ApproximateIndexCandidateQuery.value cannot be null" }
        )
        else -> throw IllegalArgumentException("ApproximateIndexCandidateQuery.values is required")
    }
    val maxCandidates = when {
        wire.containsKey("maxCandidates") -> wire["maxCandidates"].candidateInt("maxCandidates")
        wire.containsKey("max_candidates") -> wire["max_candidates"].candidateInt("maxCandidates")
        else -> DEFAULT_APPROXIMATE_INDEX_CANDIDATES
    }
    return ApproximateIndexCandidateQuery(rawValues, maxCandidates)
}

private fun Any?.asRouteValues(): List<Any> {
    requireNotNull(this) { "ApproximateIndexCandidateQuery.values cannot be null" }
    return when (this) {
        is Collection<*> -> map {
            requireNotNull(it) { "Approximate index candidate route values cannot be null" }
        }
        is Iterable<*> -> map {
            requireNotNull(it) { "Approximate index candidate route values cannot be null" }
        }
        else -> if (javaClass.isArray) {
            List(ReflectArray.getLength(this)) { index ->
                requireNotNull(ReflectArray.get(this, index)) {
                    "Approximate index candidate route values cannot be null"
                }
            }
        } else {
            throw IllegalArgumentException("ApproximateIndexCandidateQuery.values must be an array")
        }
    }
}

private fun Any?.candidateInt(field: String): Int {
    val decimal = when (this) {
        is BigDecimal -> this
        is BigInteger -> toBigDecimal()
        is Number -> toString().toBigDecimalOrNull()
        is String -> trim().toBigDecimalOrNull()
        else -> null
    } ?: throw IllegalArgumentException("ApproximateIndexCandidateQuery.$field must be an integer")
    return try {
        decimal.toBigIntegerExact().intValueExact()
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("ApproximateIndexCandidateQuery.$field must be an Int")
    }
}
