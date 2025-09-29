package com.onyx.cloud.extensions

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

/**
 * A collection of predefined `DateTimeFormatter` instances used for parsing various date/time string formats.
 * Includes common ISO formats and custom patterns.
 */
private val DATE_TIME_FORMATTERS: List<DateTimeFormatter> = listOf(
    // Custom format: MM/dd/yyyy hh:mm:ss a z (e.g., 11/23/2024 10:57:47 PM PST) - Note: Time zone names can be ambiguous.
    DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a z", Locale.US),

    // ISO 8601 formats (preferred for interoperability)
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,   // e.g., 2024-11-23T22:57:47.715Z or 2024-11-23T15:57:47.715-07:00
    DateTimeFormatter.ISO_ZONED_DATE_TIME,  // Similar to ISO_OFFSET_DATE_TIME, handles zone IDs like [UTC]
    DateTimeFormatter.ISO_INSTANT,          // Specific format for UTC instants e.g., 2024-11-23T22:57:47.715Z

    // Other common formats
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US), // e.g., 2024-11-23 22:57:47 (assumes default/system time zone if parsed to LocalDateTime)
    DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.US),  // e.g., 23 Nov 2024 22:57 (assumes default/system time zone if parsed to LocalDateTime)
    DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),           // e.g., 11/23/2024 (parses as LocalDate)
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)            // e.g., 2024-11-23 (parses as LocalDate)
)

/**
 * Parses the receiving string into a `java.util.Date` object.
 *
 * It attempts to parse the string using a predefined list of common date/time formats,
 * including ISO 8601 standards and other patterns.
 *
 * - If the format includes time zone information (offset or ID), it's preserved.
 * - If the format only includes local date/time, it's interpreted as being in the UTC time zone.
 * - If the format only includes a date, it's interpreted as the start of that day (midnight) in UTC.
 *
 * @receiver The date/time string to parse.
 * @return A `java.util.Date` representing the parsed instant, or `null` if the string
 * cannot be parsed using any of the supported formats.
 */
fun String.parseToJavaDate(): Date? {
    // Fast paths for ISO inputs (covers your server "…Z" and offset forms)
    runCatching { return Date.from(Instant.parse(this)) }.getOrNull()?.let { return it }
    runCatching { return Date.from(OffsetDateTime.parse(this).toInstant()) }.getOrNull()?.let { return it }
    runCatching { return Date.from(ZonedDateTime.parse(this).toInstant()) }.getOrNull()?.let { return it }

    // Fallbacks using your formatter list (unchanged)
    for (formatter in DATE_TIME_FORMATTERS) {
        try {
            val zdt = ZonedDateTime.parse(this, formatter)
            return Date.from(zdt.toInstant())
        } catch (_: DateTimeParseException) {}

        try {
            val ldt = LocalDateTime.parse(this, formatter)
            // interpret naive local date-times as UTC (your chosen convention)
            return Date.from(ldt.atZone(ZoneId.of("UTC")).toInstant())
        } catch (_: DateTimeParseException) {}

        try {
            val ld = LocalDate.parse(this, formatter)
            return Date.from(ld.atStartOfDay(ZoneId.of("UTC")).toInstant())
        } catch (_: DateTimeParseException) {}
        }
    return null
}
