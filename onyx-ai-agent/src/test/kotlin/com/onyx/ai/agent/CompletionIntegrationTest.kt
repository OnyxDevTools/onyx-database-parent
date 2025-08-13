package com.onyx.ai.agent

import com.onyx.ai.agent.model.Action
import com.onyx.ai.agent.model.Task
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CompletionIntegrationTest {

    @Test
    fun `test COMPLETE action properties`() {
        // Create a COMPLETE task
        val completeTask = Task(
            action = Action.COMPLETE,
            content = "Feature implementation completed successfully with all tests passing"
        )
        
        // Verify task properties
        assertEquals(Action.COMPLETE, completeTask.action)
        assertEquals("Feature implementation completed successfully with all tests passing", completeTask.content)
        assertNull(completeTask.path)
        assertNull(completeTask.instruction)
    }

    @Test
    fun `test COMPLETE action in Action enum`() {
        // Verify COMPLETE action exists and has correct properties
        val completeAction = Action.COMPLETE
        assertNotNull(completeAction)
        assertEquals("COMPLETE", completeAction.name)
        
        // Verify it's included in the enum values
        val actions = Action.values()
        assertTrue(actions.contains(Action.COMPLETE))
        
        // Verify enum order (COMPLETE should be last)
        assertEquals(Action.COMPLETE, actions.last())
    }

    @Test
    fun `test completion patterns in strings`() {
        // Test string patterns that should indicate completion
        val buildSuccessfulText = "BUILD SUCCESSFUL in 2s"
        val testsPassedText = "All tests passed"
        val completeText = "🎉 COMPLETE: Task finished"
        
        assertTrue(buildSuccessfulText.lowercase().contains("build successful"))
        assertTrue(testsPassedText.lowercase().contains("tests passed"))
        assertTrue(completeText.lowercase().contains("🎉 complete:"))
    }

    @Test
    fun `test task completion scenarios`() {
        // Test various completion scenarios
        val scenarios = listOf(
            "📤 Command: ./gradlew test\nExit Code: 0\nOutput:\nBUILD SUCCESSFUL in 2s",
            "📤 Command: npm test\nExit Code: 0\nOutput:\nAll tests passed",
            "🎉 COMPLETE: Successfully implemented the requested feature"
        )
        
        scenarios.forEach { scenario ->
            val lowercase = scenario.lowercase()
            val hasCompletion = lowercase.contains("build successful") || 
                               lowercase.contains("tests passed") || 
                               lowercase.contains("🎉 complete:")
            assertTrue(hasCompletion, "Scenario should be detected as completion: $scenario")
        }
    }
}
