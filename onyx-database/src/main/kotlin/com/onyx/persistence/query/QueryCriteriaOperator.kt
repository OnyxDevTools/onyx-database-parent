package com.onyx.persistence.query

/**
 * Set of defined Query Operators.  Used when specifying a Query and QueryCriteria.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * @see com.onyx.persistence.query.QueryCriteria
 */
enum class QueryCriteriaOperator {
    EQUAL,
    NOT_EQUAL,
    NOT_STARTS_WITH,
    NOT_NULL,
    IS_NULL,
    STARTS_WITH,
    CONTAINS,
    NOT_CONTAINS,
    LIKE,
    NOT_LIKE,
    MATCHES,
    NOT_MATCHES,
    LESS_THAN,
    GREATER_THAN,
    LESS_THAN_EQUAL,
    GREATER_THAN_EQUAL,
    IN,
    NOT_IN;

    /**
     * Indicates if the operator supports indexing capabilities
     * @return If this operator supports index scanning
     */
    val isIndexed: Boolean
        get() = this == QueryCriteriaOperator.EQUAL
                || this == QueryCriteriaOperator.IN
                || this == QueryCriteriaOperator.GREATER_THAN
                || this == QueryCriteriaOperator.GREATER_THAN_EQUAL
                || this == QueryCriteriaOperator.LESS_THAN
                || this == QueryCriteriaOperator.LESS_THAN_EQUAL
}
