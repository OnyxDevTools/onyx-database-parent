package com.onyx.cloud.model

import com.onyx.cloud.api.QueryCriteriaOperator

/**
 * Represents a single field comparison in a query.
 */
data class QueryCriteria(
    val field: String,
    val operator: QueryCriteriaOperator,
    val value: Any? = null
)
