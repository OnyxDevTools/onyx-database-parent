package com.onyx.cloud.extensions

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Represents a single cascade relationship instruction parsed from a cascade query parameter.
 *
 * @param attribute Name of the attribute on the source entity.
 * @param type Relationship graph type (graphType).
 * @param targetField Field on the target entity.
 * @param sourceField Field on the source entity.
 */
data class CascadeInstruction(
    val attribute: String,
    val type: String,
    val targetField: String,
    val sourceField: String
)

/**
 * Parses a comma-separated cascade instruction string into a list of [CascadeInstruction].
 *
 * Each entry must be URI-encoded and follow the pattern:
 * ```
 * attribute:GraphType(targetField,sourceField)
 * ```
 *
 * @receiver The raw cascade query parameter value.
 * @return A list of parsed [CascadeInstruction].
 * @throws IllegalArgumentException if the format of any entry is invalid.
 */
fun String.toCascadeInstructions(): List<CascadeInstruction> {
    val mappings = mutableListOf<CascadeInstruction>()
    val entries = this.split(",")

    for (entry in entries) {
        val decoded = URLDecoder.decode(entry, StandardCharsets.UTF_8.name())
        val parts = decoded.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid cascade mapping format: $entry")
        }
        val attribute = parts[0].trim()
        val typeAndArgs = parts[1].trim()

        val matchResult = Regex("(\\w+)\\((.+?),\\s*(.+?)\\)").find(typeAndArgs)
        if (matchResult != null) {
            val (type, target, source) = matchResult.destructured
            mappings.add(CascadeInstruction(attribute, type, target, source))
        } else {
            throw IllegalArgumentException("Invalid cascade mapping format: $decoded")
        }
    }

    return mappings
}
