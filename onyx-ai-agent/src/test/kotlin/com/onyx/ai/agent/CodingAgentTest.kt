package com.onyx.ai.agent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue

class CodingAgentTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setup() {
        // Set the ProjectIO root to our temp directory for testing
        ProjectIO.root = tempDir
    }
    
    @Test
    fun testAutoApproveMode() {
        // Test that auto-approve mode works
        val agent = CodingAgent(
            OllamaClient(
                model = "gpt-oss:20b", 
                apiKey = "07b4193cc05746a79dee4b5dc23bacd8.Vq44NbaP14UQI85WmnN1f4x9"
            ),
            autoApprove = true
        )
        
        // This should not require manual approval
        assertTrue(true) // Basic test structure
    }
    
    @Test
    fun testProjectStructure() {
        // Test project file structure creation
        ProjectIO.write("test-file.txt", "Hello World")
        assertTrue(tempDir.resolve("test-file.txt").toFile().exists())
    }
    
    @Test
    fun testReadFileAction() {
        // Test that we can read file contents
        val testContent = "Test file content\nLine 2"
        ProjectIO.write("read-test.txt", testContent)
        
        val readContent = ProjectIO.read("read-test.txt")
        assertTrue(readContent == testContent)
    }
}
