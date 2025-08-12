package com.onyx.ai.agent

import com.onyx.ai.agent.model.TaskResponse
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
    internal val baseUrl: String = "https://ollama.com",   // <-- most likely change needed

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
    1️⃣  System prompt that *forces* JSON‑only output.
    ------------------------------------------------------------- */
    private val systemPrompt = """
        You are a powerful AI coding assistant that can perform comprehensive software development tasks through a JSON-based action system.
        
        CRITICAL INSTRUCTIONS:
        - You MUST respond with ONLY a JSON object, nothing else
        - Do NOT use tool_calls, function calls, or any external tools
        - Do NOT wrap JSON in markdown code blocks or fences
        - Do NOT add explanatory text before or after the JSON
        - Do NOT use repo_browser, apply_patch, or any other tools
        
        YOUR CAPABILITIES:
        You can perform MULTIPLE tasks in sequence and iteratively solve complex problems:
        
        📂 FILE OPERATIONS:
        - "read_file": Read and examine existing files to understand code structure
        - "create_file": Create new files with any content
        - "edit_file": Modify existing files completely (full file replacement)
        - "delete_file": Remove files when needed
        
        💻 COMMAND EXECUTION:
        - "run_command": Execute ANY shell commands to:
          * Gather information (ls, find, grep, cat, etc.)
          * Build and compile projects (gradle, maven, make, etc.)
          * Run tests and check results
          * Install dependencies
          * Git operations
          * System exploration and diagnostics
        
        🔄 ITERATIVE PROBLEM SOLVING:
        - You can provide multiple tasks in a single response
        - Each task result will be sent back to you for analysis
        - You can then provide additional tasks based on the results
        - Continue until the problem is fully solved
        - Use terminal commands to gather information before making changes
        
        Your response must be a valid JSON object matching this exact schema:
        {
          "tasks": [
            {
              "action": "create_file|edit_file|delete_file|read_file|run_command",
              "path": "<file-path-when-needed>",
              "content": "<file-content-when-needed>",
              "instruction": "<command-when-needed>"
            }
          ]
        }
        
        EXAMPLES:
        
        Explore project structure:
        {"tasks":[{"action":"run_command","instruction":"find . -name '*.kt' | head -10"}]}
        
        Read a file to understand it:
        {"tasks":[{"action":"read_file","path":"src/main/kotlin/Main.kt"}]}
        
        Multiple sequential tasks:
        {"tasks":[
          {"action":"run_command","instruction":"ls -la"},
          {"action":"read_file","path":"build.gradle.kts"},
          {"action":"run_command","instruction":"./gradlew test"}
        ]}
        
        IMPORTANT: Think step by step and use information-gathering commands before making changes!
    """.trimIndent()

    /** -------------------------------------------------------------
    The JSON‑schema we send to Ollama (only used when talking to a *real* Ollama server).
    ------------------------------------------------------------- */
    private val taskSchema = JsonObject(
        mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(
                mapOf(
                    "tasks" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "items" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(
                                        mapOf(
                                            "action" to JsonObject(
                                                mapOf(
                                                    "type" to JsonPrimitive("string"),
                                                    "enum" to JsonArray(
                                                        listOf(
                                                            JsonPrimitive("create_file"),
                                                            JsonPrimitive("edit_file"),
                                                            JsonPrimitive("delete_file"),
                                                            JsonPrimitive("read_file"),
                                                            JsonPrimitive("run_command")
                                                        )
                                                    )
                                                )
                                            ),
                                            "path" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                                            "content" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                                            "instruction" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                                        )
                                    ),
                                    "required" to JsonArray(listOf(JsonPrimitive("action")))
                                )
                            )
                        )
                    )
                )
            ),
            "required" to JsonArray(listOf(JsonPrimitive("tasks")))
        )
    )

    /** -------------------------------------------------------------
    Helper – adds   Authorization: Bearer <apiKey>
    ------------------------------------------------------------- */
    private fun HttpRequestBuilder.bearerHeader() {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
    }

    /** -------------------------------------------------------------
    2️⃣  Ask the model for a **structured list of tasks**.
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
        /* --------- Build the request body ----------------------- */
        val body = buildJsonObject {
            put("model", model)

            // ---- messages ----------------------------------------------------
            put("messages", buildJsonArray {
                // system message – bans tool calls & forces JSON
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
                
                // actual user request
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })

            put("stream", false)

            // -----------------------------------------------------------------
            // ---- Two possible flavours – pick the one that matches your backend
            // -----------------------------------------------------------------
            if (baseUrl.contains("ollama", ignoreCase = true)) {
                // Pure Ollama server – it understands the `format` key
                put("format", taskSchema)
            } else {
                // OpenAI‑compatible proxy – use `options.response_format`
                put("options", buildJsonObject {
                    put("response_format", "json_object")
                    // Disable tools (OpenAI 1.0+)
                    put("tool_choice", "none")
                })
            }
        }

        /* --------- Send the HTTP request ------------------------ */
        val raw = http.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            bearerHeader()
            setBody(body)
        }.bodyAsText()

        // -----------------------------------------------------------------
        // 3️⃣ Show the raw payload – helps you debug the next time
        // -----------------------------------------------------------------
        println("🟢 Raw HTTP response:\n$raw")

        // -----------------------------------------------------------------
        // 4️⃣ Extract the assistant's `content` field (the model's reply)
        // -----------------------------------------------------------------
        val outer = Json.parseToJsonElement(raw).jsonObject
        val messageObj = outer["message"]?.jsonObject
            ?: error("Response does not contain a `message` object")

        // Some proxies put the answer into `content`, others into `tool_calls`,
        // `thinking` etc. We first try `content`.
        val rawContent = messageObj["content"]?.jsonPrimitive?.contentOrNull
            ?: messageObj["thinking"]?.jsonPrimitive?.contentOrNull
            ?: ""

        // -----------------------------------------------------------------
        // 5️⃣ Guard‑rail: pull out the first JSON object that appears
        // -----------------------------------------------------------------
        val jsonOnly = extractJsonObject(rawContent)

        // -----------------------------------------------------------------
        // 6️⃣ Deserialize to our DTO
        // -----------------------------------------------------------------
        return Json.decodeFromString<TaskResponse>(jsonOnly)
    }

    /** -------------------------------------------------------------
    3️⃣  Plain‑text chat – used for summarising, follow‑up questions, etc.
    ------------------------------------------------------------- */
    suspend fun chatPlain(messages: List<JsonObject>): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages))
            put("stream", false)

            // Same handling for pure Ollama vs OpenAI‑proxy as above
            if (baseUrl.contains("ollama", ignoreCase = true).not()) {
                put("options", buildJsonObject {
                    put("response_format", "text")
                    put("tool_choice", "none")
                })
            }
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
    Helper – extracts the **first** JSON object from a possibly noisy string.
    Throws IllegalArgumentException if no `{…}` can be found.
    ------------------------------------------------------------- */
    private fun extractJsonObject(text: String): String {
        // Trim whitespace first – makes debugging output a little cleaner
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start != -1 && end != -1 && end > start) {
            "No JSON object could be detected in LLM output: $trimmed"
        }
        return trimmed.substring(start, end + 1)
    }
}
