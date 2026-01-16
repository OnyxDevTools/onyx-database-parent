package com.onyx.persistence.query

import com.onyx.descriptor.EntityDescriptor
import java.util.Locale

data class LuceneCriteriaQuery(
    val queryText: String,
    val minScore: Float?
)

internal fun buildLuceneCriteriaQuery(criteria: QueryCriteria, descriptor: EntityDescriptor): LuceneCriteriaQuery? {
    val result = buildCriteriaQuery(criteria, descriptor)
    if (!result.supported || !result.hasFullText) return null
    val queryText = result.queryText?.trim().orEmpty()
    if (queryText.isEmpty()) return null
    return LuceneCriteriaQuery(queryText, result.minScore)
}

private data class CriteriaQueryBuildResult(
    val queryText: String?,
    val minScore: Float?,
    val supported: Boolean,
    val hasFullText: Boolean
)

private fun buildCriteriaQuery(criteria: QueryCriteria, descriptor: EntityDescriptor): CriteriaQueryBuildResult {
    if (criteria.flip) {
        return CriteriaQueryBuildResult(null, null, supported = true, hasFullText = false)
    }

    val ownClause = buildClause(criteria, descriptor)
    if (!ownClause.supported) {
        return CriteriaQueryBuildResult(null, null, supported = false, hasFullText = ownClause.hasFullText)
    }

    val builder = StringBuilder()
    var hasFullText = ownClause.hasFullText
    var minScore = ownClause.minScore
    var clauseAdded = false

    ownClause.queryText?.let { clause ->
        builder.append(wrapClause(clause))
        clauseAdded = true
    }

    for (subCriteria in criteria.subCriteria) {
        val subResult = buildCriteriaQuery(subCriteria, descriptor)
        if (!subResult.supported) {
            return CriteriaQueryBuildResult(null, null, supported = false, hasFullText = hasFullText || subResult.hasFullText)
        }

        if (subResult.queryText.isNullOrBlank()) continue
        if (subResult.hasFullText) hasFullText = true
        if (subResult.minScore != null) {
            minScore = if (minScore == null) subResult.minScore else maxOf(minScore, subResult.minScore)
        }

        if (clauseAdded) {
            val operator = if (subCriteria.isOr) "OR" else "AND"
            builder.append(" ").append(operator).append(" ")
        }

        builder.append(wrapClause(subResult.queryText))
        clauseAdded = true
    }

    if (!clauseAdded) {
        return CriteriaQueryBuildResult(null, minScore, supported = true, hasFullText = hasFullText)
    }

    var queryText = builder.toString()
    if (criteria.isNot) {
        queryText = "NOT (${queryText})"
    }

    return CriteriaQueryBuildResult(queryText, minScore, supported = true, hasFullText = hasFullText)
}

private data class ClauseBuildResult(
    val queryText: String?,
    val minScore: Float?,
    val supported: Boolean,
    val hasFullText: Boolean
)

private fun buildClause(criteria: QueryCriteria, descriptor: EntityDescriptor): ClauseBuildResult {
    val attribute = criteria.attribute
        ?: return ClauseBuildResult(null, null, supported = true, hasFullText = false)

    if (attribute == Query.FULL_TEXT_ATTRIBUTE) {
        val fullTextQuery = resolveFullTextQuery(criteria.value)
        val queryText = fullTextQuery?.queryText?.trim().orEmpty()
        return if (queryText.isEmpty()) {
            ClauseBuildResult(null, fullTextQuery?.minScore, supported = true, hasFullText = true)
        } else {
            ClauseBuildResult("${CONTENT_FIELD}:(${queryText})", fullTextQuery?.minScore, supported = true, hasFullText = true)
        }
    }

    if (criteria.isRelationship == true || attribute.contains(".")) {
        return ClauseBuildResult(null, null, supported = false, hasFullText = false)
    }

    val identifierName = descriptor.identifier?.name
    val partitionName = descriptor.partition?.name
    val knownAttribute = descriptor.attributes.containsKey(attribute) ||
        attribute == identifierName ||
        attribute == partitionName
    if (!knownAttribute) {
        return ClauseBuildResult(null, null, supported = false, hasFullText = false)
    }

    val operator = criteria.operator ?: return ClauseBuildResult(null, null, supported = false, hasFullText = false)
    val fieldName = when (operator) {
        QueryCriteriaOperator.CONTAINS_IGNORE_CASE,
        QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE -> attribute + LOWERCASE_SUFFIX
        else -> attribute
    }
    val rawValue = criteria.value

    val clause = when (operator) {
        QueryCriteriaOperator.EQUAL -> exactMatchClause(fieldName, rawValue)
        QueryCriteriaOperator.NOT_EQUAL -> negateClause(exactMatchClause(fieldName, rawValue))
        QueryCriteriaOperator.STARTS_WITH -> wildcardClause(fieldName, rawValue, prefix = "", suffix = "*")
        QueryCriteriaOperator.NOT_STARTS_WITH -> negateClause(wildcardClause(fieldName, rawValue, prefix = "", suffix = "*"))
        QueryCriteriaOperator.CONTAINS -> wildcardClause(fieldName, rawValue, prefix = "*", suffix = "*")
        QueryCriteriaOperator.NOT_CONTAINS -> negateClause(wildcardClause(fieldName, rawValue, prefix = "*", suffix = "*"))
        QueryCriteriaOperator.CONTAINS_IGNORE_CASE -> wildcardClause(fieldName, rawValue, prefix = "*", suffix = "*", lowercase = true)
        QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE -> negateClause(wildcardClause(fieldName, rawValue, prefix = "*", suffix = "*", lowercase = true))
        QueryCriteriaOperator.LIKE,
        QueryCriteriaOperator.MATCHES -> exactMatchClause(fieldName, rawValue, wrapInParens = true)
        QueryCriteriaOperator.NOT_LIKE,
        QueryCriteriaOperator.NOT_MATCHES -> negateClause(exactMatchClause(fieldName, rawValue, wrapInParens = true))
        QueryCriteriaOperator.IN -> multiValueClause(fieldName, rawValue)
        QueryCriteriaOperator.NOT_IN -> negateClause(multiValueClause(fieldName, rawValue))
        QueryCriteriaOperator.IS_NULL -> negateClause("${fieldName}:*")
        QueryCriteriaOperator.NOT_NULL -> "${fieldName}:*"
        QueryCriteriaOperator.GREATER_THAN -> rangeClause(fieldName, Pair(rawValue, null), inclusiveLower = false, inclusiveUpper = true)
        QueryCriteriaOperator.GREATER_THAN_EQUAL -> rangeClause(fieldName, Pair(rawValue, null), inclusiveLower = true, inclusiveUpper = true)
        QueryCriteriaOperator.LESS_THAN -> rangeClause(fieldName, Pair(null, rawValue), inclusiveLower = true, inclusiveUpper = false)
        QueryCriteriaOperator.LESS_THAN_EQUAL -> rangeClause(fieldName, Pair(null, rawValue), inclusiveLower = true, inclusiveUpper = true)
        QueryCriteriaOperator.BETWEEN -> rangeClause(fieldName, rawValue, inclusiveLower = true, inclusiveUpper = true)
        QueryCriteriaOperator.NOT_BETWEEN -> negateClause(rangeClause(fieldName, rawValue, inclusiveLower = true, inclusiveUpper = true))
    }

    return ClauseBuildResult(clause, minScore = null, supported = clause != null, hasFullText = false)
}

