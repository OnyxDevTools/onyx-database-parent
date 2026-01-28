@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyx.cloud.impl

import com.onyx.cloud.api.FetchInit
import com.onyx.cloud.exceptions.NotFoundException
import com.onyx.cloud.extensions.fromJson
import com.onyx.cloud.extensions.toJson
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * AI/Chat client for Onyx Cloud Database.
 *
 * Provides access to chat completions, model management, and script approval APIs.
 * Uses the same authentication (apiKey/apiSecret) as the database client.
 *
 * Example usage:
 * ```kotlin
 * val db = onyx.init()
 *
 * // Quick chat completion
 * val response = db.chat("Summarize last week's signups.")
 *
 * // Full chat request
 * val completion = db.ai.chat(ChatRequest(
 *     model = "onyx-chat",
 *     messages = listOf(ChatMessage("user", "Hello!"))
 * ))
 *
 * // Get available models
 * val models = db.ai.getModels()
 * ```
 *
 * @param client The parent OnyxClient for authentication and HTTP utilities.
 */
class OnyxAI internal constructor(private val client: OnyxClient) {

    /**
     * Performs a chat completion request.
     *
     * @param request The chat request with model, messages, and optional parameters.
     * @return The chat completion response.
     */
    fun chat(request: ChatRequest): ChatResponse {
        val response = makeAIRequest("POST", "/v1/chat/completions", request)
        return response.fromJson<ChatResponse>()
            ?: throw IllegalStateException("Failed to parse chat response")
    }

    /**
     * Performs a chat completion with a Map-based request for full flexibility.
     *
     * @param request A map containing the chat request parameters.
     * @return The raw response as a Map.
     */
    fun chat(request: Map<String, Any?>): Map<String, Any?> {
        val response = makeAIRequest("POST", "/v1/chat/completions", request)
        return response.fromJson<Map<String, Any?>>()
            ?: throw IllegalStateException("Failed to parse chat response")
    }

    /**
     * Performs a streaming chat completion request.
     *
     * @param request The chat request (must have stream=true).
     * @param onChunk Callback invoked for each SSE chunk received.
     */
    fun chatStream(request: ChatRequest, onChunk: (ChatStreamChunk) -> Unit) {
        val streamRequest = request.copy(stream = true)
        streamAIRequest("POST", "/v1/chat/completions", streamRequest) { line ->
            if (line.startsWith("data: ") && line != "data: [DONE]") {
                val json = line.removePrefix("data: ").trim()
                json.fromJson<ChatStreamChunk>()?.let(onChunk)
            }
        }
    }

    /**
     * Retrieves the list of available AI models.
     *
     * @return The models response containing available model information.
     */
    fun getModels(): ModelsResponse {
        val response = makeAIRequest("GET", "/v1/models")
        return response.fromJson<ModelsResponse>()
            ?: throw IllegalStateException("Failed to parse models response")
    }

    /**
     * Retrieves information about a specific model.
     *
     * @param modelId The model identifier.
     * @return The model information.
     */
    fun getModel(modelId: String): ModelInfo {
        val response = makeAIRequest("GET", "/v1/models/${client.encode(modelId)}")
        return response.fromJson<ModelInfo>()
            ?: throw IllegalStateException("Failed to parse model response")
    }

    /**
     * Requests approval for a database mutation script.
     *
     * @param script The script content to analyze.
     * @return The approval response with findings.
     */
    fun requestScriptApproval(script: String): ScriptApprovalResponse {
        val request = mapOf("script" to script)
        val response = makeAIRequest("POST", "/v1/script/approval", request)
        return response.fromJson<ScriptApprovalResponse>()
            ?: throw IllegalStateException("Failed to parse script approval response")
    }

    // ---------------------------------------------------------------------
    // Internal HTTP helpers
    // ---------------------------------------------------------------------

    private fun makeAIRequest(
        method: String,
        path: String,
        body: Any? = null
    ): String {
        val url = "${getAIBaseUrl()}$path"
        val headers = buildHeaders()
        val payload = if ((method == "POST" || method == "PUT") && body != null) {
            body.toJson()
        } else null

        return if (client.fetch != null) {
            executeWithFetch(url, method, headers, payload)
        } else {
            executeWithHttpUrlConnection(url, method, headers, payload)
        }
    }

    private fun streamAIRequest(
        method: String,
        path: String,
        body: Any?,
        onLine: (String) -> Unit
    ) {
        val url = "${getAIBaseUrl()}$path"
        val headers = buildHeaders()
        headers["Accept"] = "text/event-stream"
        val payload = body?.toJson()

        val urlObj = URI(url).toURL()
        val conn = urlObj.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 0 // infinite for streaming
            conn.doInput = true
            conn.doOutput = payload != null

            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            if (payload != null) {
                val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
                conn.setFixedLengthStreamingMode(payloadBytes.size)
                conn.outputStream.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                }
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = conn.bodyAsString()
                throw RuntimeException("HTTP Error: $code ${conn.responseMessage}. Body: $errorBody")
            }

            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        onLine(line)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun buildHeaders(): MutableMap<String, String> {
        return mutableMapOf(
            "x-onyx-key" to getApiKey(),
            "x-onyx-secret" to getApiSecret(),
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Connection" to "keep-alive"
        )
    }

    private fun getAIBaseUrl(): String {
        // Access via reflection since aiBaseUrl is private
        val field = OnyxClient::class.java.getDeclaredField("aiBaseUrl")
        field.isAccessible = true
        return field.get(client) as String
    }

