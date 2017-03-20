package com.onyx.persistence.query;

/**
 * Set of defined Query Operators.  Used when specifying a Query and QueryCriteria.
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 *
 * @see com.onyx.persistence.query.QueryCriteria
 *
 */
public enum QueryCriteriaOperator
{

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
     * Constructor
     */
    @SuppressWarnings("unused")
    QueryCriteriaOperator()
    {

    }
}
