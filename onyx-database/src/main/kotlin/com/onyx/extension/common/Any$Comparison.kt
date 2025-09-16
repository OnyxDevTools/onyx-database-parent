package com.onyx.extension.common

import com.onyx.exception.InvalidDataTypeForOperator
import com.onyx.persistence.query.QueryCriteriaOperator
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Compare without throwing exception
 *
 * @param `compare` First compare to compare
 * @param compareTo Second compare to compare
 * @param operator Operator to compare against
 *
 * @return If the criteria meet return true
 */
@JvmOverloads
fun Any?.forceCompare(compareTo: Any?, operator: QueryCriteriaOperator = QueryCriteriaOperator.EQUAL): Boolean = try {
    this.compare(compareTo, operator)
} catch (_: InvalidDataTypeForOperator) {
    false
}

/**
 * Generic method use to compareTo values with a given operator
 * If there is a type mismatch, the compare object will be cast to the compareTo type.
 * This will have implications on values that could loose precision for instance casting a long to an int.
 *
 * @param compareTo Object to compare this to
 * @param operator Any QueryCriteriaOperator
 * @return whether the objects meet the criteria of the operator
 * @throws InvalidDataTypeForOperator If the values cannot be compared
 */
@Throws(InvalidDataTypeForOperator::class)
@JvmOverloads
@Suppress("UNCHECKED_CAST")
fun Any?.compare(compareTo: Any?, operator: QueryCriteriaOperator = QueryCriteriaOperator.EQUAL): Boolean {

    var first: Any? = compareTo
    val second: Any? = this

    if (second != null && first != null && first::class != second::class
        && operator != QueryCriteriaOperator.IN // Expected as List when IN
        && operator != QueryCriteriaOperator.NOT_IN
    ) {
        when (second) {
            // Handle numeric types
            is Int -> first = (first as? Number)?.toInt()
            is Long -> first = (first as? Number)?.toLong()
            is Float -> first = (first as? Number)?.toFloat()
            is Double -> first = (first as? Number)?.toDouble()
            is Short -> first = (first as? Number)?.toShort()
            is Byte -> first = (first as? Number)?.toByte()

            // Handle string types
            is String -> first = first.toString()

            // Handle boolean types
            is Boolean -> first = (first as? Boolean)

            // Handle character types
            is Char -> first = (first as? Char)?.toString()?.singleOrNull()

            // Handle list types (for IN and NOT_IN operators)
            is List<*> -> first = (first as? List<Any?>)

            // Handle pairs for BETWEEN and NOT_BETWEEN
            is Pair<*, *> -> {}//first = first as? Pair<Any?, Any?>
            is BigInteger -> first = (first as? Number)?.toLong()?.let { BigInteger.valueOf(it) }
            is BigDecimal -> first = (first as? Number)?.toDouble()?.let { BigDecimal.valueOf(it) }

            // Handle comparable cases
            is Comparable<*> -> first = first as? Comparable<Any?>

            // Handle other cases or throw an error if the type is unsupported
            else -> throw InvalidDataTypeForOperator("Cannot cast to ${second::class}")
        }

    }

    try {
        return when (operator) {
            QueryCriteriaOperator.EQUAL -> first == second
            QueryCriteriaOperator.CONTAINS -> (first.toString()).contains(second.toString())
            QueryCriteriaOperator.CONTAINS_IGNORE_CASE -> (first.toString()).contains(second.toString(), true)
            QueryCriteriaOperator.GREATER_THAN -> {
                when {
                    first == null && second == null -> false
                    second == null -> true
                    first == null -> false
                    first is String && second is String -> first.compareTo(second, false) > 0
                    else -> (first as Comparable<Any?>) > (second as Comparable<Any?>)
                }
            }

            QueryCriteriaOperator.LESS_THAN -> {
                when {
                    first == null && second == null -> false
                    second == null -> false
                    first == null -> true
                    first is String && second is String -> first.compareTo(second, false) < 0
                    else -> (first as Comparable<Any?>) < (second as Comparable<Any?>)
                }
            }

            QueryCriteriaOperator.LESS_THAN_EQUAL -> {
                when {
                    first == null && second == null -> true
                    second == null -> false
                    first == null -> true
                    first is String && second is String -> first.compareTo(second, false) <= 0
                    else -> (first as Comparable<Any?>) <= (second as Comparable<Any?>)
                }
            }

            QueryCriteriaOperator.GREATER_THAN_EQUAL -> {
                when {
                    first == null && second == null -> true
                    second == null -> true
                    first == null -> false
                    first is String && second is String -> first.compareTo(second, false) >= 0
                    else -> (first as Comparable<Any?>) >= (second as Comparable<Any?>)
                }
            }

            QueryCriteriaOperator.BETWEEN -> {
                // support range pair on either side of the comparison
                val range = (first as? Pair<Any?, Any?>) ?: (second as? Pair<Any?, Any?>) ?: return false
                val value = if (first is Pair<*, *>) second else first
                return range.first.compare(value, QueryCriteriaOperator.GREATER_THAN_EQUAL) &&
                        range.second.compare(value, QueryCriteriaOperator.LESS_THAN_EQUAL)
            }

            QueryCriteriaOperator.NOT_BETWEEN -> {
                // support range pair on either side of the comparison
                val range = (first as? Pair<Any?, Any?>) ?: (second as? Pair<Any?, Any?>) ?: return false
                val value = if (first is Pair<*, *>) second else first
                return !(range.first.compare(value, QueryCriteriaOperator.GREATER_THAN_EQUAL) &&
                        range.second.compare(value, QueryCriteriaOperator.LESS_THAN_EQUAL))
            }

            QueryCriteriaOperator.NOT_EQUAL -> first != second
            QueryCriteriaOperator.NOT_STARTS_WITH -> !(first.toString()).startsWith(second.toString())
            QueryCriteriaOperator.NOT_NULL -> first != null
            QueryCriteriaOperator.IS_NULL -> first == null
            QueryCriteriaOperator.STARTS_WITH -> (first.toString()).startsWith(second.toString())
            QueryCriteriaOperator.NOT_CONTAINS -> !(first.toString()).contains(second.toString())
            QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE -> !(first.toString()).contains(second.toString(), true)
            QueryCriteriaOperator.LIKE -> (first.toString()).equals(second.toString(), true)
            QueryCriteriaOperator.NOT_LIKE -> !(first.toString()).equals(second.toString(), true)
            QueryCriteriaOperator.MATCHES -> (first.toString()).matches(Regex(second.toString()))
            QueryCriteriaOperator.NOT_MATCHES -> !(first.toString()).matches(Regex(second.toString()))
            QueryCriteriaOperator.IN -> {
                val list = second as List<Any>
                return list.find { first.compare(it, QueryCriteriaOperator.EQUAL) } != null
            }

            QueryCriteriaOperator.NOT_IN -> {
                val list = second as List<Any>
                return list.find { first.compare(it, QueryCriteriaOperator.EQUAL) } == null
            }
        }
    } catch (e: Exception) {
        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR)
    }
}
