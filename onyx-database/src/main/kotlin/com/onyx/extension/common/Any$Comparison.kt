package com.onyx.extension.common

import com.onyx.exception.InvalidDataTypeForOperator
import com.onyx.persistence.query.QueryCriteriaOperator

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
} catch (invalidDataTypeForOperator: InvalidDataTypeForOperator) {
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

    var first:Any? = compareTo
    val second:Any? = this

    if(second != null && first != null && first.javaClass !== second.javaClass
            && operator != QueryCriteriaOperator.IN // Expected as List when IN
            && operator != QueryCriteriaOperator.NOT_IN) {
        first = first.castTo(second.javaClass)
    }

    try {
        return when (operator) {
            QueryCriteriaOperator.EQUAL -> first == second
            QueryCriteriaOperator.CONTAINS -> (first.toString()).contains(second.toString())
            QueryCriteriaOperator.GREATER_THAN -> {
                when {
                    first == null && second == null -> false
                    second == null -> true
                    first == null -> false
                    first is String && second is String -> first.compareTo(second, true) > 0
                    else -> (first as Comparable<Any?>) > (second as Comparable<Any?>)
                }
            }
            QueryCriteriaOperator.LESS_THAN -> {
                when {
                    first == null && second == null -> false
                    second == null -> false
                    first == null -> true
                    first is String && second is String -> first.compareTo(second, true) < 0
                    else -> (first as Comparable<Any?>) < (second as Comparable<Any?>)
                }
            }
            QueryCriteriaOperator.LESS_THAN_EQUAL -> {
                when {
                    first == null && second == null -> true
                    second == null -> false
                    first == null -> true
                    first is String && second is String -> first.compareTo(second, true) <= 0
                    else -> (first as Comparable<Any?>) <= (second as Comparable<Any?>)
                }
            }
            QueryCriteriaOperator.GREATER_THAN_EQUAL -> {
                when {
                    first == null && second == null -> true
                    second == null -> true
                    first == null -> false
                    first is String && second is String -> first.compareTo(second, true) >= 0
                    else -> (first as Comparable<Any?>) >= (second as Comparable<Any?>)
                }
            }
            QueryCriteriaOperator.NOT_EQUAL -> first != second
            QueryCriteriaOperator.NOT_STARTS_WITH -> !(first.toString()).startsWith(second.toString())
            QueryCriteriaOperator.NOT_NULL -> first != null
            QueryCriteriaOperator.IS_NULL -> first == null
            QueryCriteriaOperator.STARTS_WITH -> (first.toString()).startsWith(second.toString())
            QueryCriteriaOperator.NOT_CONTAINS -> !(first.toString()).contains(second.toString())
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
    } catch (e:Exception) {
        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR)
    }
}