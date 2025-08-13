package com.onyx.ai.agent

import java.nio.file.*
import kotlin.io.path.readText
import kotlin.io.path.writeText
import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
import com.github.difflib.UnifiedDiffUtils

object ProjectIO {
    /** Root of the project you want the agent to touch. */
    var root: Path = Paths.get("").toAbsolutePath()

    /** Read an existing file in its entirety. */
    fun read(relPath: String): String = root.resolve(relPath).readText()

    /**
     * DEPRECATED: Range-based reading is disabled.
     * For drop-in safety, this will ignore ranges and return the whole file.
     */
    @Deprecated(
        message = "Range-based reads are disabled. Use whole-file read instead.",
        replaceWith = ReplaceWith("read(relPath)")
    )
    fun read(relPath: String, lineStart: Int?, lineEnd: Int?): String {
        // Intentionally ignore line ranges to enforce whole-file semantics.
        return read(relPath)
    }

    /** Write/replace a file in its entirety (create if missing). Atomic when possible. */
    fun write(relPath: String, content: String) {
        val target = root.resolve(relPath)
        Files.createDirectories(target.parent)

        // Write atomically when supported by the filesystem.
        val tmp = Files.createTempFile(target.parent, ".tmp-", ".write")
        tmp.writeText(content)
        try {
            Files.move(
                tmp, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // Fallback to non-atomic replace if ATOMIC_MOVE not supported.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * DEPRECATED: Range-based writes are disabled.
     * For drop-in safety, ignores ranges and performs a whole-file write.
     */
    @Deprecated(
        message = "Range-based writes are disabled. Use whole-file write instead.",
        replaceWith = ReplaceWith("write(relPath, content)")
    )
    fun write(relPath: String, content: String, lineStart: Int?, lineEnd: Int?) {
        // Intentionally ignore line ranges to enforce whole-file semantics.
        write(relPath, content)
    }

    /** Delete a file if it exists. */
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
