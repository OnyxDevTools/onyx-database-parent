package com.onyx.persistence.query;

import com.onyx.persistence.ManagedEntity;

import java.util.List;

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
    FLOAT,
    CHARACTER,
    BYTE,
    SHORT,
    ENTITY,
    LIST_FLOAT,
    LIST_CHARACTER,
    LIST_BYTE,
    LIST_SHORT,
    LIST_ENTITY,
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
