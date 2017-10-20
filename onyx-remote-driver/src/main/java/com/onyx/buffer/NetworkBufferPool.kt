package com.onyx.buffer

import java.nio.ByteBuffer
import java.util.*
import kotlin.NoSuchElementException

object NetworkBufferPool {

    private val numberOfBuffers = 100
    private val bufferPool = LinkedList<ByteBuffer>()
    private var isInitialized = false
    var bufferSize = 0

    fun init(bufferSize:Int) {
        if(!isInitialized) {
            this.bufferSize = bufferSize
            bufferPool.clear()
            for (i in 0..numberOfBuffers) bufferPool.add(ByteBuffer.allocateDirect(bufferSize))
        }
        isInitialized = true
    }

    @Synchronized
    fun allocate():ByteBuffer = try { bufferPool.removeFirst() } catch (e:NoSuchElementException) {
        println("Creating New Buffer")
        ByteBuffer.allocateDirect(bufferSize)
    }

    @Synchronized
    fun recycle(buffer: ByteBuffer) {
        buffer.clear()
        bufferPool.addLast(buffer)
    }

    fun <T> withBuffer(buffer: ByteBuffer, body:(ByteBuffer) -> T):T {
        try {
            return body.invoke(buffer)
        } finally {
            recycle(buffer)
        }
    }

}
