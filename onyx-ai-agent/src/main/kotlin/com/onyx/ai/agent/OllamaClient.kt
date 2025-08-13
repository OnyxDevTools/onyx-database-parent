package com.onyx.ai.agent

import com.onyx.ai.agent.model.TaskResponse
import com.onyx.ai.agent.model.Task
import com.onyx.ai.agent.model.Action
import io.ktor.client.*
import io.ktor.client.engine.cio.*
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
        - edit_file: Modify existing files completely (full file replacement)
        - delete_file: Remove files when needed
        - complete: Signal that the task has been completed successfully
        
        When you need to perform any file operations or execute commands, use the appropriate tool.
        You can use multiple tools in sequence to solve complex problems iteratively.
        
        IMPORTANT: When you have successfully completed the user's request, use the 'complete' tool
        to signal task completion with a summary of what was accomplished.
        
        Always think step by step and use information-gathering commands before making changes.
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
        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content 
            ?: "Continue with the previous task"
        askForTasksInternal(lastUserMessage, messages)
    }
    
    private suspend fun askForTasksInternal(userPrompt: String, previousMessages: List<ChatHistory.Msg>? = null): TaskResponse {
        // 1) First call: provide the functions as tools
        val messages = buildJsonArray {
            // System message
            add(buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            })
            
            // Add previous messages if provided
            previousMessages?.forEach { msg ->
                if (msg.role != "system") { // Don't duplicate system messages
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }
            
            // Current user message
            if (userPrompt.isNotEmpty()) {
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

        println("🟢 First response: $firstResp")

        val root = Json.parseToJsonElement(firstResp).jsonObject
        val message = root["message"]?.jsonObject ?: error("No message in response")
        val toolCalls = message["tool_calls"]?.jsonArray

        // If no tool calls, try to parse content as direct JSON response
        if (toolCalls == null || toolCalls.isEmpty()) {
            val content = message["content"]?.jsonPrimitive?.contentOrNull ?: ""
            if (content.contains("tasks")) {
                try {
                    val jsonOnly = extractJsonObject(content)
                    return Json.decodeFromString<TaskResponse>(jsonOnly)
                } catch (e: Exception) {
                    // If parsing fails, create a simple task from the content
                    return TaskResponse(listOf(
                        Task(action = Action.RUN_COMMAND, instruction = "echo 'No specific tasks identified'")
                    ))
                }
            } else {
                // No tool calls and no JSON tasks, create a simple response
                return TaskResponse(listOf(
                    Task(action = Action.RUN_COMMAND, instruction = "echo 'Processing request: $userPrompt'")
                ))
            }
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
                    tasks.add(Task(action = Action.READ_FILE, path = path))
                    
                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "File read queued: $path")
                        put("status", "success")
                    })
                }
                "create_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    val content = argsObj["content"]?.jsonPrimitive?.content ?: ""
                    tasks.add(Task(action = Action.CREATE_FILE, path = path, content = content))
                    
                    toolResults.add(buildJsonObject {
                        put("tool_call_result", "File creation queued: $path")
                        put("status", "success")
                    })
                }
                "edit_file" -> {
                    val path = argsObj["path"]?.jsonPrimitive?.content ?: ""
                    val content = argsObj["content"]?.jsonPrimitive?.content ?: ""
                    tasks.add(Task(action = Action.EDIT_FILE, path = path, content = content))
                    
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
            return TaskResponse(tasks)
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
            
            // Add tool results
            toolResults.forEach { result ->
                add(buildJsonObject {
                    put("role", "tool")
                    put("content", result.toString())
                })
            }
        }

        val secondBody = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", followupMessages)
        }

        val secondResp = http.post("$baseUrl/api/chat") {
            bearerHeader()
            contentType(ContentType.Application.Json)
            setBody(secondBody)
        }.bodyAsText()

        println("🟢 Second response: $secondResp")

        val finalRoot = Json.parseToJsonElement(secondResp).jsonObject
        val finalMessage = finalRoot["message"]?.jsonObject
        val finalContent = finalMessage?.get("content")?.jsonPrimitive?.contentOrNull ?: ""

        // Try to parse final response as TaskResponse
        return try {
            val jsonOnly = extractJsonObject(finalContent)
            Json.decodeFromString<TaskResponse>(jsonOnly)
        } catch (e: Exception) {
            // If parsing fails, create a simple response
            TaskResponse(listOf(
                Task(action = Action.RUN_COMMAND, instruction = "echo 'Task completed'")
            ))
        }
    }

    /** -------------------------------------------------------------
    Plain‑text chat – used for summarising, follow‑up questions, etc.
    ------------------------------------------------------------- */
    suspend fun chatPlain(messages: List<JsonObject>): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages))
            put("stream", false)
        }

        val raw = http.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            bearerHeader()
            setBody(body)
        }.bodyAsText()

        val outer = Json.parseToJsonElement(raw).jsonObject
        outer["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }

    /** -------------------------------------------------------------
    Helper – extracts the first JSON object from a possibly noisy string.
    Throws IllegalArgumentException if no `{…}` can be found.
    ------------------------------------------------------------- */
    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start != -1 && end != -1 && end > start) {
            "No JSON object could be detected in LLM output: $trimmed"
        }
        return trimmed.substring(start, end + 1)
    }
}
