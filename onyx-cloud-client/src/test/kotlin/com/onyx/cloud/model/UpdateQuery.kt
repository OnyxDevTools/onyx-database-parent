package com.onyx.cloud.model

import com.onyx.cloud.impl.QueryCondition
import com.onyx.cloud.api.Sort

/**
 * Wire format for update queries sent to the server.
 */
data class UpdateQuery(
    val type: String = "UpdateQuery",
    val conditions: QueryCondition? = null,
    val updates: Map<String, Any?>,
    val sort: List<Sort>? = null,
    val limit: Int? = null,
    val partition: String? = null
)
