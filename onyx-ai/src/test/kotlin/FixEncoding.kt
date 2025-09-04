import java.nio.charset.Charset
import java.nio.file.*
import kotlin.io.path.*

fun fixMojibake(s: String): String {
    val candidates = listOf(
            s,
            String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8),
            String(s.toByteArray(Charset.forName("windows-1252")), Charsets.UTF_8),
            String(s.toByteArray(Charset.forName("MacRoman")), Charsets.UTF_8),
            )
    // Prefer the version with fewer obvious mojibake markers
    fun score(t: String) = listOf("â€", "â€™", "Ã", "Â", "‚Ä", "�").sumOf { t.count { ch -> t.contains(it) } }
    return candidates.minByOrNull(::score) ?: s
}

fun main() {
    val root = Paths.get("/Volumes/onyx/books/training_data")
    Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() && it.toString().endsWith(".txt") }.forEach { p ->
            val raw = p.readText()
        val fixed = fixMojibake(raw)
        if (fixed != raw) {
            val out = p.resolveSibling(p.nameWithoutExtension + ".fixed.txt")
            out.writeText(fixed)
            println("fixed: $p -> $out")
        }
    }
    }
}
