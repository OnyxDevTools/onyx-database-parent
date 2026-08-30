package com.onyx.persistence.query

import com.onyx.persistence.annotations.SearchSupport
import java.io.Serializable

/** High-level search modes exposed by the embedded database and remote clients. */
enum class SearchMode {
    LEXICAL,
    SEMANTIC,
    HYBRID;

    internal val usesLexical: Boolean
        get() = this == LEXICAL || this == HYBRID

    internal val usesSemantic: Boolean
        get() = this == SEMANTIC || this == HYBRID
}

/** Controls whether every lexical term or any lexical term may admit a result. */
enum class SearchMatch {
    ALL,
    ANY
}

/** Whether an entity capability can execute this requested strategy. */
fun SearchSupport.supports(mode: SearchMode): Boolean = when (mode) {
    SearchMode.LEXICAL -> supportsLexical
    SearchMode.SEMANTIC -> supportsSemantic
    SearchMode.HYBRID -> this == SearchSupport.BOTH
}

/**
 * Options for the ergonomic natural-language search API.
 *
 * The legacy `search(text, minScore)` overload remains exhaustive lexical search. Supplying this
 * options object opts into bounded lexical, semantic-HNSW, or hybrid execution.
 */
data class SearchOptions @JvmOverloads constructor(
    val mode: SearchMode = SearchMode.HYBRID,
    val match: SearchMatch = SearchMatch.ANY,
    val minScore: Float? = null,
    val maxCandidates: Int = DEFAULT_SEARCH_CANDIDATES,
) : Serializable {
    init {
        validateSearchOptions(mode, minScore, maxCandidates)
    }
}

/** Versioned, fail-closed value carried by the dedicated `SEARCH` query operator. */
data class SearchQuery @JvmOverloads constructor(
    val text: String,
    val mode: SearchMode,
    val match: SearchMatch = SearchMatch.ANY,
    val minScore: Float? = null,
    val maxCandidates: Int = DEFAULT_SEARCH_CANDIDATES,
) : Serializable {
    init {
        require(text.isNotBlank()) { "Search text must not be blank" }
        validateSearchOptions(mode, minScore, maxCandidates)
    }

    @Suppress("unused")
    private constructor() : this("__transport__", SearchMode.HYBRID)

    constructor(text: String, options: SearchOptions) : this(
        text = text,
        mode = options.mode,
        match = options.match,
        minScore = options.minScore,
        maxCandidates = options.maxCandidates,
    )
}

/** Revalidates typed values and decodes the generic map used by JSON/MessagePack transports. */
fun resolveSearchQuery(value: Any?): SearchQuery = when (value) {
    is SearchQuery -> SearchQuery(
        text = value.text,
        mode = value.mode,
        match = value.match,
        minScore = value.minScore,
        maxCandidates = value.maxCandidates,
    )
    is Map<*, *> -> value.decodeSearchQuery()
    else -> throw IllegalArgumentException("SEARCH requires a SearchQuery object")
}

private fun Map<*, *>.decodeSearchQuery(): SearchQuery {
    val wire = buildMap<String, Any?>(size) {
        this@decodeSearchQuery.forEach { (key, value) ->
            require(key is String) { "SearchQuery field names must be strings" }
            put(key, value)
        }
    }
    val unknownFields = wire.keys - SEARCH_QUERY_WIRE_FIELDS
    require(unknownFields.isEmpty()) {
        "Unknown SearchQuery field(s): ${unknownFields.sorted().joinToString()}"
    }
    SEARCH_QUERY_FIELD_ALIASES.forEach { aliases ->
        require(aliases.count(wire::containsKey) <= 1) {
            "SearchQuery field aliases cannot be supplied together: ${aliases.joinToString()}"
        }
    }
    val text = wire.value("text", "queryText", "query_text")
    require(text is String) { "SearchQuery.text must be a string" }

    return SearchQuery(
        text = text,
        mode = parseMode(wire.value("mode")),
        match = parseMatch(wire.value("match")),
        minScore = wire.value("minScore", "min_score")?.let {
            val score = when (it) {
                is Number -> it.toFloat()
                is String -> it.trim().toFloatOrNull()
                else -> null
            } ?: throw IllegalArgumentException("SearchQuery.minScore must be numeric")
            score
        },
        maxCandidates = wire.value("maxCandidates", "max_candidates")?.let {
            val candidateCount = when (it) {
                is Number -> it.toDouble().takeIf(Double::isFinite)?.let { number ->
                    require(number == kotlin.math.floor(number)) {
                        "SearchQuery.maxCandidates must be an integer"
                    }
                    number.toLong()
                }
                is String -> it.trim().toLongOrNull()
                else -> null
            } ?: throw IllegalArgumentException("SearchQuery.maxCandidates must be an integer")
            require(candidateCount in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                "SearchQuery.maxCandidates is outside the Int range"
            }
            candidateCount.toInt()
        } ?: DEFAULT_SEARCH_CANDIDATES,
    )
}

private fun parseMode(value: Any?): SearchMode {
    val normalized = when (value) {
        is SearchMode -> return value
        is Enum<*> -> value.name
        is String -> value
        null -> throw IllegalArgumentException("SearchQuery.mode is required")
        else -> throw IllegalArgumentException("SearchQuery.mode must be lexical, semantic, or hybrid")
    }.trim().uppercase()
    return when (normalized) {
        "LEXICAL" -> SearchMode.LEXICAL
        "SEMANTIC" -> SearchMode.SEMANTIC
        "HYBRID", "BOTH" -> SearchMode.HYBRID
        else -> throw IllegalArgumentException("SearchQuery.mode must be lexical, semantic, or hybrid")
    }
}

private fun parseMatch(value: Any?): SearchMatch {
    val normalized = when (value) {
        is SearchMatch -> return value
        is Enum<*> -> value.name
        is String -> value
        null -> return SearchMatch.ANY
        else -> throw IllegalArgumentException("SearchQuery.match must be all or any")
    }.trim().uppercase()
    return when (normalized) {
        "ALL" -> SearchMatch.ALL
        "ANY" -> SearchMatch.ANY
        else -> throw IllegalArgumentException("SearchQuery.match must be all or any")
    }
}

private fun Map<String, Any?>.value(vararg names: String): Any? {
    names.forEach { name -> if (containsKey(name)) return get(name) }
    return null
}

private fun validateSearchOptions(mode: SearchMode, minScore: Float?, maxCandidates: Int) {
    require(minScore == null || minScore.isFinite() && minScore in 0f..1f) {
        "minScore must be finite and between 0 and 1"
    }
    require(maxCandidates in 1..MAX_SEARCH_CANDIDATES) {
        "maxCandidates must be between 1 and $MAX_SEARCH_CANDIDATES"
    }
    require(mode != SearchMode.HYBRID || maxCandidates >= 2) {
        "Hybrid search requires maxCandidates to be at least 2"
    }
}

const val DEFAULT_SEARCH_CANDIDATES: Int = 1_000
const val MAX_SEARCH_CANDIDATES: Int = 5_000

private val SEARCH_QUERY_FIELD_ALIASES = listOf(
    listOf("text", "queryText", "query_text"),
    listOf("minScore", "min_score"),
    listOf("maxCandidates", "max_candidates"),
)

private val SEARCH_QUERY_WIRE_FIELDS = SEARCH_QUERY_FIELD_ALIASES.flatten().toSet() +
    setOf("mode", "match")
