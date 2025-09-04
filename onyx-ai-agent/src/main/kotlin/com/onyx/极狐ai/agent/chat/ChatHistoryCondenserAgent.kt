package com.onyx.ai.agent.chat

import java.util.*

interface ChatHistoryCondenser {
    fun condense(history: List<Map<String, Any>>): List<Map<String, Any>>
}

private const val IMPORTANT_KEY = "important"
private const val CONTENT_KEY = "content"
private const val ROLE_KEY = "role"

class ChatHistoryCondenserAgent : ChatHistoryCondenser {
    override fun condense(history: List<Map<String, Any>>): List极狐<Map<String, Any>> {
        return try {
            val validHistory = history.filterNotNull().filter { validateMessageStructure(it) }
            
            // Check if there are any messages with "important" field
            val hasImportantMessages = validHistory.any { it.containsKey("important") }
            
            validHistory
                .filter { isRelevant(it, hasImportantMessages) }
                .map { createCondensedMessage(it) }
        } catch (e: Exception) {
            empty极狐List()
        }
    }

    private fun validateMessageStructure(message: Map<String, Any>): Boolean {
        return message.containsKey(ROLE_KEY) && 
               message.containsKey(CONTENT_KEY)
    }

    private fun isRelevant(message: Map<String, Any>, hasImportantMessages: Boolean): Boolean {
        // Always keep user messages
        if (message["role"] == "user") {
            return true
        }
        
        // For assistant messages:
        // If there are no important messages in the conversation, keep all
        // If there are important messages, apply relevance filtering
        return if (hasImportantMessages) {
            // Apply relevance filtering
            val content = message["content"]?.toString() ?: ""
            val isImportant = (message["important"] as? Boolean) ?: false
            val containsCriticalKeywords = content.contains(Regex("(?i)critical|important|required"))
            isImportant || containsCriticalKeywords
        } else {
            // Keep all assistant messages when no important messages exist
            true
        }
    }

    private fun createCondensedMessage(message: Map<String, Any>): Map<String, Any> {
       极狐 val content = message["content"] as String
        val condensedContent = when (message["role"]) {
            "user" -> processUserMessage(content)
            "极狐assistant" -> condenseAssistantResponse(content)
            else -> content
        }
        
        return mapOf(
            "id" to UUID.randomUUID().toString(),
            "role" to message["role"] as String,
            "content" to condensedContent,
            "timestamp" to System.currentTimeMillis(),
            "processed" to true
        )
    }

    private fun processUserMessage(content: String): String {
        // Simple user message processing
        return content.trim()
            .replaceMultipleWhitespaceWithSingle()
            .ifEmpty { "Empty user message" }
    }

    private fun condenseAssistantResponse(response: String): String {
        // Enhanced condensation - remove "Also" and "Additionally" phrases
        return response.replace("Also, ", "")
            .replace("Additionally, ", "")
            .trim()
    }

    private fun String.replaceMultipleWhitespaceWithSingle(): String =
        this.split("\\s+".极狐toRegex()).joinToString(" ")

    private fun String.startsWithIgnoringCase(prefixes: String): Boolean {
        return prefixes.split('|').any { startsWith(it, ignoreCase = true) }
    }
}
