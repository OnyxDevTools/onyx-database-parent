package com.onyx.cloud

import kotlin.test.*
import com.onyx.cloud.extensions.parseToJavaDate
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * Tests the `parseToJavaDate` extension across supported formats and edge cases.
 */
class DateTimeExtensionsTest {
    @Test
    fun parsesIsoInstant() {
        val s = "2024-11-23T22:57:47Z"
        val date = s.parseToJavaDate()
        assertNotNull(date)
        assertEquals(Instant.parse(s), date!!.toInstant())
    }

    @Test
    fun parsesLocalDateTimeAsUtc() {
        val s = "2024-11-23 22:57:47"
        val date = s.parseToJavaDate()
        val expected = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(ZoneId.of("UTC")).toInstant()
        assertNotNull(date)
        assertEquals(expected, date!!.toInstant())
    }

    @Test
    fun parsesDateOnlyToStartOfDayUtc() {
        val s = "2024-11-23"
        val date = s.parseToJavaDate()
        val expected = LocalDate.parse(s).atStartOfDay(ZoneId.of("UTC")).toInstant()
        assertNotNull(date)
        assertEquals(expected, date!!.toInstant())
    }

    @Test
    fun parsesIsoOffset() {
        val s = "2024-11-23T22:57:47+02:00"
        val date = s.parseToJavaDate()
        val expected = OffsetDateTime.parse(s).toInstant()
        assertNotNull(date)
        assertEquals(expected, date!!.toInstant())
    }

    @Test
    fun invalidFormatReturnsNull() {
        val date = "not-a-date".parseToJavaDate()
        assertNull(date)
    }
}

