package com.onyx.persistence.query;

/**
 * Type of attribute to compare while filtering record
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * @see com.onyx.persistence.query.QueryCriteria
 *
 */
public enum QueryCriteriaType
{

    STRING,
    LONG,
    DOUBLE,
    DATE,
    INTEGER,
    BOOLEAN,
    LIST_STRING,
    LIST_LONG,
    LIST_INTEGER,
    LIST_DOUBLE,
    LIST_DATE;

    /**
     * Constructor
     */
    QueryCriteriaType()
    {

    }
}
