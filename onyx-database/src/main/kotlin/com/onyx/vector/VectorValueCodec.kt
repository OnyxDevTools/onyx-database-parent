package com.onyx.vector

import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class IntervalCoordinate(
    val coordinate: BigInteger,
    val bits: Int,
    val domain: String,
    /** Whether multiple ordered source values can intentionally share this coordinate. */
    val lossy: Boolean = false
)

/** Stable conversions shared by entity ingestion and predicate planning. */
object VectorValueCodec {
    private val TWO_64 = BigInteger.ONE.shiftLeft(64)
    private val MASK_64 = TWO_64.subtract(BigInteger.ONE)

    private const val STRING_PREFIX_UNITS = 3
    private const val STRING_UNIT_BITS = 17
    private const val STRING_COORDINATE_BITS = STRING_PREFIX_UNITS * STRING_UNIT_BITS

    fun intervalCoordinate(value: Any): IntervalCoordinate? {
        if (value is Float && !value.isFinite() || value is Double && !value.isFinite()) {
            // IEEE NaN and infinities do not have useful interval semantics. They remain
            // searchable through the categorical feature emitted by VectorEntityEncoder.
            return null
        }
        return when (value) {
            is Byte -> IntervalCoordinate(BigInteger.valueOf((value.toInt() - Byte.MIN_VALUE).toLong()), 8, "i8")
            is Short -> IntervalCoordinate(BigInteger.valueOf((value.toInt() - Short.MIN_VALUE).toLong()), 16, "i16")
            is Int -> IntervalCoordinate(BigInteger.valueOf(value.toLong() - Int.MIN_VALUE.toLong()), 32, "i32")
            is Long -> IntervalCoordinate(signedLongCoordinate(value), 64, "i64")
            is Float -> if (value.isNaN()) null else IntervalCoordinate(floatCoordinate(value), 32, "f32")
            is Double -> if (value.isNaN()) null else IntervalCoordinate(doubleCoordinate(value), 64, "f64")
            is Date -> IntervalCoordinate(signedLongCoordinate(value.time), 64, "date:MILLISECONDS")
            is Char -> IntervalCoordinate(BigInteger.valueOf(value.code.toLong()), 16, "char16")
            is Boolean -> IntervalCoordinate(if (value) BigInteger.ONE else BigInteger.ZERO, 1, "bool")
            is Enum<*> -> enumCoordinate(value)
            is String -> IntervalCoordinate(
                coordinate = stringPrefixCoordinate(value),
                bits = STRING_COORDINATE_BITS,
                domain = "string:utf16-prefix$STRING_PREFIX_UNITS",
                lossy = true
            )
            else -> null
        }
    }

    fun categorical(value: Any): String = when (value) {
        is Enum<*> -> value.name
        is Date -> isoDate(value)
        else -> value.toString()
    }

    /** Text used by whole-record search. Date keeps its legacy human-readable year tokens. */
    fun text(value: Any): String = when (value) {
        is Date -> "${isoDate(value)} ${value}"
        else -> value.toString()
    }

    /** Stable text used by per-attribute predicate routing and exact predicate evaluation. */
    fun predicateText(value: Any): String = when (value) {
        is Date -> isoDate(value)
        else -> value.toString()
    }

    fun signedLongCoordinate(value: Long): BigInteger =
        unsignedLong(value xor Long.MIN_VALUE)

    fun floatCoordinate(value: Float): BigInteger {
        val raw = value.toRawBits()
        val transformed = if (raw < 0) raw.inv() else raw xor Int.MIN_VALUE
        return BigInteger.valueOf(transformed.toLong() and 0xffff_ffffL)
    }

    fun doubleCoordinate(value: Double): BigInteger {
        val raw = value.toRawBits()
        val unsigned = unsignedLong(raw)
        return if (raw < 0) MASK_64.xor(unsigned) else unsigned.setBit(63)
    }

    fun unsignedLong(value: Long): BigInteger =
        BigInteger.valueOf(value and Long.MAX_VALUE).let { if (value < 0) it.setBit(63) else it }

    private fun enumCoordinate(value: Enum<*>): IntervalCoordinate {
        val enumClass = value.declaringJavaClass
        val constantCount = requireNotNull(enumClass.enumConstants).size
        val bits = (Int.SIZE_BITS - Integer.numberOfLeadingZeros((constantCount - 1).coerceAtLeast(1)))
            .coerceAtLeast(1)
        return IntervalCoordinate(
            coordinate = BigInteger.valueOf(value.ordinal.toLong()),
            bits = bits,
            domain = "enum:${enumClass.name}"
        )
    }

    /**
     * Monotonic prefix of Java's raw, case-sensitive UTF-16 ordering.
     *
     * Zero represents end-of-string and every UTF-16 code unit is shifted by one. Values that
     * differ after the retained prefix deliberately collide, so range planning must retain a
     * strict bound's mapped coordinate and let exact predicate verification remove extras.
     */
    private fun stringPrefixCoordinate(value: String): BigInteger {
        var coordinate = BigInteger.ZERO
        repeat(STRING_PREFIX_UNITS) { index ->
            val digit = if (index < value.length) value[index].code + 1 else 0
            coordinate = coordinate.shiftLeft(STRING_UNIT_BITS).or(BigInteger.valueOf(digit.toLong()))
        }
        return coordinate
    }

    private fun isoDate(value: Date): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(value)
}
