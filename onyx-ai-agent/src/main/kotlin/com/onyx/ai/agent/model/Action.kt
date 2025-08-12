package com.onyx.ai.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Action {
    @SerialName("create_file") CREATE_FILE,
    @SerialName("edit_file")   EDIT_FILE,
    @SerialName("delete_file") DELETE_FILE,
    @SerialName("read_file")   READ_FILE,
    @SerialName("run_command") RUN_COMMAND
}
