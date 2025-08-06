import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

fun main(args: Array<String>) {
    val directory = "onyx-ai"
    val location = File("/Volumes/onyx/books/gutenberg_books")
    File("/Volumes/onyx/books/formatted_books").mkdirs()
    location.listFiles()?.forEach {
        if (it.isFile && it.extension.contains("txt")) {
            val formatted = formatTextByParagraph(it.readText(), it.absolutePath)
            val outFile = File("/Volumes/onyx/books/formatted_books/${it.name}")
            outFile.writeText(formatted)
        }
    }
//    val file = File("$directory/src/test/resources/alice_full_source.txt")
//    val outFile = File("$directory/src/test/resources/alice_full_packed.txt")
//
//    try {
//        val value = formatTextByParagraph(inFile.readText())
//        outFile.writeText(value)
//        println("Processing complete. Output written to ${outFile.absolutePath}")
//    } catch (e: FileNotFoundException) {
//        println("Error: Input file not found at ${inFile.absolutePath}")
//    } catch (e: Exception) {
//        println("An error occurred: ${e.message}")
//    }
}

/**
 * Processes a single block of text.
 * Splits by punctuation OR newlines, joins with [SMT], and wraps with [SOT]/[EOT].
 * Optionally prepends [CLS] if provided.
 */
private fun processSingleBlock(
    block: String,
    maxStatementLength: Int = Int.MAX_VALUE,
    clsTag: String = ""
): String {
    val trimmedBlock = block.trim()
    if (trimmedBlock.isEmpty()) {
        return ""
    }

    // Improved regex: Handles abbreviations, splits on sentence ends or newlines.
    val sentenceDelimiters = Regex("(?<!\\w\\.\\w.)(?<![A-Z][a-z]\\.)(?<=\\.|\\?|\\!|\\n)\\s*")

    val statements = trimmedBlock
        .split(sentenceDelimiters)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { if (it.length > maxStatementLength) it.substring(0, maxStatementLength) + "..." else it }

    val clsPrefix = if (clsTag.isNotEmpty()) "$clsTag " else ""

    if (statements.isEmpty()) {
        return clsPrefix + "[SOT]$trimmedBlock[EOT]"
    }

    return clsPrefix + "[SOT]" + statements.joinToString("[SEP]") + "[EOT]"
}

/**
 * Formats a multi-paragraph document by applying SOT/SMT/EOT logic to each paragraph.
 * Paragraphs are separated by one or more blank lines.
 * Supports optional [CLS] prepending and future extra tokens.
 */
fun formatTextByParagraph(
    text: String? = null,
    filePath: String? = null,
    sotTag: String = "[SOT]",
    smtTag: String = "[SEP]",
    eotTag: String = "[EOT]",
    clsTag: String = "",  // New: Optional [CLS] tag to prepend
    padTag: String = "[PAD]",  // New: Optional [PAD] tag (not auto-applied; for manual use if needed)
    minParagraphLength: Int = 10,
    maxStatementLength: Int = Int.MAX_VALUE,
    groupDialogue: Boolean = true,
    maxDialogueLength: Int = 200
): String {
    val paragraphs = mutableListOf<String>()
    val currentParagraph = StringBuilder()

    val lines = text?.lines() ?: filePath?.let {
        Files.lines(Paths.get(it)).asSequence().toList()
    } ?: throw IllegalArgumentException("Either text or filePath must be provided")

    for (line in lines) {
        if (line.trim().isEmpty()) {
            if (currentParagraph.length >= minParagraphLength) {
                paragraphs.add(currentParagraph.toString())
            }
            currentParagraph.clear()
        } else {
            if (currentParagraph.isNotEmpty()) {
                currentParagraph.append("\n")
            }
            currentParagraph.append(line)
        }
    }
    if (currentParagraph.length >= minParagraphLength) {
        paragraphs.add(currentParagraph.toString())
    }

    var formattedParagraphs = paragraphs.map { processSingleBlock(it, maxStatementLength, clsTag) }

    // Group consecutive dialogue blocks
    if (groupDialogue) {
        val result = mutableListOf<String>()
        val currentDialog = mutableListOf<String>()
        for (fp in formattedParagraphs) {
            val effectiveFp = if (clsTag.isNotEmpty()) fp.substring(clsTag.length + 1) else fp  // Strip CLS for checking
            if (effectiveFp.startsWith("$sotTag “") || effectiveFp.startsWith("$sotTag \"")) {
                val statementStart = if (clsTag.isNotEmpty()) clsTag.length + 1 + "$sotTag ".length else "$sotTag ".length
                val statement = fp.substring(statementStart, fp.length - " $eotTag".length)
                if (statement.length < maxDialogueLength) {
                    currentDialog.add(statement)
                    continue
                }
            }
            if (currentDialog.isNotEmpty()) {
                val clsPrefix = if (clsTag.isNotEmpty()) "$clsTag " else ""
                result.add(clsPrefix + sotTag + currentDialog.joinToString(smtTag) + eotTag)
                currentDialog.clear()
            }
            result.add(fp)
        }
        if (currentDialog.isNotEmpty()) {
            val clsPrefix = if (clsTag.isNotEmpty()) "$clsTag " else ""
            result.add(clsPrefix + sotTag + currentDialog.joinToString(smtTag) + eotTag)
        }
        formattedParagraphs = result
    }

    // Optional: If padTag is set and you want manual padding (e.g., to a fixed length), implement here.
    // But recommended to handle padding in training loop.

    return formattedParagraphs
        .filter { it.isNotEmpty() }
        .map { it.replace("[SOT]", sotTag).replace("[SMT]", smtTag).replace("[EOT]", eotTag) }
        .joinToString("\n")
}

/**
 * Formats code by splitting into functions/classes based on language.
 * Joins lines with [EOL], wraps with [SOT_CODE]/[EOT_CODE].
 * Now supports JS/TS/Swift/Kotlin.
 */
fun formatCode(text: String, language: String): String {
    val codeBlockDelimiter = when (language.lowercase()) {
        "kotlin" -> Regex("^(fun|class|object|interface)\\s", RegexOption.MULTILINE)
        "javascript", "typescript" -> Regex("^(function|class|export\\s+(function|class))\\s", RegexOption.MULTILINE)
        "swift" -> Regex("^(func|class|struct|enum)\\s", RegexOption.MULTILINE)
        else -> throw IllegalArgumentException("Unsupported language: $language")
    }

    val codeBlocks = text
        .split(codeBlockDelimiter)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.lines().joinToString(" [EOL] ") }

    return codeBlocks.joinToString("\n") { "[SOT_CODE] $it [EOT_CODE]" }
}