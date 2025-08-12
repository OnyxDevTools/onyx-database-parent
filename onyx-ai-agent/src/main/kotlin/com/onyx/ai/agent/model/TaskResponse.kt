package com.onyx.ai.agent.model

import kotlinx.serialization.Serializable

/** Whole response object coming from Ollama */
@Serializable
data class TaskResponse(val tasks: List<Task>)