    private fun getApiKey(): String {
        val field = OnyxClient::class.java.getDeclaredField("apiKey")
        field.isAccessible = true
        return field.get(client) as String
    }

    private fun getApiSecret(): String {
        val field = OnyxClient::class.java.getDeclaredField("apiSecret")
        field.isAccessible = true
        return field.get(client) as String
    }

    private fun executeWithFetch(
        url: String,
        method: String,
        headers: MutableMap<String, String>,
        payload: String?
    ): String {
        val init = FetchInit(method = method, headers = headers.toMap(), body = payload)
        val response = client.fetch!!.invoke(url, init)
        val text = response.text()
        if (response.status !in 200..299) {
            val msg = "HTTP ${response.status} @ $url → $text"
            throw when (response.status) {
                404 -> NotFoundException(msg, RuntimeException("HTTP ${response.status}"))
                else -> RuntimeException(msg)
            }
        }
        return text
    }

    private fun executeWithHttpUrlConnection(
        url: String,
        method: String,
        headers: MutableMap<String, String>,
        payload: String?
    ): String {
        val urlObj = URI(url).toURL()
        val conn = urlObj.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.doInput = true
            conn.doOutput = payload != null

            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            if (payload != null) {
                val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
                conn.setFixedLengthStreamingMode(payloadBytes.size)
                conn.outputStream.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                }
            }

            val code = conn.responseCode
            val stream = if (code >= 400) (conn.errorStream ?: conn.inputStream) else conn.inputStream
            val text = stream?.use { String(it.readBytes(), StandardCharsets.UTF_8) } ?: ""

            if (code !in 200..299) {
                val msg = "HTTP $code @ $url → $text"
                throw when (code) {
                    404 -> NotFoundException(msg, RuntimeException("HTTP $code"))
                    else -> RuntimeException(msg)
                }
            }

            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.bodyAsString(): String {
        fun readAll(input: InputStream): String =
            input.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
        return try {
            readAll(inputStream)
        } catch (_: Exception) {
            val err = errorStream
            if (err != null) readAll(err) else ""
        }
    }

    // Access the fetch property via the client
    private val OnyxClient.fetch: com.onyx.cloud.api.FetchImpl?
        get() {
            val field = OnyxClient::class.java.getDeclaredField("fetch")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return field.get(this) as? com.onyx.cloud.api.FetchImpl
        }
}

// =====================================================================
// AI Data Models
// =====================================================================

/**
 * A message in a chat conversation.
 *
 * @property role The role of the message author (e.g., "user", "assistant", "system").
 * @property content The content of the message.
 */
data class ChatMessage(
    val role: String,
    val content: String
)

/**
 * Request for a chat completion.
 *
 * @property model The model to use for completion (defaults to "onyx").
 * @property messages The conversation history.
 * @property stream Whether to stream the response.
 * @property temperature Sampling temperature (0-2).
 * @property maxTokens Maximum tokens to generate.
 * @property topP Top-p sampling parameter.
 * @property frequencyPenalty Frequency penalty (-2 to 2).
 * @property presencePenalty Presence penalty (-2 to 2).
 * @property stop Stop sequences.
 */
data class ChatRequest(
    val model: String = "onyx",
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stop: List<String>? = null
)

/**
 * Response from a chat completion.
 *
 * @property id Unique identifier for the completion.
 * @property object Object type (e.g., "chat.completion").
 * @property created Timestamp of creation.
 * @property model Model used for the completion.
 * @property choices The completion choices.
 * @property usage Token usage statistics.
 */
data class ChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: ChatUsage?
)

/**
 * A choice in a chat completion response.
 *
 * @property index The index of this choice.
 * @property message The assistant's response message.
 * @property finishReason The reason for finishing (e.g., "stop", "length").
 */
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    val finishReason: String?
)

/**
 * Token usage statistics for a completion.
 *
 * @property promptTokens Tokens used in the prompt.
 * @property completionTokens Tokens generated in the completion.
 * @property totalTokens Total tokens used.
 */
data class ChatUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * A chunk in a streaming chat completion.
 *
 * @property id Unique identifier for the completion.
 * @property object Object type.
 * @property created Timestamp of creation.
 * @property model Model used.
 * @property choices The delta choices.
 */
data class ChatStreamChunk(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<StreamChoice>
)

/**
 * A choice in a streaming response.
 *
 * @property index The index of this choice.
 * @property delta The incremental content.
 * @property finishReason The reason for finishing.
 */
data class StreamChoice(
    val index: Int,
    val delta: StreamDelta,
    val finishReason: String?
)

/**
 * Delta content in a streaming response.
 *
 * @property role The role (only in first chunk).
 * @property content The incremental content.
 */
data class StreamDelta(
    val role: String? = null,
    val content: String? = null
)

/**
 * Response containing available models.
 *
 * @property object Object type.
 * @property data List of available models.
 */
data class ModelsResponse(
    val `object`: String,
    val data: List<ModelInfo>
)

/**
 * Information about a model.
 *
 * @property id Model identifier.
 * @property object Object type.
 * @property created Timestamp of creation.
 * @property ownedBy Owner of the model.
 */
data class ModelInfo(
    val id: String,
    val `object`: String,
    val created: Long,
    val ownedBy: String
)

/**
 * Response from a script approval request.
 *
 * @property requiresApproval Whether the script requires manual approval.
 * @property findings List of findings that require attention.
 * @property approved Whether the script was auto-approved.
 */
data class ScriptApprovalResponse(
    val requiresApproval: Boolean,
    val findings: List<String>,
    val approved: Boolean
)
