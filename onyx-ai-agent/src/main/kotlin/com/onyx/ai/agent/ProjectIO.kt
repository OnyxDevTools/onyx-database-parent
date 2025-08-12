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
        set(value) {
            field = value
        }

    fun read(relPath: String): String = root.resolve(relPath).readText()

    fun write(relPath: String, content: String) {
        val target = root.resolve(relPath)
        Files.createDirectories(target.parent)
        target.writeText(content)
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
