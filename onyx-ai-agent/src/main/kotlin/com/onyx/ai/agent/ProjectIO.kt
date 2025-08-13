package com.onyx.ai.agent

import java.nio.file.*
import kotlin.io.path.readText
import kotlin.io.path.writeText
import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
import com.github.difflib.UnifiedDiffUtils        // <-- NEW

object ProjectIO {
    /** Root of the project you want the agent to touch. */
    var root: Path = Paths.get("").toAbsolutePath()

    fun read(relPath: String): String = root.resolve(relPath).readText()

    /** Read specific lines from a file (1-based line numbers) */
    fun read(relPath: String, lineStart: Int?, lineEnd: Int?): String {
        if (lineStart == null && lineEnd == null) {
            return read(relPath)
        }

        val file = root.resolve(relPath)
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return "File is not found or is not a regular file: $relPath"
        }
        
        val allLines = root.resolve(relPath).readText().lines()
        val start = (lineStart ?: 1) - 1  // Convert to 0-based
        val end = (lineEnd ?: allLines.size) - 1  // Convert to 0-based
        
        return allLines.drop(start).take(end - start + 1).joinToString("\n")
    }

    fun write(relPath: String, content: String) {
        val target = root.resolve(relPath)
        Files.createDirectories(target.parent)
        target.writeText(content)
    }

    /** Write content to specific lines in a file (1-based line numbers) */
    fun write(relPath: String, content: String, lineStart: Int?, lineEnd: Int?) {
        val target = root.resolve(relPath)
        Files.createDirectories(target.parent)
        
        if (lineStart == null && lineEnd == null) {
            target.writeText(content)
            return
        }
        
        // For line-based writing, we need to handle partial file updates
        val existingLines = if (Files.exists(target)) {
            target.readText().lines().toMutableList()
        } else {
            mutableListOf()
        }
        
        val start = (lineStart ?: 1) - 1  // Convert to 0-based
        val end = (lineEnd ?: existingLines.size) - 1  // Convert to 0-based
        val newContentLines = content.lines()
        
        // Ensure we have enough lines
        while (existingLines.size <= end) {
            existingLines.add("")
        }
        
        // Replace the specified line range
        for (i in 0 until (end - start + 1)) {
            if (i < newContentLines.size) {
                existingLines[start + i] = newContentLines[i]
            }
        }
        
        target.writeText(existingLines.joinToString("\n"))
    }

    fun delete(relPath: String) = Files.deleteIfExists(root.resolve(relPath))

    /** Returns a unified diff – useful for logging what changed. */
    fun diff(old: String, new: String): String {
        val oldLines = old.lines()
        val newLines = new.lines()
        val patch: Patch<String> = DiffUtils.diff(oldLines, newLines)
        return UnifiedDiffUtils.generateUnifiedDiff(
            "a/${old.hashCode()}",
            "b/${new.hashCode()}",
            oldLines,
            patch,
            3
        ).joinToString("\n")
    }
}
