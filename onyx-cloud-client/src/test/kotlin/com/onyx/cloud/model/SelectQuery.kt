package com.onyx.cloud.model

import com.onyx.cloud.impl.QueryCondition
import com.onyx.cloud.api.Sort


/**
 * Wire format for select queries sent to the server.
 */
data class SelectQuery(
    val type: String = "SelectQuery",
    val fields: List<String>? = null,
    val conditions: QueryCondition? = null,
    val sort: List<Sort>? = null,
    val limit: Int? = null,
    val distinct: Boolean? = null,
    val groupBy: List<String>? = null,
    val partition: String? = null,
    val resolvers: List<String>? = null
)
