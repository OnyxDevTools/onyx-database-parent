package com.onyx.network

import javax.net.ssl.SSLEngineResult
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ByteChannel
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngine
import java.nio.channels.SocketChannel
import javax.net.ssl.SSLContext

/**
 * This class wraps a socket channel and applies a ssl engine to wrap the transport data using SSL
 *
 * @since 2.0.0 Refactored so that the SSL logic is out of the networking classes
 */
class SSLSocketChannel(private val channel: SocketChannel, sslContext: SSLContext, useClientMode:Boolean = true) : ByteChannel {

    private var sslEngine: SSLEngine? = null
    private var cipherOut: ByteBuffer? = null
    private var cipherIn: ByteBuffer? = null
    private var plainIn: ByteBuffer? = null
    private var plainOut: ByteBuffer? = null

    init {
        sslEngine = sslContext.createSSLEngine()
        sslEngine!!.useClientMode = useClientMode
        createBuffers()
        doHandshake()
    }

    /**
     * Override is open.  Just determines if the channel is open
     */
    override fun isOpen(): Boolean = channel.isOpen

    /**
     * Perform handshake.  I recommend looking up how ssl engine wrapping and unwrapping / handshake works cause, its a mess.
     * Security through obfuscation!!
     *
     */
    private fun doHandshake() {
        sslEngine!!.beginHandshake()
        var handshakeStatus = sslEngine!!.handshakeStatus
        while (handshakeStatus != HandshakeStatus.FINISHED && handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {
            when (handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_TASK ->
                    handshakeStatus = runDelegatedTasks()
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    handshakeStatus = unwrap(null)
                    plainIn!!.clear()
                }
                SSLEngineResult.HandshakeStatus.NEED_WRAP ->
                    handshakeStatus = wrap(plainOut)
                else -> { }
            }
        }

        plainIn!!.clear()
        plainOut!!.clear()
    }

    /**
     * Run handshake tasks
     */
    private fun runDelegatedTasks(): HandshakeStatus {
        var runnable: Runnable?
        do {
            runnable = sslEngine!!.delegatedTask
            runnable?.run()
        } while (runnable != null)
        return sslEngine!!.handshakeStatus
    }

    /**
     * Unwrap data from a channel and put into buffer
     */
    private fun unwrap(buffer: ByteBuffer?): HandshakeStatus {
        var handshakeStatus = sslEngine!!.handshakeStatus

        if (channel.read(cipherIn) < 0) {
            throw IOException("Failed to establish SSL socket connection.")
        }
        cipherIn!!.flip()

        var status: SSLEngineResult.Status?
        do {
            status = sslEngine!!.unwrap(cipherIn, plainIn).status
            when (status) {
                SSLEngineResult.Status.OK -> {
                    plainIn!!.flip()
                    copyBuffer(plainIn, buffer)
                    plainIn!!.compact()
                    handshakeStatus = runDelegatedTasks()
                }
                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                    plainIn!!.flip()
                    val appSize = sslEngine!!.session.applicationBufferSize
                    val newAppSize = appSize + plainIn!!.remaining()
                    if (newAppSize > appSize * 2)
                        throw IOException("Failed to enlarge application input buffer ")
                    val newPlainIn = ByteBuffer.allocateDirect(newAppSize).order(ByteOrder.BIG_ENDIAN)
                    newPlainIn.put(plainIn)
                    plainIn = newPlainIn
                }
                SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                    val curNetSize = cipherIn!!.capacity()
                    val netSize = sslEngine!!.session.packetBufferSize
                    if (netSize > curNetSize) {
                        val newCipherIn = ByteBuffer.allocateDirect(netSize).order(ByteOrder.BIG_ENDIAN)
                        newCipherIn.put(cipherIn)
                        cipherIn = newCipherIn
                    } else {
                        cipherIn!!.compact()
                    }
                    return handshakeStatus
                }
                else -> throw IOException("Unexpected status $status")
            }
        } while (cipherIn!!.hasRemaining())

        cipherIn!!.compact()
        return handshakeStatus
    }

    /**
     * Wrap byte buffer and write it to a channel
     */
    private fun wrap(buffer: ByteBuffer?): HandshakeStatus {
        var handshakeStatus = sslEngine!!.handshakeStatus
        val status = sslEngine!!.wrap(buffer, cipherOut).status
        when (status) {
            SSLEngineResult.Status.OK -> {
                handshakeStatus = runDelegatedTasks()
                cipherOut!!.flip()
                channel.write(cipherOut)
                cipherOut!!.clear()
            }
            SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                val curNetSize = cipherOut!!.capacity()
                val netSize = sslEngine!!.session.packetBufferSize
                if (curNetSize >= netSize || buffer!!.capacity() > netSize) {
                    throw IOException("Failed to enlarge network buffer")
                }

                cipherOut = ByteBuffer.allocateDirect(netSize).order(ByteOrder.BIG_ENDIAN)
            }
            else -> throw IOException("Unexpected status $status")
        }
        return handshakeStatus
    }

    /**
     * Initial allocation of a buffer
     */
    private fun createBuffers() {
        val session = sslEngine!!.session
        val appBufferSize = session.applicationBufferSize
        val netBufferSize = session.packetBufferSize

        plainOut = ByteBuffer.allocateDirect(appBufferSize).order(ByteOrder.BIG_ENDIAN)
        plainIn = ByteBuffer.allocateDirect(appBufferSize).order(ByteOrder.BIG_ENDIAN)
        cipherOut = ByteBuffer.allocateDirect(netBufferSize).order(ByteOrder.BIG_ENDIAN)
        cipherIn = ByteBuffer.allocateDirect(netBufferSize).order(ByteOrder.BIG_ENDIAN)
    }

    /**
     * Read from a channel and Unwrap SSL data
     */
    override fun read(dst: ByteBuffer): Int {
        val toRead = dst.remaining()
        plainIn!!.flip()
        if (plainIn!!.remaining() >= toRead) {
            copyBuffer(plainIn, dst)
            plainIn!!.compact()
        } else {
            dst.put(plainIn)
            do {
                plainIn!!.clear()
                unwrap(dst)
            } while (dst.remaining() > 0)
        }

        return toRead
    }

    /**
     * SSL Wrap a buffer and write it to socket channel
     */
    override fun write(src: ByteBuffer): Int {
        val toWrite = src.remaining()
        while (src.remaining() > 0) {
            wrap(src)
        }
        return toWrite
    }

    /**
     * Close channel
     */
    override fun close() {
        plainOut!!.clear()
        sslEngine!!.closeOutbound()

        while (!sslEngine!!.isOutboundDone) {
            sslEngine!!.wrap(plainOut, cipherOut)

            while (cipherOut!!.hasRemaining()) {
                val num = channel.write(cipherOut)
                if (num == -1) {
                    break
                }
            }
        }
        channel.close()
    }

    companion object {

        /**
         * Helper method to copy a buffer
         */
        internal fun copyBuffer(from: ByteBuffer?, to: ByteBuffer?): Int {
            if (from == null || to == null)
                return 0

            var i = 0
            while (to.remaining() > 0 && from.remaining() > 0) {
                to.put(from.get())
                i++
            }
            return i
        }
    }
}