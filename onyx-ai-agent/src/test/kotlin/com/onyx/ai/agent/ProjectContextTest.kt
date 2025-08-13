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
    fun `test project file listing with filtering`() {
        // Create some test files
        tempDir.resolve("build.gradle.kts").toFile().writeText("plugins { kotlin(\"jvm\") }")
        tempDir.resolve("README.md").toFile().writeText("# Project")
        
        // Use reflection to access private method for testing
        val method = agent::class.java.getDeclaredMethod("getProjectFileList")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val fileList = method.invoke(agent) as List<String>
        
        // Verify that regular files are included
        assertTrue(fileList.contains("build.gradle.kts"), "Should contain build.gradle.kts. Actual list: $fileList")
        assertTrue(fileList.contains("README.md"), "Should contain README.md. Actual list: $fileList")
    }
    
    @Test
    fun `test gitignore pattern loading`() {
        // Create a .gitignore file
        val gitignoreContent = """
            *.class
            target/
            build/
            *.log
        """.trimIndent()
        
        tempDir.resolve(".gitignore").toFile().writeText(gitignoreContent)
        
        // Use reflection to access private method for testing
        val method = agent::class.java.getDeclaredMethod("loadGitignorePatterns")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val patterns = method.invoke(agent) as List<String>
        
        // Verify that gitignore patterns are loaded correctly
        assertTrue(patterns.contains("*.class"))
        assertTrue(patterns.contains("target/"))
        assertTrue(patterns.contains("build/"))
    }
    
    @Test
    fun `test file filtering with gitignore`() {
        // Create test files and structure
        tempDir.resolve("src/main/kotlin").toFile().mkdirs()
        tempDir.resolve("build").toFile().mkdirs()
        tempDir.resolve("target").toFile().mkdirs()
        
        val testFile = tempDir.resolve("src/main/kotlin/Test.kt")
        testFile.toFile().writeText("class Test")
        
        val buildFile = tempDir.resolve("build/classes/Test.class")
        buildFile.parent.toFile().mkdirs()
        buildFile.toFile().writeText("compiled")
        
        val targetFile = tempDir.resolve("target/test.jar")
        targetFile.toFile().writeText("jar")
        
        // Use reflection to test file filtering
        val method = agent::class.java.getDeclaredMethod("getProjectFileList")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val fileList = method.invoke(agent) as List<String>
        
        // Verify that source files are included but build artifacts are excluded
        assertTrue(fileList.contains("src/main/kotlin/Test.kt"))
        assertTrue(!fileList.any { it.contains("build/") })
        assertTrue(!fileList.any { it.contains("target/") })
    }
}
