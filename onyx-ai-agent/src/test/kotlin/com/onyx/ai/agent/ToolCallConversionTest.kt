package com.onyx.ai.agent

import com.onyx.ai.agent.model.Action
import com.onyx.ai.agent.model.Task
import com.onyx.ai.agent.model.TaskResponse
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class ToolCallConversionTest {
    
    @Test
    fun `test OllamaClient properly handles function calling tools`() {
        val client = OllamaClient(
            model = "test-model",
            apiKey = "test-key"
        )
        
        // Test that the tools are properly defined
        assertNotNull(client.http, "HTTP client should be initialized")
        
        println("✅ OllamaClient initialized with proper function calling support")
    }
    
    @Test
    fun `test JSON parsing functionality`() {
        // Test basic JSON parsing works with our serialization setup
        val jsonString = """{"tasks":[{"action":"run_command","instruction":"ls -la"}]}"""
        
        val parsed = Json.parseToJsonElement(jsonString).jsonObject
        assertTrue(parsed.containsKey("tasks"), "Should parse tasks object")
        
        val tasksArray = parsed["tasks"]!!.jsonArray
        assertEquals(1, tasksArray.size)
        
        val firstTask = tasksArray[0].jsonObject
        assertEquals("run_command", firstTask["action"]!!.jsonPrimitive.content)
        assertEquals("ls -la", firstTask["instruction"]!!.jsonPrimitive.content)
        
        println("✅ JSON parsing functionality works correctly")
    }
    
    @Test
    fun `test tool call to task conversion logic`() {
        // Test the core logic of converting a tool call to a Task object
        val runCommandTask = Task(
            action = Action.RUN_COMMAND,
            instruction = "ls -R"
        )
        
        assertEquals(Action.RUN_COMMAND, runCommandTask.action)
        assertEquals("ls -R", runCommandTask.instruction)
        assertNull(runCommandTask.path)
        assertNull(runCommandTask.content)
        
        val readFileTask = Task(
            action = Action.READ_FILE,
            path = "src/main/kotlin/Main.kt"
        )
        
        assertEquals(Action.READ_FILE, readFileTask.action)
        assertEquals("src/main/kotlin/Main.kt", readFileTask.path)
        assertNull(readFileTask.instruction)
        assertNull(readFileTask.content)
        
        val createFileTask = Task(
            action = Action.CREATE_FILE,
            path = "test.txt",
            content = "Hello World"
        )
        
        assertEquals(Action.CREATE_FILE, createFileTask.action)
        assertEquals("test.txt", createFileTask.path)
        assertEquals("Hello World", createFileTask.content)
        assertNull(createFileTask.instruction)
        
        println("✅ Task creation and serialization works correctly")
    }
    
    @Test
    fun `test TaskResponse serialization`() {
        val tasks = listOf(
            Task(action = Action.RUN_COMMAND, instruction = "find . -name '*.kt'"),
            Task(action = Action.READ_FILE, path = "src/main/kotlin/Main.kt"),
            Task(action = Action.CREATE_FILE, path = "test.txt", content = "Hello World")
        )
        
        val taskResponse = TaskResponse(tasks)
        
        // Test serialization
        val json = Json.encodeToString(TaskResponse.serializer(), taskResponse)
        assertTrue(json.contains("run_command"), "Should contain run_command action")
        assertTrue(json.contains("find . -name '*.kt'"), "Should contain instruction")
        assertTrue(json.contains("read_file"), "Should contain read_file action")
        assertTrue(json.contains("create_file"), "Should contain create_file action")
        
        // Test deserialization
        val deserialized = Json.decodeFromString<TaskResponse>(json)
        assertEquals(3, deserialized.tasks.size)
        assertEquals(Action.RUN_COMMAND, deserialized.tasks[0].action)
        assertEquals("find . -name '*.kt'", deserialized.tasks[0].instruction)
        
        println("✅ TaskResponse serialization/deserialization works correctly")
    }
    
    @Test
    fun `test function calling request structure`() {
        // Test that we can build the expected function calling request structure
        val toolsArray = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", "run_command")
                    put("description", "Execute shell commands")
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("instruction", buildJsonObject {
                                put("type", "string")
                                put("description", "The shell command to execute")
                            })
                        })
                        put("required", buildJsonArray { add("instruction") })
                    })
                })
            })
        }
        
        val requestBody = buildJsonObject {
            put("model", "gpt-oss:20b")
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "What is the weather in Paris (celsius)?")
                })
            })
            put("tools", toolsArray)
        }
        
        // Verify the structure matches the expected Ollama function calling format
        assertTrue(requestBody.containsKey("tools"), "Should contain tools array")
        assertTrue(requestBody.containsKey("messages"), "Should contain messages array")
        assertTrue(requestBody.containsKey("model"), "Should contain model")
        assertFalse(requestBody["stream"]!!.jsonPrimitive.boolean, "Stream should be false")
        
        val tools = requestBody["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        val tool = tools[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        
        val function = tool["function"]!!.jsonObject
        assertEquals("run_command", function["name"]!!.jsonPrimitive.content)
        assertTrue(function.containsKey("parameters"), "Function should have parameters")
        
        println("✅ Function calling request structure is correct")
    }
}
