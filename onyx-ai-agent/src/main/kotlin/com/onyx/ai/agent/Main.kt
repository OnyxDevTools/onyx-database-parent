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
        Can you output the message thinking for each iteration insgtead of the Iteration number
        
        only focus in onyx-ai-agent module. 

        You may complete your task only if the unit tests pass.  If it broke, fix it!
       "./gradlew onyx-ai-agent:test"
    """.trimIndent()

    println("🚀 Starting first task...")
    agent.handleUserInput(first)
}
