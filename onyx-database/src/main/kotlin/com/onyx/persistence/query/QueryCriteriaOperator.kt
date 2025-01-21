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
    CONTAINS_IGNORE_CASE,
    NOT_CONTAINS_IGNORE_CASE,
    NOT_CONTAINS,
    LIKE,
    NOT_LIKE,
    MATCHES,
    NOT_MATCHES,
    LESS_THAN,
    GREATER_THAN,
    BETWEEN,
    NOT_BETWEEN,
    LESS_THAN_EQUAL,
    GREATER_THAN_EQUAL,
    IN,
    NOT_IN;

    /**
     * Indicates if the operator supports indexing capabilities
     * @return If this operator supports index scanning
     */
    val isIndexed: Boolean
        get() = this === EQUAL
                || this === IN
                || this === GREATER_THAN
                || this === GREATER_THAN_EQUAL
                || this === LESS_THAN
                || this === LESS_THAN_EQUAL
                || this === BETWEEN

    /**
     * Get the inverse in order to support the .not() feature within query criteria
     *
     * @since 2.0.0
     */
    val inverse: QueryCriteriaOperator
        get() = when(this) {
            EQUAL -> NOT_EQUAL
            NOT_EQUAL -> EQUAL
            NOT_STARTS_WITH -> STARTS_WITH
            NOT_NULL -> IS_NULL
            IS_NULL -> NOT_NULL
            STARTS_WITH -> NOT_STARTS_WITH
            CONTAINS -> NOT_CONTAINS
            CONTAINS_IGNORE_CASE -> NOT_CONTAINS_IGNORE_CASE
            NOT_CONTAINS_IGNORE_CASE -> CONTAINS_IGNORE_CASE
            NOT_CONTAINS -> CONTAINS
            LIKE -> NOT_LIKE
            NOT_LIKE -> LIKE
            MATCHES -> NOT_MATCHES
            NOT_MATCHES -> MATCHES
            LESS_THAN -> GREATER_THAN_EQUAL
            GREATER_THAN -> LESS_THAN_EQUAL
            LESS_THAN_EQUAL -> GREATER_THAN
            GREATER_THAN_EQUAL -> LESS_THAN
            IN -> NOT_IN
            NOT_IN -> IN
            BETWEEN -> NOT_BETWEEN
            NOT_BETWEEN -> BETWEEN
        }
}
