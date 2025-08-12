package com.onyx.ai.agent

import com.onyx.ai.agent.model.Action
import com.onyx.ai.agent.model.Task
import kotlinx.coroutines.runBlocking

class CodingAgent(
    private val ollama: OllamaClient = OllamaClient(
        model = "gpt-oss:20b",
        apiKey = System.getenv("OPENAI_API_KEY") ?: throw IllegalArgumentException("OPENAI_API_KEY not set")
    ),
    private val autoApprove: Boolean = false,
    private val projectDirectory: String? = null
) {
    private val history = ChatHistory(model = ollama.model, client = ollama)
    private val executionResults = mutableListOf<String>()
    
    init {
        // Configure project directory if specified
        projectDirectory?.let { 
            ProjectIO.root = java.nio.file.Paths.get(it)
            println("📁 Project directory set to: $it")
        }
    }

    /** Public entry – give the user request, get a JSON task list, execute it. */
    fun handleUserInput(userPrompt: String) = runBlocking {
        // Enrich the prompt with project context
        var currentPrompt = enrichPromptWithProjectContext(userPrompt)
        var maxIterations = 10 // Prevent infinite loops
        var iteration = 0
        
        while (iteration < maxIterations) {
            iteration++
            println("\n🔄 Iteration $iteration")
            
            // 1️⃣  Store the user message (or feedback from previous iteration)
            history.user(currentPrompt)

            // 2️⃣  Ask Ollama for a *structured* list of tasks
            val taskResponse = try {
                if (iteration == 1) {
                    ollama.askForTasks(currentPrompt)
                } else {
                    ollama.askForTasks(history.getMessages())
                }
            } catch (e: Exception) {
                println("❌ Failed to get tasks from LLM: ${e.message}")
                break
            }

            // 3️⃣  Echo the JSON back to the history (so the model sees its own output)
            history.assistant(taskResponse.toString())

            // 4️⃣  Safety: ask the human before any file‑system change (unless auto-approve)
            if (!autoApprove && taskResponse.tasks.any { it.action != Action.RUN_COMMAND }) {
                println("\n⚠️  The plan contains file changes. Type \"yes\" to continue:")
                val ok = java.util.Scanner(System.`in`).nextLine()
                if (ok.lowercase() != "yes") {
                    println("❌  Aborted by user.")
                    return@runBlocking
                }
            }

            // 5️⃣  Execute every task in order and collect results
            val taskResults = mutableListOf<String>()
            taskResponse.tasks.forEach { task ->
                val result = execute(task)
                taskResults.add(result)
            }

            println("\n✅  All ${taskResponse.tasks.size} tasks finished.")

            // 6️⃣  Check if we should continue (look for completion indicators)
            if (isComplete(taskResults) || taskResponse.tasks.isEmpty()) {
                println("🎉 Task completion detected!")
                break
            }

            // 7️⃣  Send results back to LLM for next iteration
            currentPrompt = buildFeedbackPrompt(taskResults)
        }
        
        if (iteration >= maxIterations) {
            println("⚠️  Maximum iterations reached. Stopping.")
        }
    }

    /** --------------------------------------------------------------- */
    private fun execute(task: Task): String {
        return when (task.action) {
            Action.CREATE_FILE -> {
                requireNotNull(task.path) { "CREATE_FILE needs a path" }
                requireNotNull(task.content) { "CREATE_FILE needs content" }
                ProjectIO.write(task.path, task.content)
                val result = "📄 Created ${task.path}"
                println(result)
                result
            }

            Action.EDIT_FILE -> {
                requireNotNull(task.path) { "EDIT_FILE needs a path" }
                val old = ProjectIO.read(task.path)
                val new = task.content
                    ?: throw IllegalArgumentException("EDIT_FILE needs content")
                ProjectIO.write(task.path, new)
                val diff = ProjectIO.diff(old, new)
                val result = "✏️  Edited ${task.path}\n$diff"
                println(result)
                result
            }

            Action.DELETE_FILE -> {
                requireNotNull(task.path) { "DELETE_FILE needs a path" }
                ProjectIO.delete(task.path)
                val result = "🗑️  Deleted ${task.path}"
                println(result)
                result
            }

            Action.RUN_COMMAND -> {
                requireNotNull(task.instruction) { "RUN_COMMAND needs an instruction" }
                println("🚀 Running: ${task.instruction}")
                val proc = ProcessBuilder(*task.instruction.split("\\s+".toRegex()).toTypedArray())
                    .directory(ProjectIO.root.toFile())
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText()
                val exitCode = proc.waitFor()
                val result = "📤 Command: ${task.instruction}\nExit Code: $exitCode\nOutput:\n$output"
                println(result)
                result
            }
        }
    }
    
    private fun isComplete(taskResults: List<String>): Boolean {
        val combinedResults = taskResults.joinToString("\n").lowercase()
        
        // Look for completion indicators
        return when {
            combinedResults.contains("build successful") -> true
            combinedResults.contains("tests passed") -> true
            combinedResults.contains("all tests passed") -> true
            combinedResults.contains("build failure") && combinedResults.contains("error") -> false
            taskResults.isEmpty() -> true
            else -> false
        }
    }
    
    private fun buildFeedbackPrompt(taskResults: List<String>): String {
        return buildString {
            appendLine("Here are the results from the previous tasks:")
            taskResults.forEachIndexed { index, result ->
                appendLine("Task ${index + 1} result:")
                appendLine(result)
                appendLine("---")
            }
            appendLine("Based on these results, what should be done next? If everything is working correctly, respond with an empty task list.")
        }
    }
    
    private fun enrichPromptWithProjectContext(userPrompt: String): String {
        return buildString {
            appendLine(userPrompt)
            appendLine()
            
            // Add project structure information
            appendLine("PROJECT CONTEXT:")
            appendLine("=================")
            
            val projectFiles = getProjectFileList()
            appendLine("Project Structure:")
            projectFiles.forEach { appendLine("  $it") }
            appendLine()
            
            // Add Agent.md content if it exists
            val agentReadme = getAgentReadme()
            if (agentReadme.isNotEmpty()) {
                appendLine("Agent Documentation:")
                appendLine(agentReadme)
                appendLine()
            }
            
            appendLine("Working Directory: ${ProjectIO.root}")
        }
    }
    
    private fun getProjectFileList(): List<String> {
        return try {
            val gitignorePatterns = loadGitignorePatterns()
            ProjectIO.root.toFile().walk()
                .filter { it.isFile }
                .filter { file -> !isHiddenFile(file) }
                .filter { file -> !isGitignored(file, gitignorePatterns) }
                .map { it.relativeTo(ProjectIO.root.toFile()).path }
                .sorted()
                .toList()
        } catch (e: Exception) {
            listOf("Error reading project files: ${e.message}")
        }
    }
    
    private fun isHiddenFile(file: java.io.File): Boolean {
        val path = file.relativeTo(ProjectIO.root.toFile()).path
        return path.split("/").any { it.startsWith(".") }
    }
    
    private fun isGitignored(file: java.io.File, gitignorePatterns: List<String>): Boolean {
        val relativePath = file.relativeTo(ProjectIO.root.toFile()).path
        return gitignorePatterns.any { pattern ->
            when {
                pattern.isEmpty() || pattern.startsWith("#") -> false
                pattern.endsWith("/") -> {
                    val dirName = pattern.removeSuffix("/")
                    relativePath == dirName || relativePath.startsWith("$dirName/")
                }
                pattern.contains("*") -> {
                    val regex = pattern.replace("*", ".*").toRegex()
                    regex.matches(relativePath)
                }
                else -> relativePath == pattern || relativePath.startsWith("$pattern/")
            }
        }
    }
    
    private fun loadGitignorePatterns(): List<String> {
        return try {
            val gitignoreFile = ProjectIO.root.resolve(".gitignore").toFile()
            if (gitignoreFile.exists()) {
                gitignoreFile.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            } else {
                // Default patterns to ignore common files
                listOf(
                    "*.class",
                    "*.log",
                    "*.jar",
                    "*.war",
                    "*.ear",
                    "target/",
                    "build/",
                    ".gradle/",
                    ".idea/",
                    "*.iml",
                    ".vscode/",
                    "node_modules/",
                    "*.tmp",
                    "*.temp"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun getAgentReadme(): String {
        return try {
            val readmePath = ProjectIO.root.resolve("Agent.md")
            if (readmePath.toFile().exists()) {
                ProjectIO.read(readmePath.toString())
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
