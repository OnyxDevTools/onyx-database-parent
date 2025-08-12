package com.onyx.ai.agent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue
import kotlin.test.assertContains

class ProjectContextTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    private lateinit var agent: CodingAgent
    
    @BeforeEach
    fun setup() {
        // Create an agent with project directory configuration
        agent = CodingAgent(
            ollama = OllamaClient(
                model = "test-model",
                apiKey = "test-key"
            ),
            autoApprove = true,
            projectDirectory = tempDir.toString()
        )
    }
    
    @Test
    fun `test project directory configuration`() {
        // Verify that the project directory was set correctly
        assertTrue(ProjectIO.root == tempDir)
    }
    
    @Test
    fun `test project file listing`() {
        // Create some test files
        tempDir.resolve("src/main/kotlin").toFile().mkdirs()
        tempDir.resolve("src/test/kotlin").toFile().mkdirs()
        tempDir.resolve("build.gradle.kts").toFile().writeText("plugins { kotlin(\"jvm\") }")
        tempDir.resolve("src/main/kotlin/Main.kt").toFile().writeText("fun main() {}")
        tempDir.resolve("src/test/kotlin/MainTest.kt").toFile().writeText("// test")
        
        // Use reflection to access private method for testing
        val method = agent::class.java.getDeclaredMethod("getProjectFileList")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val fileList = method.invoke(agent) as List<String>
        
        // Verify that all files are listed
        assertTrue(fileList.contains("build.gradle.kts"))
        assertTrue(fileList.contains("src/main/kotlin/Main.kt"))
        assertTrue(fileList.contains("src/test/kotlin/MainTest.kt"))
    }
    
    @Test
    fun `test agent readme ingestion`() {
        // Create an Agent.md file
        val agentMd = """
            # Test Project
            This is a test project for the AI agent.
            
            ## Guidelines
            - Use Kotlin for all code
            - Follow testing best practices
        """.trimIndent()
        
        tempDir.resolve("Agent.md").toFile().writeText(agentMd)
        
        // Use reflection to access private method for testing
        val method = agent::class.java.getDeclaredMethod("getAgentReadme")
        method.isAccessible = true
        val readme = method.invoke(agent) as String
        
        // Verify that the readme content is read correctly
        assertContains(readme, "Test Project")
        assertContains(readme, "Use Kotlin for all code")
    }
    
    @Test
    fun `test prompt enrichment with project context`() {
        // Create test project structure
        tempDir.resolve("src/main/kotlin").toFile().mkdirs()
        tempDir.resolve("build.gradle.kts").toFile().writeText("plugins { kotlin(\"jvm\") }")
        tempDir.resolve("src/main/kotlin/Calculator.kt").toFile().writeText("class Calculator")
        
        val agentMd = """
            # Calculator Project
            Build a calculator with basic operations.
        """.trimIndent()
        tempDir.resolve("Agent.md").toFile().writeText(agentMd)
        
        val originalPrompt = "Add a multiply function to the calculator"
        
        // Use reflection to access private method
        val method = agent::class.java.getDeclaredMethod("enrichPromptWithProjectContext", String::class.java)
        method.isAccessible = true
        val enrichedPrompt = method.invoke(agent, originalPrompt) as String
        
        // Verify the prompt contains original request
        assertContains(enrichedPrompt, originalPrompt)
        
        // Verify the prompt contains project context
        assertContains(enrichedPrompt, "PROJECT CONTEXT:")
        assertContains(enrichedPrompt, "Project Structure:")
        assertContains(enrichedPrompt, "build.gradle.kts")
        assertContains(enrichedPrompt, "Calculator.kt")
        
        // Verify the prompt contains Agent.md content
        assertContains(enrichedPrompt, "Agent Documentation:")
        assertContains(enrichedPrompt, "Calculator Project")
        
        // Verify working directory is included
        assertContains(enrichedPrompt, "Working Directory:")
    }
}
