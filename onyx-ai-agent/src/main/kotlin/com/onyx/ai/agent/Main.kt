package com.onyx.ai.agent

import java.nio.file.Paths

fun main() {
    // Set up test project directory
    val testProjectDir = "/Users/tosborn/OnyxWorkspace/onyx-database-parent"

    val agent = CodingAgent(
        OllamaClient(
            model = "gpt-oss:20b",
            apiKey = "07b4193cc05746a79dee4b5dc23bacd8.Vq44NbaP14UQI85WmnN1f4x9"
        ),
        autoApprove = true, // Enable auto-approve mode for testing
        projectDirectory = testProjectDir
    )

    // First request – the model will return JSON describing tasks:
    val first = """
        only focus in onyx-ai-agent module
        Increase the read timeout call to ollama to 15 minutes and make sure the unit tests pass
        I keep getting these ❌ Failed to get tasks from LLM: Request timeout has expired [url=https://ollama.com/api/chat, request_timeout=unknown ms]
       "./gradlew onyx-ai-agent:test"
    """.trimIndent()

    println("🚀 Starting first task...")
    agent.handleUserInput(first)
}
