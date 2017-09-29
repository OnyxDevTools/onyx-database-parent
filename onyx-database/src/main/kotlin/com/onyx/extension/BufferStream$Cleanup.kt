package com.onyx.extension

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import java.nio.ByteBuffer

fun <T> withBuffer(buffer: ByteBuffer, body: (buffer:ByteBuffer) -> T) = try {
    body.invoke(buffer)
} finally {
    BufferPool.recycle(buffer)
}

fun <T> BufferStream?.perform(body: (stream: BufferStream?) -> T) = try {
    body.invoke(this)
} finally {
    this?.recycle()
}
