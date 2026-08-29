package com.onyx.persistence.query

import com.onyx.vector.SemanticVectorSignature
import java.io.Serializable
import java.lang.reflect.Array as ReflectArray
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs

/**
 * Query-side routing metadata for vector-managed records. Text uses deterministic sparse
 * lexical features; [semantic] uses PCA cells plus SimHash bands. Candidate texts may be
 * re-embedded and reranked by the caller without persisting full-precision vectors. Native HNSW
 * uses its separate [HnswSearchQuery] contract and may retain normalized int8 vectors. A
 * lexical-only query remains exhaustive unless it is used with the dedicated
 * `SEARCH_CANDIDATES` operator.
 */
data class VectorSearchQuery @JvmOverloads constructor(
    val text: String? = null,
    val semantic: SemanticVectorSignature? = null,
    val minScore: Float? = null,
    val nearbyBucketRadius: Int = 1,
    val maxCandidates: Int = 1_000,
    val requireAllTerms: Boolean = true
) : Serializable {
    init {
        require(minScore == null || minScore.isFinite()) { "minScore must be finite" }
        require(nearbyBucketRadius >= 0) { "nearbyBucketRadius must be non-negative" }
        require(maxCandidates in 1..MAX_VECTOR_SEARCH_CANDIDATES) {
            "maxCandidates must be between 1 and $MAX_VECTOR_SEARCH_CANDIDATES"
        }
    }
}

internal fun resolveVectorSearchQuery(value: Any?): VectorSearchQuery? = when (value) {
    null -> null
    // Reconstruct reflective OTHER transport values so constructor invariants are applied again.
    is VectorSearchQuery -> VectorSearchQuery(
        text = value.text,
        semantic = value.semantic?.revalidated(),
        minScore = value.minScore,
        nearbyBucketRadius = value.nearbyBucketRadius,
        maxCandidates = value.maxCandidates,
        requireAllTerms = value.requireAllTerms,
    )
    is FullTextQuery -> VectorSearchQuery(text = value.queryText, minScore = value.minScore)
    is SemanticVectorSignature -> VectorSearchQuery(semantic = value)
    is String -> value.takeIf { it.isNotBlank() }?.let { VectorSearchQuery(text = it) }
    is Map<*, *> -> value.decodeVectorSearchQuery()
    else -> VectorSearchQuery(text = value.toString())
}

private fun SemanticVectorSignature.revalidated(): SemanticVectorSignature =
    SemanticVectorSignature(
        calibrationId = calibrationId,
        bucketId = bucketId,
        cells = cells,
        cellCounts = cellCounts,
        fingerprint = fingerprint,
        bands = bands,
        boundaryConfidence = boundaryConfidence,
    )

/** Decodes the generic map produced by JSON-backed remote/Cloud query transports. */
private fun Map<*, *>.decodeVectorSearchQuery(): VectorSearchQuery {
    val wire = stringKeyed("VectorSearchQuery")

    if (wire.looksLikeSemanticSignature()) {
        return VectorSearchQuery(semantic = wire.decodeSemanticSignature())
    }

    val text = wire.value("text", "queryText", "query_text")?.let { raw ->
        require(raw is String) { "VectorSearchQuery.text must be a string" }
        raw.takeIf(String::isNotBlank)
    }
    val semantic = wire.value("semantic", "semanticSignature", "semantic_signature")?.let { raw ->
        when (raw) {
            is SemanticVectorSignature -> raw
            is Map<*, *> -> raw.stringKeyed("VectorSearchQuery.semantic").decodeSemanticSignature()
            else -> throw IllegalArgumentException("VectorSearchQuery.semantic must be an object")
        }
    }

    require(text != null || semantic != null) {
        "VectorSearchQuery must contain non-blank text and/or a semantic signature"
    }

    return VectorSearchQuery(
        text = text,
        semantic = semantic,
        minScore = wire.optionalFloat("minScore", "min_score"),
        nearbyBucketRadius = wire.optionalInt(1, "nearbyBucketRadius", "nearby_bucket_radius"),
        maxCandidates = wire.optionalInt(1_000, "maxCandidates", "max_candidates"),
        requireAllTerms = wire.optionalBoolean(true, "requireAllTerms", "require_all_terms")
    )
}

/** Runtime-only discriminator passed from the dedicated `SEARCH_CANDIDATES` plan. */
internal data class BoundedLexicalSearchQuery(val query: VectorSearchQuery)

private fun Map<String, Any?>.decodeSemanticSignature(): SemanticVectorSignature {
    val fingerprint = requiredLongArray("fingerprint", "fingerprintWords", "fingerprint_words")
    val bands = value("bands", "semanticBands", "semantic_bands")?.let {
        longArray(it, "SemanticVectorSignature.bands")
    } ?: SemanticVectorSignature.splitIntoFourBands(fingerprint)

    return SemanticVectorSignature(
        calibrationId = requiredLong("calibrationId", "calibration_id"),
        bucketId = requiredInt("bucketId", "bucket_id"),
        cells = requiredIntArray("cells"),
        cellCounts = requiredIntArray("cellCounts", "cell_counts"),
        fingerprint = fingerprint,
        bands = bands,
        boundaryConfidence = optionalFloat("boundaryConfidence", "boundary_confidence") ?: 0f
    )
}

