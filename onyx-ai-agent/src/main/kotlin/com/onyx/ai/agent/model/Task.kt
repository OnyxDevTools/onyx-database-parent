package com.onyx.ai.agent.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val action: Action,
    val path: String? = null,
    val content: String? = null,
    val instruction: String? = null,
    val line_start: Int? = null,
    val line_end: Int? = null
)
