package com.onyx.cloud.extensions

import java.net.URLDecoder

/**
 * Represents a single cascade relationship instruction parsed from a cascade query parameter.
 *
 * @property attribute name of the attribute on the source entity.
 * @property type relationship graph type (graphType).
 * @property targetField field on the target entity.
 * @property sourceField field on the source entity.
 */
data class CascadeInstruction(
    val attribute: String,
    val type: String,
    val targetField: String,
    val sourceField: String
)

/**
 * Parses a comma-separated cascade instruction string into a list of [CascadeInstruction] instances.
 *
 * Each entry must be URI-encoded and follow the pattern:
 *
 * ```
 * attribute:GraphType(targetField,sourceField)
 * ```
 *
 * @receiver the raw cascade query parameter value.
 * @return a list of parsed instructions; empty when the string is blank.
 * @throws IllegalArgumentException if the format of any entry is invalid or parentheses are unbalanced.
 */
fun String.toCascadeInstructions(): List<CascadeInstruction> {
    val input = this.trim()
    if (input.isEmpty()) return emptyList()

    val entries = splitTopLevelByComma(input)
    val mappings = ArrayList<CascadeInstruction>(entries.size)

    // type(arg1, arg2) with flexible spacing
    val typeSig = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*,\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)$""")

    for (raw in entries) {
        val entry = raw.decodeURI().trim()
        if (entry.isEmpty()) continue

        val colon = entry.indexOf(':')
        require(colon > 0 && colon < entry.lastIndex) { "Invalid cascade mapping format: $raw" }

        val attribute = entry.substring(0, colon).trim()
        val typeAndArgs = entry.substring(colon + 1).trim()

        val match = typeSig.matchEntire(typeAndArgs)
            ?: throw IllegalArgumentException("Invalid type/args segment: $raw")

        val (type, target, source) = match.destructured
        mappings.add(CascadeInstruction(attribute, type, target, source))
    }

    return mappings
}

/**
 * Splits the supplied string by commas, ignoring commas enclosed in parentheses.
 *
 * @param s the string to split.
 * @return a list of segments extracted at the top level.
 * @throws IllegalArgumentException when parentheses are unbalanced.
 */
private fun splitTopLevelByComma(s: String): List<String> {
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var depth = 0
    for (ch in s) {
        when (ch) {
            '(' -> {
                depth++
                buf.append(ch)
            }
            ')' -> {
                depth--
                if (depth < 0) throw IllegalArgumentException("Unbalanced parentheses in: $s")
                buf.append(ch)
            }
            ',' -> if (depth == 0) {
                val piece = buf.toString().trim()
                if (piece.isNotEmpty()) out.add(piece)
                buf.setLength(0)
            } else {
                buf.append(ch)
            }
            else -> buf.append(ch)
        }
    }
    val last = buf.toString().trim()
    if (last.isNotEmpty()) out.add(last)
    if (depth != 0) throw IllegalArgumentException("Unbalanced parentheses in: $s")
    return out
}

/**
 * Decodes the receiving URI-encoded string using UTF-8.
 *
 * @receiver the encoded string value.
 * @return the decoded text.
 */
fun String.decodeURI(): String = URLDecoder.decode(this, "UTF-8")