private fun Map<String, Any?>.looksLikeSemanticSignature(): Boolean =
    has("calibrationId", "calibration_id") &&
        has("bucketId", "bucket_id") &&
        has("fingerprint", "fingerprintWords", "fingerprint_words")

private fun Map<*, *>.stringKeyed(type: String): Map<String, Any?> = buildMap(size) {
    this@stringKeyed.forEach { (key, value) ->
        require(key is String) { "$type field names must be strings" }
        put(key, value)
    }
}

private fun Map<String, Any?>.has(vararg names: String): Boolean = names.any(::containsKey)

private fun Map<String, Any?>.value(vararg names: String): Any? {
    names.forEach { name -> if (containsKey(name)) return get(name) }
    return null
}

private fun Map<String, Any?>.requiredInt(vararg names: String): Int {
    val field = names.first()
    return value(*names)?.let { integralInt(it, "SemanticVectorSignature.$field") }
        ?: throw IllegalArgumentException("SemanticVectorSignature.$field is required")
}

private fun Map<String, Any?>.requiredLong(vararg names: String): Long {
    val field = names.first()
    return value(*names)?.let { wireLong(it, "SemanticVectorSignature.$field") }
        ?: throw IllegalArgumentException("SemanticVectorSignature.$field is required")
}

private fun Map<String, Any?>.requiredIntArray(vararg names: String): IntArray {
    val field = names.first()
    val raw = value(*names)
        ?: throw IllegalArgumentException("SemanticVectorSignature.$field is required")
    return values(raw, "SemanticVectorSignature.$field")
        .map { integralInt(it, "SemanticVectorSignature.$field") }
        .toIntArray()
}

private fun Map<String, Any?>.requiredLongArray(vararg names: String): LongArray {
    val field = names.first()
    val raw = value(*names)
        ?: throw IllegalArgumentException("SemanticVectorSignature.$field is required")
    return longArray(raw, "SemanticVectorSignature.$field")
}

private fun Map<String, Any?>.optionalInt(default: Int, vararg names: String): Int =
    value(*names)?.let { integralInt(it, "VectorSearchQuery.${names.first()}") } ?: default

private fun Map<String, Any?>.optionalFloat(vararg names: String): Float? =
    value(*names)?.let { finiteFloat(it, "VectorSearchQuery.${names.first()}") }

private fun Map<String, Any?>.optionalBoolean(default: Boolean, vararg names: String): Boolean {
    val raw = value(*names) ?: return default
    return when (raw) {
        is Boolean -> raw
        is String -> when (raw.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("VectorSearchQuery.${names.first()} must be a boolean")
        }
        else -> throw IllegalArgumentException("VectorSearchQuery.${names.first()} must be a boolean")
    }
}

private fun values(value: Any, field: String): List<Any> = when (value) {
    is Collection<*> -> value.map {
        requireNotNull(it) { "$field must not contain null values" }
    }
    is Iterable<*> -> value.map {
        requireNotNull(it) { "$field must not contain null values" }
    }
    else -> if (value.javaClass.isArray) {
        List(ReflectArray.getLength(value)) { index ->
            requireNotNull(ReflectArray.get(value, index)) { "$field must not contain null values" }
        }
    } else {
        listOf(value)
    }
}

private fun longArray(value: Any, field: String): LongArray =
    values(value, field).map { wireLong(it, field) }.toLongArray()

private fun integralInt(value: Any, field: String): Int {
    val long = integralLong(value, field, rejectUnsafeJsonNumber = false)
    require(long in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$field is outside the Int range" }
    return long.toInt()
}

private fun wireLong(value: Any, field: String): Long {
    if (value is String) {
        val text = value.trim()
        require(text.isNotEmpty()) { "$field must not be blank" }
        val unsignedHex = when {
            text.startsWith("0x", ignoreCase = true) -> text.substring(2)
            text.any { it in 'a'..'f' || it in 'A'..'F' } -> text
            else -> null
        }
        if (unsignedHex != null) {
            require(unsignedHex.isNotEmpty() && unsignedHex.all(Char::isHexDigit)) {
                "$field must be a signed decimal or unsigned hexadecimal 64-bit value"
            }
            val parsed = BigInteger(unsignedHex, 16)
            require(parsed.bitLength() <= Long.SIZE_BITS) { "$field exceeds 64 bits" }
            return parsed.toLong()
        }
    }
    return integralLong(value, field, rejectUnsafeJsonNumber = true)
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
    val float = when (value) {
        is Number -> value.toFloat()
        is String -> value.trim().toFloatOrNull()
        else -> null
    } ?: throw IllegalArgumentException("$field must be numeric")
    require(float.isFinite()) { "$field must be finite" }
    return float
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L

/** Public hard ceiling for semantic/hybrid candidate admission and scoring work. */
const val MAX_VECTOR_SEARCH_CANDIDATES: Int = 5_000