private fun exactMatchClause(fieldName: String, value: Any?, wrapInParens: Boolean = false): String? {
    val values = extractValues(value)
    if (values.isEmpty()) return null
    val clause = values.joinToString(" OR ") { "${fieldName}:${escapeLuceneTerm(it)}" }
    return if (wrapInParens && values.size > 1) "(${clause})" else clause
}

private fun multiValueClause(fieldName: String, value: Any?): String? {
    val values = extractValues(value)
    if (values.isEmpty()) return null
    return values.joinToString(" OR ") { "${fieldName}:${escapeLuceneTerm(it)}" }.let { "(${it})" }
}

private fun wildcardClause(
    fieldName: String,
    value: Any?,
    prefix: String,
    suffix: String,
    lowercase: Boolean = false
): String? {
    val values = extractValues(value)
    if (values.isEmpty()) return null
    val clause = values.joinToString(" OR ") { raw ->
        val normalized = if (lowercase) raw.lowercase(Locale.US) else raw
        "${fieldName}:${prefix}${escapeLuceneTerm(normalized)}${suffix}"
    }
    return if (values.size > 1) "(${clause})" else clause
}

private fun rangeClause(
    fieldName: String,
    range: Any?,
    inclusiveLower: Boolean,
    inclusiveUpper: Boolean
): String? {
    val (lowerValue, upperValue) = extractRangeBounds(range)
    val lowerToken = lowerValue?.let { escapeLuceneTerm(it) } ?: "*"
    val upperToken = upperValue?.let { escapeLuceneTerm(it) } ?: "*"
    val left = if (inclusiveLower) "[" else "{"
    val right = if (inclusiveUpper) "]" else "}"
    return "${fieldName}:${left}${lowerToken} TO ${upperToken}${right}"
}

private fun negateClause(clause: String?): String? {
    if (clause.isNullOrBlank()) return null
    return "NOT (${clause})"
}

private fun wrapClause(clause: String): String {
    return if (clause.contains(' ') || clause.startsWith("NOT ")) "(${clause})" else clause
}

private fun extractValues(value: Any?): List<String> = when (value) {
    null -> emptyList()
    is Collection<*> -> value.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
    is Array<*> -> value.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
    is IntArray -> value.map { it.toString() }
    is LongArray -> value.map { it.toString() }
    is FloatArray -> value.map { it.toString() }
    is DoubleArray -> value.map { it.toString() }
    else -> listOf(value.toString())
}

private fun extractRangeBounds(range: Any?): Pair<String?, String?> {
    return when (range) {
        is Pair<*, *> -> Pair(range.first?.toString(), range.second?.toString())
        else -> Pair(range?.toString(), null)
    }
}

private fun escapeLuceneTerm(value: String): String {
    val specialChars = setOf(
        '+', '-', '!', '(', ')', '{', '}', '[', ']', '^', '"', '~', '*', '?', ':', '\\', '/'
    )
    val builder = StringBuilder()
    for (ch in value) {
        if (specialChars.contains(ch)) {
            builder.append('\\')
        }
        builder.append(ch)
    }
    return builder.toString()
}

private const val CONTENT_FIELD = "content"
private const val LOWERCASE_SUFFIX = "__lc"
