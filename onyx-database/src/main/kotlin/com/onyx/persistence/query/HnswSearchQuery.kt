package com.onyx.persistence.query

import com.onyx.vector.QuantizedCosineVector
import java.io.Serializable
import java.lang.reflect.Array as ReflectArray
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs

/**
 * Explicit, bounded native-HNSW candidate request.
 *
 * [calibrationId] identifies an embedding model/vector space. The database never joins graph
 * edges across calibration IDs, even when dimensions happen to match. [efSearch] is a hard
 * bound on level-zero distance evaluations, not merely a tuning hint.
 */
class HnswSearchQuery @JvmOverloads constructor(
    val calibrationId: Long,
    vector: FloatArray,
    val maxCandidates: Int = DEFAULT_HNSW_CANDIDATES,
    val efSearch: Int = maxOf(DEFAULT_HNSW_EF_SEARCH, maxCandidates),
    val minScore: Float? = null,
    val formatVersion: Int = HNSW_QUERY_FORMAT_VERSION,
) : Serializable {
    private val vectorContent = vector.copyOf()

    val vector: FloatArray
        get() = vectorContent.copyOf()

    internal val quantizedVector: QuantizedCosineVector
        get() = QuantizedCosineVector.fromDense(vectorContent)

    init {
        // Validate eagerly while keeping the derived helper out of Java serialization.
        QuantizedCosineVector.fromDense(vectorContent)
        require(formatVersion == HNSW_QUERY_FORMAT_VERSION) {
            "Unsupported HNSW query formatVersion $formatVersion; expected $HNSW_QUERY_FORMAT_VERSION"
        }
        require(calibrationId != 0L) { "HNSW calibrationId must be non-zero" }
        require(maxCandidates in 1..MAX_HNSW_CANDIDATES) {
            "maxCandidates must be between 1 and $MAX_HNSW_CANDIDATES"
        }
        require(efSearch in maxCandidates..MAX_HNSW_EF_SEARCH) {
            "efSearch must be between maxCandidates and $MAX_HNSW_EF_SEARCH"
        }
        require(minScore == null || minScore.isFinite() && minScore in -1f..1f) {
            "minScore must be finite and between -1 and 1"
        }
    }

    /** Used only by Onyx's reflective RMI query transport decoder. */
    @Suppress("unused")
    private constructor() : this(
        calibrationId = 1L,
        vector = floatArrayOf(1f),
        maxCandidates = 1,
        efSearch = 1,
    )

    private companion object {
        const val serialVersionUID: Long = 1L
    }
}

/** Resolves the typed or schema-free map form accepted by remote query transports. */
internal fun resolveHnswSearchQuery(value: Any?): HnswSearchQuery = when (value) {
    // BufferStream creates OTHER values through the private transport constructor and assigns
    // fields reflectively, bypassing init. Reconstruct every typed value before use so transport
    // bytes cannot bypass dimension, finiteness, calibration or work-budget validation.
    is HnswSearchQuery -> HnswSearchQuery(
        calibrationId = value.calibrationId,
        vector = value.vector,
        maxCandidates = value.maxCandidates,
        efSearch = value.efSearch,
        minScore = value.minScore,
        formatVersion = value.formatVersion,
    )
    is Map<*, *> -> value.decodeHnswSearchQuery()
    else -> throw IllegalArgumentException(
        "HNSW_CANDIDATES requires an HnswSearchQuery formatVersion $HNSW_QUERY_FORMAT_VERSION object"
    )
}

private fun Map<*, *>.decodeHnswSearchQuery(): HnswSearchQuery {
    val wire = buildMap<String, Any?>(size) {
        this@decodeHnswSearchQuery.forEach { (key, value) ->
            require(key is String) { "HnswSearchQuery field names must be strings" }
            put(key, value)
        }
    }
    val vectorValue = wireValue(wire, "vector", "embedding")
        ?: throw IllegalArgumentException("HnswSearchQuery.vector is required")
    val vector = wireVector(vectorValue, "HnswSearchQuery.vector")
    return HnswSearchQuery(
        calibrationId = requiredWireLong(wire, "calibrationId", "calibration_id"),
        vector = vector,
        maxCandidates = optionalInt(wire, DEFAULT_HNSW_CANDIDATES, "maxCandidates", "max_candidates"),
        efSearch = optionalInt(
            wire,
            maxOf(
                DEFAULT_HNSW_EF_SEARCH,
                optionalInt(wire, DEFAULT_HNSW_CANDIDATES, "maxCandidates", "max_candidates")
            ),
            "efSearch",
            "ef_search"
        ),
        minScore = wireValue(wire, "minScore", "min_score")?.let {
            finiteFloat(it, "HnswSearchQuery.minScore")
        },
        formatVersion = optionalInt(
            wire,
            HNSW_QUERY_FORMAT_VERSION,
            "formatVersion",
            "format_version"
        ),
    )
}

