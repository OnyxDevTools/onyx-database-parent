package com.onyx.ai.agent

import com.onyx.ai.agent.model.Action
import com.onyx.ai.agent.model.Task
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CompleteActionTest {

    @Test
    fun `COMPLETE action should be properly serialized and deserialized`() {
        // Given a COMPLETE task
        val task = Task(
            action = Action.COMPLETE,
            content = "Task completed successfully with all tests passing"
        )
        
        // When we access the action
        val action = task.action
        
        // Then it should be COMPLETE
        assertEquals(Action.COMPLETE, action)
        assertEquals("Task completed successfully with all tests passing", task.content)
    }
    
    @Test
    fun `COMPLETE action should be included in Action enum`() {
        // Given the Action enum
        val actions = Action.values()
        
        // Then it should contain COMPLETE
        assertTrue(actions.contains(Action.COMPLETE))
        
        // And it should be the last action (sixth action after the original five)
        assertEquals(6, actions.size)
        assertEquals(Action.COMPLETE, actions.last())
    }
    
    @Test
    fun `COMPLETE action should not require path or instruction`() {
        // Given a COMPLETE task with only content
        val task = Task(
            action = Action.COMPLETE,
            content = "All requirements satisfied"
        )
        
        // Then it should be valid without path or instruction
        assertNull(task.path)
        assertNull(task.instruction)
        assertNotNull(task.content)
        assertEquals(Action.COMPLETE, task.action)
    }
    
    @Test
    fun `COMPLETE action should work with minimal content`() {
        // Given a COMPLETE task with minimal content
        val task = Task(
            action = Action.COMPLETE,
            content = null
        )
        
        // Then it should still be valid
        assertEquals(Action.COMPLETE, task.action)
        assertNull(task.content)
    }
    
    @Test
    fun `COMPLETE action serialization name should be correct`() {
        // Given the Action enum values
        val completeAction = Action.COMPLETE
        
        // Then the action should exist and be serializable
        assertNotNull(completeAction)
        assertEquals("COMPLETE", completeAction.name)
    }
}
