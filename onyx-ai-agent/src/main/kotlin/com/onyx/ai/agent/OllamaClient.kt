package com.onyx.ai.agent

import com.onyx.ai.agent.model.TaskResponse
import com.onyx.ai.agent.model.Task
import com.onyx.ai.agent.model.Action
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class OllamaClient(
    /** Model name – change as you wish (e.g. "codellama:13b", "gpt-oss:20b", "gpt-4o") */
    internal val model: String = "gpt-oss:20b",

    /** Base URL of the *actual* Ollama server (or an OpenAI-compatible proxy). */
    internal val baseUrl: String = "https://ollama.com",

    /** Secret bearer token – required by Ollama as of v0.1.27 and by every proxy that expects a key */
    private val apiKey: String
) {

    /* -------------------------------------------------------------
       Ktor HTTP client – JSON aware, coroutine friendly
       ------------------------------------------------------------- */
    internal val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            // Increase read timeout to 15 minutes (900,000 ms)
            requestTimeoutMillis = 900_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 900_000
        }
    }

    /** -------------------------------------------------------------
    System prompt – whole-file I/O only + drop-in replacement guidance
    ------------------------------------------------------------- */
    private val systemPrompt = """
        You are a powerful AI coding assistant that can perform comprehensive software development tasks.

        You have access to these tools for interacting with the project (STRICT RULES):
        - run_command: Execute shell commands to gather information, build projects, run tests, etc.
        - read_file: Read an existing file **in its entirety**. Arguments: { path } ONLY. Never request line ranges.
        - write_file: Write/replace a file **in its entirety** (create if it does not exist). Arguments: { path, content } ONLY.
          Never pass start_line, end_line, patch, or diff. Always provide the full final file contents.
        - delete_file: Remove files when needed.
        - complete: Signal that the task has been completed successfully. If files needed changes, they have already been written,
          the project compiles, and unit tests have run successfully.

        DROP-IN REPLACEMENT EXPECTATION:
        - When changing a file, produce a complete, ready-to-compile replacement of that file via write_file.
        - Preserve package declarations, imports, public APIs, and behavior unless explicitly instructed otherwise.

        CRITICAL COMPLETION RULES:
        1. When tests pass, builds succeed, and you have met all criteria for the request, USE the 'complete' function immediately.
        2. The 'complete' function is MANDATORY when work is done.

        Always think step by step and use information-gathering commands before making changes.
        But once successful, STOP and use 'complete'.

        You must respond with a tool_calls array containing the tool calls you wish to make.
        Each tool call must contain a 'function' object with the function name and arguments.
        The arguments must be a JSON object. If that is empty, the user will just ask you for the next command.
        If you intend to complete you must pass the complete function in the tool_calls array.

        If it does not compile, you must fix the compile errors. If it does not pass unit tests, you must fix the unit tests.
    """.trimIndent()

    /** -------------------------------------------------------------
    Tool definitions for function calling (whole-file only)
    ------------------------------------------------------------- */
    private val tools = buildJsonArray {
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "run_command")
                put("description", "Execute shell commands to gather information, build projects, run tests, install dependencies, etc.")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("instruction", buildJsonObject {
                            put("type", "string")
                            put("description", "The shell command to execute")
                        })
                    })
                    put("required", buildJsonArray { add("instruction") })
                    put("additionalProperties", JsonPrimitive(false))
                })
            })
        })
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "read_file")
                put("description", "Read an existing file in its entirety. No partial ranges are supported.")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute or project-relative file path to read")
                        })
                    })
                    put("required", buildJsonArray { add("path") })
                    put("additionalProperties", JsonPrimitive(false))
                })
            })
        })
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "write_file")
                put("description", "Write/replace a file in its entirety (create if it does not exist). Provide the full final contents.")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The absolute or project-relative file path to write")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "The complete file contents to write")
                        })
                    })
                    put("required", buildJsonArray { add("path"); add("content") })
                    put("additionalProperties", JsonPrimitive(false))
                })
            })
        })
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "delete_file")
                put("description", "Remove files when needed")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The file path to delete")
                        })
                    })
                    put("required", buildJsonArray { add("path") })
                    put("additionalProperties", JsonPrimitive(false))
                })
            })
        })
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "complete")
                put("description", "Signal that the task has been completed successfully - use this when you have accomplished the user's request")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "A brief summary describing what was accomplished")
                        })
                    })
                    // no required fields; allow empty {}
                    put("additionalProperties", JsonPrimitive(false))
                })
            })
        })
    }

    /** -------------------------------------------------------------
    Helper – adds Authorization: Bearer <apiKey>
    ------------------------------------------------------------- */
    private fun HttpRequestBuilder.bearerHeader() {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
    }

    /** -------------------------------------------------------------
    Public API
    ------------------------------------------------------------- */
    suspend fun askForTasks(userPrompt: String): TaskResponse = withContext(Dispatchers.IO) {
        askForTasksInternal(userPrompt)
    }

    /** Overloaded version that accepts chat history */
    suspend fun askForTasks(messages: List<ChatHistory.Msg>): TaskResponse = withContext(Dispatchers.IO) {
        askForTasksInternal("", messages)
    }

    /** -------------------------------------------------------------
    Core flow
    ------------------------------------------------------------- */
    private suspend fun askForTasksInternal(
        userPrompt: String,
        previousMessages: List<ChatHistory.Msg>? = null
    ): TaskResponse {
        // 1) First call: provide the functions as tools
        val messages = buildJsonArray {
            // System message
            add(buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            })

            // Prior conversation (excluding system to avoid duplication)
            previousMessages?.forEach { msg ->
                if (msg.role != "system") {
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                        msg.thinking?.let { put("thinking", it) }
                    })
                }
            }

            // First turn user prompt
            if (previousMessages == null && userPrompt.isNotEmpty()) {
                add(buildJsonObject { put("role", "user"); put("content", userPrompt) })
            }
        }

        val firstBody = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", messages)
            put("tools", tools)
        }

        val firstResp = http.post("$baseUrl/api/chat") {
            bearerHeader()
            contentType(ContentType.Application.Json)
            setBody(firstBody)
        }.bodyAsText()

        val root = Json.parseToJsonElement(firstResp).jsonObject
        val message = root["message"]?.jsonObject ?: error("No message in response")
        val toolCalls = message["tool_calls"]?.jsonArray
        val content = message["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val thinking = message["thinking"]?.jsonPrimitive?.contentOrNull ?: ""

        if (toolCalls.isNullOrEmpty()) {
            return TaskResponse(listOf(Task(action = Action.NONE)), content = content, thinking = thinking)
        }

        // 2) Translate tool calls into tasks
        val tasks = mutableListOf<Task>()

        for (call in toolCalls) {
            val callObj = call.jsonObject
            val function = callObj["function"]?.jsonObject ?: continue
            val name = function["name"]?.jsonPrimitive?.content ?: continue
            val argsEl = function["arguments"]

            // Parse arguments - handle either JsonObject or stringified JSON
            val argsObj = when {
                argsEl is JsonObject -> argsEl
                argsEl is JsonPrimitive && argsEl.isString -> {
                    try { Json.parseToJsonElement(argsEl.content).jsonObject } catch (_: Exception) { buildJsonObject { } }
                }
                else -> buildJsonObject { }
            }

            when (name) {
                "run_command" -> {
                    val instruction = argsObj["instruction"]?.jsonPrimitive?.content ?: ""
                    tasks.add(Task(action = Action.RUN_COMMAND, instruction = instruction))
                }
                "read_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    // Whole-file read only; no ranges
                    tasks.add(Task(action = Action.READ_FILE, path = path, line_start = null, line_end = null))
                }
                "write_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    val newContent = argsObj["content"]?.jsonPrimitive?.content ?: ""
                    // Map whole-file write to existing EDIT_FILE action for compatibility
                    tasks.add(Task(action = Action.EDIT_FILE, path = path, content = newContent, line_start = null, line_end = null))
                }
                "delete_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    tasks.add(Task(action = Action.DELETE_FILE, path = path))
                }
                "complete" -> {
                    val summary = argsObj["content"]?.jsonPrimitive?.content ?: "Task completed successfully"
                    tasks.add(Task(action = Action.COMPLETE, content = summary))
                }
                else -> {
                    // Ignore unknown tools
                }
            }
        }

        if (tasks.isNotEmpty()) {
            return TaskResponse(tasks, content = content, thinking = thinking)
        }

        // 3) If no tasks created, echo tool calls and let the model continue (rare)
        val followupMessages = buildJsonArray {
            messages.forEach { add(it) }
            add(buildJsonObject {
                put("role", "assistant")
                put("content", "")
                put("tool_calls", toolCalls)
            })
        }

        val finalBody = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", followupMessages)
            put("tools", tools)
        }

        val finalResp = http.post("$baseUrl/api/chat") {
            bearerHeader()
            contentType(ContentType.Application.Json)
            setBody(finalBody)
        }.bodyAsText()

        val finalRoot = Json.parseToJsonElement(finalResp).jsonObject
        val finalMessage = finalRoot["message"]?.jsonObject ?: throw IllegalStateException("No message in follow-up response")
        val finalContent = finalMessage["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val finalThinking = finalMessage["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
        val finalToolCalls = finalMessage["tool_calls"]?.jsonArray

        if (finalToolCalls != null && finalToolCalls.isNotEmpty()) {
            val finalTasks = mutableListOf<Task>()
            for (tc in finalToolCalls) {
                val f = tc.jsonObject["function"]?.jsonObject ?: continue
                val fname = f["name"]?.jsonPrimitive?.content ?: continue
                val fargsEl = f["arguments"]
                val fargs = when {
                    fargsEl is JsonObject -> fargsEl
                    fargsEl is JsonPrimitive && fargsEl.isString -> {
                        try { Json.parseToJsonElement(fargsEl.content).jsonObject } catch (_: Exception) { buildJsonObject { } }
                    }
                    else -> buildJsonObject { }
                }

                when (fname) {
                    "run_command" -> {
                        val instr = fargs["instruction"]?.jsonPrimitive?.content ?: ""
                        finalTasks.add(Task(action = Action.RUN_COMMAND, instruction = instr))
                    }
                    "read_file" -> {
                        val p = fargs["path"]?.jsonPrimitive?.content ?: ""
                        finalTasks.add(Task(action = Action.READ_FILE, path = p, line_start = null, line_end = null))
                    }
                    "write_file" -> {
                        val p = fargs["path"]?.jsonPrimitive?.content ?: ""
                        val c = fargs["content"]?.jsonPrimitive?.content ?: ""
                        finalTasks.add(Task(action = Action.EDIT_FILE, path = p, content = c, line_start = null, line_end = null))
                    }
                    "delete_file" -> {
                        val p = fargs["path"]?.jsonPrimitive?.content ?: ""
                        finalTasks.add(Task(action = Action.DELETE_FILE, path = p))
                    }
                    "complete" -> {
                        val c = fargs["content"]?.jsonPrimitive?.content ?: "Task completed successfully"
                        finalTasks.add(Task(action = Action.COMPLETE, content = c))
                    }
                }
            }
            return TaskResponse(finalTasks, content = finalContent, thinking = finalThinking)
        }

        return TaskResponse(listOf(Task(action = Action.NONE)), content = finalContent, thinking = finalThinking)
    }
}