private fun wireValue(values: Map<String, Any?>, vararg names: String): Any? {
    names.forEach { name -> if (values.containsKey(name)) return values[name] }
    return null
}

private fun optionalInt(values: Map<String, Any?>, default: Int, vararg names: String): Int =
    wireValue(values, *names)?.let { integralLong(it, "HnswSearchQuery.${names.first()}", false) }
        ?.also { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }
        ?.toInt()
        ?: default

private fun requiredWireLong(values: Map<String, Any?>, vararg names: String): Long {
    val raw = wireValue(values, *names)
        ?: throw IllegalArgumentException("HnswSearchQuery.${names.first()} is required")
    if (raw is String) {
        val text = raw.trim()
        require(text.isNotEmpty()) { "HnswSearchQuery.${names.first()} must not be blank" }
        val unsignedHex = when {
            text.startsWith("0x", ignoreCase = true) -> text.substring(2)
            text.any { it in 'a'..'f' || it in 'A'..'F' } -> text
            else -> null
        }
        if (unsignedHex != null) {
            require(unsignedHex.isNotEmpty() && unsignedHex.all(Char::isHexDigit)) {
                "HnswSearchQuery.${names.first()} must be a signed decimal or unsigned hexadecimal 64-bit value"
            }
            return BigInteger(unsignedHex, 16).also {
                require(it.bitLength() <= Long.SIZE_BITS) {
                    "HnswSearchQuery.${names.first()} exceeds 64 bits"
                }
            }.toLong()
        }
    }
    return integralLong(raw, "HnswSearchQuery.${names.first()}", true)
}

private fun integralLong(value: Any, field: String, rejectUnsafeJsonNumber: Boolean): Long {
    if (value is Float || value is Double) {
        val double = value.toDouble()
        require(double.isFinite() && double == kotlin.math.floor(double)) { "$field must be an integer" }
        if (rejectUnsafeJsonNumber) {
            require(abs(double) <= MAX_SAFE_JSON_INTEGER.toDouble()) {
                "$field must be encoded as a decimal or hexadecimal string outside the JSON safe-integer range"
            }
        }
    }
    val decimal = when (value) {
        is BigDecimal -> value
        is BigInteger -> value.toBigDecimal()
        is Number -> value.toString().toBigDecimalOrNull()
        is String -> value.trim().toBigDecimalOrNull()
        else -> null
    } ?: throw IllegalArgumentException("$field must be an integer")
    return try {
        decimal.toBigIntegerExact().longValueExact()
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("$field must be an integer in the signed 64-bit range")
    }
}

private fun finiteFloat(value: Any, field: String): Float {
    val result = when (value) {
        is Number -> value.toFloat()
        is String -> value.trim().toFloatOrNull()
        else -> null
    } ?: throw IllegalArgumentException("$field must be numeric")
    require(result.isFinite()) { "$field must be finite" }
    return result
}

private fun wireVector(value: Any, field: String): FloatArray {
    if (value.javaClass.isArray) {
        val size = ReflectArray.getLength(value)
        require(size <= QuantizedCosineVector.MAX_DIMENSIONS) {
            "$field must contain at most ${QuantizedCosineVector.MAX_DIMENSIONS} values"
        }
        return FloatArray(size) { index ->
            finiteFloat(
                requireNotNull(ReflectArray.get(value, index)) {
                    "$field must not contain null values"
                },
                field,
            )
        }
    }

    require(value is Iterable<*>) { "$field must be an array" }
    if (value is Collection<*>) {
        require(value.size <= QuantizedCosineVector.MAX_DIMENSIONS) {
            "$field must contain at most ${QuantizedCosineVector.MAX_DIMENSIONS} values"
        }
    }
    val result = ArrayList<Float>(
        (value as? Collection<*>)?.size ?: minOf(64, QuantizedCosineVector.MAX_DIMENSIONS)
    )
    val iterator = value.iterator()
    while (iterator.hasNext()) {
        // Consume at most max + 1 values from a lazy or untrusted Iterable.
        require(result.size < QuantizedCosineVector.MAX_DIMENSIONS) {
            "$field must contain at most ${QuantizedCosineVector.MAX_DIMENSIONS} values"
        }
        val raw = requireNotNull(iterator.next()) { "$field must not contain null values" }
        result += finiteFloat(raw, field)
    }
    return result.toFloatArray()
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

const val HNSW_QUERY_FORMAT_VERSION: Int = 1
const val DEFAULT_HNSW_CANDIDATES: Int = 1_000
const val DEFAULT_HNSW_EF_SEARCH: Int = 1_000
const val MAX_HNSW_CANDIDATES: Int = 5_000
const val MAX_HNSW_EF_SEARCH: Int = 20_000

private const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L
