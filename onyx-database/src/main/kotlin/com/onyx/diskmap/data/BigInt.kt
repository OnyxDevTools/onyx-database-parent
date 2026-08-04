package com.onyx.diskmap.data

import com.onyx.exception.BufferingException
import java.nio.ByteBuffer

/** Writes an unsigned 40-bit file position in little-endian order. */
fun ByteBuffer.putBigInt(value: Long) {
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
