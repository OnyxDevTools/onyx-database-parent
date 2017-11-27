package com.onyx.persistence.query

/**
 * Type of attribute to compare while filtering record
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * @see com.onyx.persistence.query.QueryCriteria
 */
enum class QueryCriteriaType {
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
    ENUM,
    LIST_FLOAT,
    LIST_CHARACTER,
    LIST_BYTE,
    LIST_SHORT,
    LIST_ENTITY,
    LIST_STRING,
    LIST_LONG,
    LIST_INTEGER,
    LIST_DOUBLE,
    LIST_DATE
}
