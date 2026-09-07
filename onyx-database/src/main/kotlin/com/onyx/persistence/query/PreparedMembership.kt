package com.onyx.persistence.query

import com.onyx.extension.common.anyComparisonValue
import com.onyx.extension.common.compare
import java.math.BigDecimal
import java.math.BigInteger
import java.util.IdentityHashMap
import java.util.UUID

/**
 * A membership lookup for immutable, homogeneous scalar operands. Other runtime types retain
 * the comparison operator's directional coercion and equality rules.
 */
internal class PreparedMembership private constructor(
    val source: Any,
    private val valueType: Class<*>?,
    private val values: Set<Any?>,
) {
    /** Null means the caller must use the original comparison, rather than a hash lookup. */
    fun matches(value: Any?): Boolean? = when {
        value == null -> values.contains(null)
        valueType == null -> false
        value.javaClass == valueType -> values.contains(value)
        else -> null
    }

    companion object {
        fun create(source: Any?): PreparedMembership? {
            if (source !is Iterable<*> && (source == null || !source.javaClass.isArray)) return null

            var valueType: Class<*>? = null
            val values = HashSet<Any?>()
            val unsupported = source.anyComparisonValue { value ->
                if (value != null) {
                    val type = value.javaClass
                    if (!isImmutableScalar(type, value) || valueType != null && type != valueType) {
                        return@anyComparisonValue true
                    }
                    valueType = type
                }
                values.add(value)
                false
            }
            return if (unsupported) null else PreparedMembership(source!!, valueType, values)
        }

        private fun isImmutableScalar(type: Class<*>, value: Any): Boolean =
            type == String::class.java || type == Int::class.javaObjectType ||
                type == Long::class.javaObjectType || type == Short::class.javaObjectType ||
                type == Byte::class.javaObjectType || type == Float::class.javaObjectType ||
                type == Double::class.javaObjectType || type == Boolean::class.javaObjectType ||
                type == Char::class.javaObjectType || type == BigInteger::class.java ||
                type == BigDecimal::class.java || type == UUID::class.java || value is Enum<*>
    }
}

/**
 * Prepare once for the scan, then release the lookups even on failure. Keeping them out of cached
 * queries avoids stale membership when callers mutate a criterion's list, array, or scalar value
 * between executions. The immutable map is also shared by scans over parallel partitions.
 */
internal fun <T> Query.withPreparedMemberships(action: () -> T): T {
    if (isTerminated) return action()
    val previous = preparedMemberships
    var prepared: IdentityHashMap<QueryCriteria, PreparedMembership>? = null
    for (criterion in getAllCriteria()) {
        if (criterion.operator != QueryCriteriaOperator.IN && criterion.operator != QueryCriteriaOperator.NOT_IN) continue
        if (criterion.attribute == Query.FULL_TEXT_ATTRIBUTE) continue
        val membership = PreparedMembership.create(criterion.value) ?: continue
        if (prepared == null) prepared = IdentityHashMap()
        prepared[criterion] = membership
    }
    preparedMemberships = prepared
    return try {
        action()
    } finally {
        preparedMemberships = previous
    }
}

/** Compare a row value while leaving the public criterion and its serialized operands unchanged. */
internal fun Query.compareCriterion(
    criterion: QueryCriteria,
    attribute: Any?,
    comparisonValue: Any? = criterion.value,
): Boolean {
    val operator = criterion.operator!!
    if (operator == QueryCriteriaOperator.IN || operator == QueryCriteriaOperator.NOT_IN) {
        val prepared = preparedMemberships?.get(criterion)
        if (prepared != null && prepared.source === comparisonValue) {
            prepared.matches(attribute)?.let { matches ->
                return if (operator == QueryCriteriaOperator.IN) matches else !matches
            }
        }
    }
    return comparisonValue.compare(attribute, operator)
}
