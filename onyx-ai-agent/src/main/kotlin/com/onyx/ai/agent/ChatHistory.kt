package com.onyx.ai.agent

import com.onyx.ai.agent.model.TaskResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private const val MAX_TOKENS = 128_000          // Ollama’s hard limit
private const val SAFETY_MARGIN = 4_000        // keep a small safety buffer

/** Rough 4‑char ≈ 1‑token estimate – good enough for budgeting. */
private fun approxTokenCount(text: String): Int = maxOf(1, text.length / 4)

/**
 * Holds the message history that will be sent to Ollama.
 * Old messages are automatically summarised when the token budget is exceeded.
 */
class ChatHistory(
    private val model: String = "codellama:13b",
    private val client: OllamaClient
) {
    @kotlinx.serialization.Serializable
    data class Msg(val role: String, val content: String)

    private val messages = mutableListOf<Msg>()
    private var tokenCount = 0

    /** Public helpers ---------------------------------------------------- */
    fun user(text: String)      = add(Msg("user", text))
    fun assistant(text: String) = add(Msg("assistant", text))
    fun system(text: String)    = add(Msg("system", text))

    /** Append a message and update the token counter. */
    private fun add(msg: Msg) {
        messages += msg
        tokenCount += approxTokenCount(msg.content)
    }

    /** Payload that the `/api/chat` endpoint expects (plain‑text chat). */
    private fun payload(): String = Json.encodeToString(
        mapOf(
            "model"    to model,
            "messages" to messages,
            "stream"   to false
        )
    )
    
    /** Get the current messages for external use */
    fun getMessages(): List<Msg> = messages.toList()
    
    /** Get the current message history as JSON objects for LLM consumption */
    fun getMessageHistory(): String {
        val historyPrompt = buildString {
            appendLine("Previous conversation context:")
            messages.forEach { msg ->
                appendLine("[${msg.role.uppercase()}] ${msg.content}")
            }
        }
        return historyPrompt
    }

    /** Sends the current history, stores the assistant reply and returns it. */
    suspend fun chatAndGetReply(): String {
        if (tokenCount > MAX_TOKENS - SAFETY_MARGIN) compressHistory()

        val raw = client.http.post("${client.baseUrl}/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(payload())
        }.bodyAsText()

        val content = Json.parseToJsonElement(raw)
            .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content

        assistant(content)                // keep the reply in the history
        return content
    }

    /** ---------------------------------------------------------------
     *  If the token budget is exceeded we replace the oldest part of the
     *  conversation with a short *system* message that contains a summary.
     *  --------------------------------------------------------------- */
    private fun compressHistory() = runBlocking {
        val keepLast = 8                                 // keep recent tail
        val (old, recent) = messages.partition {
            messages.indexOf(it) < messages.size - keepLast
        }

        if (old.isEmpty()) return@runBlocking

        // Build a prompt for the summariser LLM
        val prompt = buildString {
            appendLine("You are a summariser for a programming assistant.")
            appendLine("Summarise the conversation below in 1‑2 sentences, preserving the overall goal.")
            appendLine()
            appendLine("--- BEGIN CONVERSATION ---")
            old.forEach { appendLine("[${it.role.uppercase()}] ${it.content.trim()}") }
            appendLine("--- END CONVERSATION ---")
            appendLine("Summary:")
        }

        val summariser = Summariser(client)
        val summary = summariser.summarise(prompt)

        // Reset history: one system message + the recent tail
        messages.clear()
        tokenCount = 0
        system("Conversation summary: $summary")
        recent.forEach { add(it) }

        println("\n🗜️  History compressed → new system prompt:\n$summary\n")
    }
}

/** -----------------------------------------------------------------
 *  Helper that asks Ollama for a *plain‑text* summary.
 *  The request uses the `format` field so Ollama returns a JSON object
 *  `{ "summary": "…" }`.  We unwrap that JSON and hand the string back.
 *  ----------------------------------------------------------------- */
private class Summariser(private val client: OllamaClient) {
    // JSON schema that forces a single‑field output
    private val summarySchema = JsonObject(
        mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(
                mapOf(
                    "summary" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                )
            ),
            "required" to JsonArray(listOf(JsonPrimitive("summary")))
        )
    )

    suspend fun summarise(userPrompt: String): String {
        // Build request body exactly like the normal chat request, but with `format`
        val body = JsonObject(
            mapOf(
                "model"    to JsonPrimitive(client.model),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "role"    to JsonPrimitive("user"),
                                "content" to JsonPrimitive(userPrompt)
                            )
                        )
                    )
                ),
                "stream"   to JsonPrimitive(false),
                "format"   to summarySchema
            )
        )

        val raw = client.http.post("${client.baseUrl}/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()

        // Ollama wraps our summary JSON inside `message.content`
        val outer = Json.parseToJsonElement(raw).jsonObject
        val inner = outer["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        val parsed = Json.parseToJsonElement(inner).jsonObject
        return parsed["summary"]!!.jsonPrimitive.content
    }
}
