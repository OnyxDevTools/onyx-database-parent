package com.onyx.diskmap.data

import com.onyx.exception.BufferingException
import java.nio.ByteBuffer

/** Writes an unsigned 40-bit file position in little-endian order. */
fun ByteBuffer.putBigInt(value: Long) {
    require(value in 0..MAX_UNSIGNED_40_BIT) {
        "Value $value does not fit in an unsigned 40-bit integer"
    }
    put(value.toByte())
    put((value ushr 8).toByte())
    put((value ushr 16).toByte())
    put((value ushr 24).toByte())
    put((value ushr 32).toByte())
}

/** Reads an unsigned 40-bit file position in little-endian order. */
val ByteBuffer.bigInt: Long
    @Throws(BufferingException::class)
    get() = ((get().toLong() and 0xff)
        or ((get().toLong() and 0xff) shl 8)
        or ((get().toLong() and 0xff) shl 16)
        or ((get().toLong() and 0xff) shl 24)
        or ((get().toLong() and 0xff) shl 32))

/** Writes an unsigned 48-bit file position in little-endian order. */
fun ByteBuffer.putUnsignedLong48(value: Long) {
    require(value in 0..MAX_UNSIGNED_48_BIT) {
        "Value $value does not fit in an unsigned 48-bit integer"
    }
    put(value.toByte())
    put((value ushr 8).toByte())
    put((value ushr 16).toByte())
    put((value ushr 24).toByte())
    put((value ushr 32).toByte())
    put((value ushr 40).toByte())
}

/** Reads an unsigned 48-bit file position in little-endian order. */
val ByteBuffer.unsignedLong48: Long
    @Throws(BufferingException::class)
    get() = ((get().toLong() and 0xff)
        or ((get().toLong() and 0xff) shl 8)
        or ((get().toLong() and 0xff) shl 16)
        or ((get().toLong() and 0xff) shl 24)
        or ((get().toLong() and 0xff) shl 32)
        or ((get().toLong() and 0xff) shl 40))

const val MAX_UNSIGNED_40_BIT: Long = (1L shl 40) - 1L
const val MAX_UNSIGNED_48_BIT: Long = (1L shl 48) - 1L
