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

    /** Base URL of the *actual* Ollama server (or an OpenAI‑compatible proxy). */
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
            requestTimeoutMillis = 900_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 900_000
        }
    }

    /** -------------------------------------------------------------
    System prompt that explains the available tools and when to use them
    ------------------------------------------------------------- */
    private val systemPrompt = """
        You are a powerful AI coding assistant that can perform comprehensive software development tasks.

        You have access to these tools for interacting with the project:
        - run_command: Execute shell commands to gather information, build projects, run tests, etc.
        - read_file: Read and examine existing files to understand code structure
        - create_file: Create new files with any content
        - edit_file: Modify existing files completely
        - delete_file: Remove files when needed
        - complete: Signal that the task has been completed successfully.  If files needed to be changed they have been already done and it compiles and unit tests have run successfully.

        CRITICAL COMPLETION RULES:
        1. When tests pass and the builds succeed and you have met all the criteria for the original request, USE THE 'complete' FUNCTION IMMEDIATELY
        2. The 'complete' function is MANDATORY when work is done - not optional
        
        Always think step by step and use information-gathering commands before making changes.
        But once successful, STOP and use 'complete' function.
        
        You must respond with a tool_calls array containing the tool calls you wish to make.
        Each tool call must contain a 'function' object with the name of the function to call and the arguments.
        The arguments must be a JSON object.  If that is empty, the user will just ask you for the next command.  
        If you intend to complete you must pass the complete function in the tool_calls array.
        
        If it does not compile, you must fix the compile errors.  If it does not pass unit tests, you must fix the unit tests.
    """.trimIndent()

    /** -------------------------------------------------------------
    Tool definitions for function calling
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
                })
            })
        })
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "read_file")
                put("description", "Read and examine existing files to understand code structure")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The file path to read")
                        })
                        put("line_start", buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional starting line number (1-based) to read from")
                        })
                        put("line_end", buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional ending line number (1-based) to read until")
                        })
                    })
                    put("required", buildJsonArray { add("path") })
                })
            })
        })
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "create_file")
                put("description", "Create new files with any content")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The file path to create")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "The content to write to the file")
                        })
                        put("line_start", buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional starting line number (1-based) to write from")
                        })
                        put("line_end", buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional ending line number (1-based) to write until")
                        })
                    })
                    put("required", buildJsonArray { add("path"); add("content") })
                })
            })
        })
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "edit_file")
                put("description", "Modify existing files completely (full file replacement)")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "The file path to edit")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "The new content for the file")
                        })
                        put("line_start", buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional starting line number (1-based) to replace from.  If you provide a partial edit, this is required otherwise it will corrupt the file.  Be mindful of the file position you read from.")
                        })
                        put("line_end", buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional ending line number (1-based) to replace until.  .  If you provide a partial edit, this is required otherwise it will corrupt the file. Be mindful of the file position you read from.")
                        })
                    })
                    put("required", buildJsonArray { add("path"); add("content") })
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
                            put("description", "A summary message describing what was accomplished")
                        })
                    })
                    put("required", buildJsonArray { })
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
    Ask the model for a structured list of tasks using function calling
    ------------------------------------------------------------- */
    suspend fun askForTasks(userPrompt: String): TaskResponse = withContext(Dispatchers.IO) {
        askForTasksInternal(userPrompt)
    }

    /** Overloaded version that accepts chat history */
    suspend fun askForTasks(messages: List<ChatHistory.Msg>): TaskResponse = withContext(Dispatchers.IO) {
        askForTasksInternal("", messages)
    }

    private suspend fun askForTasksInternal(userPrompt: String, previousMessages: List<ChatHistory.Msg>? = null): TaskResponse {
        // 1) First call: provide the functions as tools
        val messages = buildJsonArray {
            // System message
            add(buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            })

            // Add previous messages if provided - these already contain the full conversation context
            previousMessages?.forEach { msg ->
                if (msg.role != "system") { // Don't duplicate system messages
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                        msg.thinking?.let { put("thinking", it) }
                    })
                }
            }

            // Only add userPrompt if we don't have previous messages (first iteration)
            if (previousMessages == null && userPrompt.isNotEmpty()) {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
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

        // Removed tracing of the json response
        val root = Json.parseToJsonElement(firstResp).jsonObject
        val message = root["message"]?.jsonObject ?: error("No message in response")
        val toolCalls = message["tool_calls"]?.jsonArray
        val content = message["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val thinking = message["thinking"]?.jsonPrimitive?.contentOrNull ?: ""

        // If no tool calls, try to parse content as direct JSON response
        if (toolCalls.isNullOrEmpty()) {
            // No tool calls and no JSON tasks, create a simple response
            return TaskResponse(listOf(
                Task(action = Action.NONE)
            ), content = content, thinking = thinking)
        }

        // 2) Execute the requested tool calls
        val toolResults = mutableListOf<JsonObject>()
        val tasks = mutableListOf<Task>()

        for (call in toolCalls) {
            val callObj = call.jsonObject
            val function = callObj["function"]?.jsonObject ?: continue
            val name = function["name"]?.jsonPrimitive?.content ?: continue
            val argsEl = function["arguments"]

            // Parse arguments - handle both JsonObject and string formats
            val argsObj = when {
                argsEl is JsonObject -> argsEl
                argsEl is JsonPrimitive && argsEl.isString -> {
                    try {
                        Json.parseToJsonElement(argsEl.content).jsonObject
                    } catch (e: Exception) {
                        buildJsonObject { }
                    }
                }
                else -> buildJsonObject { }
            }

            // Convert tool call to task and prepare result
            when (name) {
                "run_command" -> {
                    val instruction = argsObj["instruction"]?.jsonPrimitive?.content ?: ""
                    tasks.add(Task(action = Action.RUN_COMMAND, instruction = instruction))

                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "Command queued: $instruction")
                        put("status", "success")
                    })
                }
                "read_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    val lineStart = argsObj["line_start"]?.jsonPrimitive?.intOrNull
                    val lineEnd = argsObj["line_end"]?.jsonPrimitive?.intOrNull
                    tasks.add(Task(action = Action.READ_FILE, path = path, line_start = lineStart, line_end = lineEnd))

                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "File read queued: $path")
                        put("status", "success")
                    })
                }
                "create_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    val content = argsObj["content"]?.jsonPrimitive?.content ?: ""
                    val lineStart = argsObj["line_start"]?.jsonPrimitive?.intOrNull
                    val lineEnd = argsObj["line_end"]?.jsonPrimitive?.intOrNull
                    tasks.add(Task(action = Action.CREATE_FILE, path = path, content = content, line_start = lineStart, line_end = lineEnd))

                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "File creation queued: $path")
                        put("status", "success")
                    })
                }
                "edit_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    val content = argsObj["content"]?.jsonPrimitive?.content ?: ""
                    val lineStart = argsObj["line_start"]?.jsonPrimitive?.intOrNull
                    val lineEnd = argsObj["line_end"]?.jsonPrimitive?.intOrNull
                    tasks.add(Task(action = Action.EDIT_FILE, path = path, content = content, line_start = lineStart, line_end = lineEnd))

                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "File edit queued: $path")
                        put("status", "success")
                    })
                }
                "delete_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    tasks.add(Task(action = Action.DELETE_FILE, path = path))

                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "File deletion queued: $path")
                        put("status", "success")
                    })
                }
                "complete" -> {
                    val content = argsObj["content"]?.jsonPrimitive?.content ?: "Task completed successfully"
                    tasks.add(Task(action = Action.COMPLETE, content = content))

                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "Task completion signaled: $content")
                        put("status", "success")
                    })
                }
                else -> {
                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "Unknown tool: $name")
                        put("status", "error")
                    })
                }
            }
        }

        // If we have tasks from tool calls, return them
        if (tasks.isNotEmpty()) {
            return TaskResponse(tasks, content = content, thinking = thinking)
        }

        // 3) If no tasks were generated, send tool results back for final completion
        val followupMessages = buildJsonArray {
            // Include all previous messages
            messages.forEach { add(it) }

            // Add the assistant's tool call message
            add(buildJsonObject {
                put("role", "assistant")
                put("content", "")
                put("tool_calls", toolCalls)
            })
        }

        // Re-send with tool call results and await final tool output
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
        val finalMessage = finalRoot["message"]?.jsonObject ?: throw IllegalStateException("No message in follow‑up response")
        val finalContent = finalMessage["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val finalThinking = finalMessage["thinking"]?.jsonPrimitive?.contentOrNull ?: ""

        val finalToolCalls = finalMessage["tool_calls"]?.jsonArray

        // If tool calls present, execute them and return tasks
        if (finalToolCalls != null && finalToolCalls.isNotEmpty()) {
            val finalTaskResults = mutableListOf<Task>()
            for (tc in finalToolCalls) {
                val f = tc.jsonObject["function"]?.jsonObject ?: continue
                val fname = f["name"]?.jsonPrimitive?.content ?: continue
                val fargs = f["arguments"]?.jsonObject ?: buildJsonObject {}
                when (fname) {
                    "run_command" -> {
                        val instr = fargs["instruction"]?.jsonPrimitive?.content ?: ""
                        finalTaskResults.add(Task(action = Action.RUN_COMMAND, instruction = instr))
                    }
                    "read_file" -> {
                        val p = fargs["path"]?.jsonPrimitive?.content ?: ""
                        val ls = fargs["line_start"]?.jsonPrimitive?.intOrNull
                        val le = fargs["line_end"]?.jsonPrimitive?.intOrNull
                        finalTaskResults.add(Task(action = Action.READ_FILE, path = p, line_start = ls, line_end = le))
                    }
                    "create_file" -> {
                        val p = fargs["path"]?.jsonPrimitive?.content ?: ""
                        val c = fargs["content"]?.jsonPrimitive?.content ?: ""
                        val ls = fargs["line_start"]?.jsonPrimitive?.intOrNull
                        val le = fargs["line_end"]?.jsonPrimitive?.intOrNull
                        finalTaskResults.add(Task(action = Action.CREATE_FILE, path = p, content = c, line_start = ls, line_end = le))
                    }
                    "edit_file" -> {
                        val p = fargs["path"]?.jsonPrimitive?.content ?: ""
                        val c = fargs["content"]?.jsonPrimitive?.content ?: ""
                        val ls = fargs["line_start"]?.jsonPrimitive?.intOrNull
                        val le = fargs["line_end"]?.jsonPrimitive?.intOrNull
                        finalTaskResults.add(Task(action = Action.EDIT_FILE, path = p, content = c, line_start = ls, line_end = le))
                    }
                    "delete_file" -> {
                        val p = fargs["path"]?.jsonPrimitive?.content ?: ""
                        finalTaskResults.add(Task(action = Action.DELETE_FILE, path = p))
                    }
                    "complete" -> {
                        val c = fargs["content"]?.jsonPrimitive?.content ?: "Task completed successfully"
                        finalTaskResults.add(Task(action = Action.COMPLETE, content = c))
                    }
                }
            }
            return TaskResponse(finalTaskResults, content = finalContent, thinking = finalThinking)
        }

        // Fallback: return content as a final task if no tool calls
        return TaskResponse(listOf(Task(action = Action.NONE)), content = finalContent, thinking = finalThinking)
    }
}
