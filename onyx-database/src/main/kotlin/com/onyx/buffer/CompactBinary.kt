package com.onyx.buffer

import java.nio.ByteBuffer

/**
 * Allocation-free variable-width integer encoding used by BufferStream's compact wire format.
 * Signed values use ZigZag encoding so small negative and positive numbers both remain small.
 */
internal object CompactBinary {

    fun putUnsignedInt(buffer: ByteBuffer, value: Int) {
        var remaining = value
        while (remaining and -0x80 != 0) {
            buffer.put(((remaining and 0x7f) or 0x80).toByte())
            remaining = remaining ushr 7
        }
        buffer.put(remaining.toByte())
    }

    fun getUnsignedInt(buffer: ByteBuffer): Int {
        var result = 0
        var shift = 0
        for (index in 0 until 5) {
            val next = buffer.get().toInt() and 0xff
            if (index == 4 && next and 0xf0 != 0) {
                throw IllegalArgumentException("Malformed unsigned variable-length Int")
            }
            result = result or ((next and 0x7f) shl shift)
            if (next and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Malformed unsigned variable-length Int")
    }

    fun putSignedInt(buffer: ByteBuffer, value: Int) {
        putUnsignedInt(buffer, (value shl 1) xor (value shr 31))
    }

    fun getSignedInt(buffer: ByteBuffer): Int {
        val value = getUnsignedInt(buffer)
        return (value ushr 1) xor -(value and 1)
    }

    fun putUnsignedLong(buffer: ByteBuffer, value: Long) {
        var remaining = value
        while (remaining and -0x80L != 0L) {
            buffer.put(((remaining and 0x7fL) or 0x80L).toByte())
            remaining = remaining ushr 7
        }
        buffer.put(remaining.toByte())
    }

    fun getUnsignedLong(buffer: ByteBuffer): Long {
        var result = 0L
        var shift = 0
        for (index in 0 until 10) {
            val next = buffer.get().toInt() and 0xff
            if (index == 9 && next and 0xfe != 0) {
                throw IllegalArgumentException("Malformed unsigned variable-length Long")
            }
            result = result or ((next.toLong() and 0x7fL) shl shift)
            if (next and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Malformed unsigned variable-length Long")
    }

    fun putSignedLong(buffer: ByteBuffer, value: Long) {
        putUnsignedLong(buffer, (value shl 1) xor (value shr 63))
    }

    fun getSignedLong(buffer: ByteBuffer): Long {
        val value = getUnsignedLong(buffer)
        return (value ushr 1) xor -(value and 1L)
    }
}
