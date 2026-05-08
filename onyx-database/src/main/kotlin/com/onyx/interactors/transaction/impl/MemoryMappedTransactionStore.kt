package com.onyx.interactors.transaction.impl

import com.onyx.buffer.copy
import com.onyx.diskmap.store.impl.FileChannelStore
import com.onyx.diskmap.store.impl.MappedFileSegment
import com.onyx.diskmap.store.impl.MappedFileSegmentFactory
import com.onyx.diskmap.store.impl.MappedFileSegmentKey
import com.onyx.diskmap.store.impl.MemoryMappedStore
import com.onyx.exception.TransactionException
import com.onyx.extension.common.Block
import com.onyx.extension.common.catchAll
import com.onyx.extension.common.openFileChannel
import com.onyx.interactors.transaction.TransactionStore
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.Arrays
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Transaction WAL store that appends through the shared memory-mapped segment cache used by [MemoryMappedStore].
 */
open class MemoryMappedTransactionStore(val location: String) : TransactionStore {

    private val journalFileIndex = AtomicLong(0L)
    private var lastWalFileChannel: FileChannel? = null
    private val transactionFileLock = Block()

    private val walDirectory: String
        get() = this.location + File.separator + "wal" + File.separator

    @Throws(TransactionException::class)
    override fun getTransactionFile(): FileChannel = synchronized(transactionFileLock) {
        try {
            if (lastWalFileChannel == null) {
                val directory = walDirectory
                val journalingDirector = File(directory)
                if (!journalingDirector.exists()) {
                    journalingDirector.mkdirs()
                }

                val directoryListing = File(directory).list() ?: emptyArray()
                Arrays.sort(directoryListing)

                if (directoryListing.isNotEmpty()) {
                    var fileName = directoryListing[directoryListing.size - 1]
                    fileName = fileName.replace(".wal", "")

                    journalFileIndex.addAndGet(Integer.valueOf(fileName).toLong())
                }

                lastWalFileChannel = openMappedWalFile(directory + journalFileIndex.get() + ".wal")
            }

            if (lastWalFileChannel!!.size() > MAX_JOURNAL_SIZE) {
                lastWalFileChannel!!.force(true)
                lastWalFileChannel!!.close()

                val directory = walDirectory
                lastWalFileChannel = openMappedWalFile(directory + journalFileIndex.addAndGet(1) + ".wal")
            }

            return lastWalFileChannel!!
        } catch (e: IOException) {
            throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_OPEN_FILE)
        }
    }

    override fun close() {
        if (lastWalFileChannel != null) {
            catchAll {
                lastWalFileChannel!!.force(true)
                lastWalFileChannel!!.close()
            }
        }
    }

    private fun openMappedWalFile(filePath: String): FileChannel {
        val file = File(filePath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }

        val channel = file.path.openFileChannel()
            ?: throw IOException("Unable to open WAL file channel")

        return MemoryMappedTransactionFileChannel(channel)
    }

    companion object {
        private const val MAX_JOURNAL_SIZE = 1024 * 1024 * 20
    }
}

private class MemoryMappedTransactionFileChannel(
    private val channel: FileChannel
) : FileChannel() {

    private val lock = Any()
    private val fileId = MemoryMappedStore.nextMappedFileId()
    private val bufferSliceSize = if (FileChannelStore.isSmallDevice) {
        FileChannelStore.SMALL_FILE_SLICE_SIZE
    } else {
        FileChannelStore.LARGE_FILE_SLICE_SIZE
    }

    private var currentPosition = channel.position()
    private var logicalSize = channel.size()

    override fun read(dst: ByteBuffer): Int = synchronized(lock) {
        val bytesRead = read(dst, currentPosition)
        if (bytesRead > 0) {
            currentPosition += bytesRead
        }
        bytesRead
    }

    override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long = synchronized(lock) {
        validateArrayRange(dsts.size, offset, length)
        var total = 0L
        for (index in offset until offset + length) {
            val bytesRead = read(dsts[index])
            if (bytesRead < 0) {
                return@synchronized if (total == 0L) -1L else total
            }
            total += bytesRead
            if (bytesRead == 0) {
                return@synchronized total
            }
        }
        total
    }

    override fun write(src: ByteBuffer): Int = synchronized(lock) {
        val written = writeMapped(src, currentPosition)
        currentPosition += written
        written
    }

    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long = synchronized(lock) {
        validateArrayRange(srcs.size, offset, length)
        var total = 0L
        for (index in offset until offset + length) {
            total += write(srcs[index]).toLong()
        }
        total
    }

    override fun position(): Long = synchronized(lock) {
        ensureOpen()
        currentPosition
    }

    override fun position(newPosition: Long): FileChannel = synchronized(lock) {
        ensureOpen()
        require(newPosition >= 0) { "Position must not be negative" }
        currentPosition = newPosition
        this
    }

    override fun size(): Long = synchronized(lock) {
        ensureOpen()
        logicalSize
    }

    override fun truncate(size: Long): FileChannel = synchronized(lock) {
        ensureOpen()
        require(size >= 0) { "Size must not be negative" }
        MemoryMappedStore.removeMappedFileSegments(fileId)
        channel.truncate(size)
        logicalSize = min(logicalSize, size)
        currentPosition = min(currentPosition, size)
        this
    }

    override fun force(metaData: Boolean) = synchronized(lock) {
        ensureOpen()
        MemoryMappedStore.forceMappedFileSegments(fileId)
        channel.force(metaData)
    }

    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long = synchronized(lock) {
        ensureOpen()
        require(position >= 0) { "Position must not be negative" }
        if (count <= 0 || position >= logicalSize) {
            return@synchronized 0L
        }
        channel.transferTo(position, min(count, logicalSize - position), target)
    }

    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long = synchronized(lock) {
        ensureOpen()
        require(position >= 0) { "Position must not be negative" }
        if (count <= 0) {
            return@synchronized 0L
        }

        val transferBuffer = ByteBuffer.allocate(min(DEFAULT_TRANSFER_BUFFER_SIZE.toLong(), count).toInt())
        var total = 0L
        var writePosition = position
        while (total < count) {
            transferBuffer.clear()
            transferBuffer.limit(min(transferBuffer.capacity().toLong(), count - total).toInt())
            val bytesRead = src.read(transferBuffer)
            if (bytesRead < 0) {
                break
            }
            if (bytesRead == 0) {
                break
            }
            transferBuffer.flip()
            writePosition += writeMapped(transferBuffer, writePosition)
            total += bytesRead
        }
        total
    }

    override fun read(dst: ByteBuffer, position: Long): Int = synchronized(lock) {
        ensureOpen()
        require(position >= 0) { "Position must not be negative" }
        if (position >= logicalSize) {
            return@synchronized -1
        }

        val originalLimit = dst.limit()
        val readable = min(dst.remaining().toLong(), logicalSize - position).toInt()
        dst.limit(dst.position() + readable)
        try {
            channel.read(dst, position)
        } finally {
            dst.limit(originalLimit)
        }
    }

    override fun write(src: ByteBuffer, position: Long): Int = synchronized(lock) {
        ensureOpen()
        require(position >= 0) { "Position must not be negative" }
        writeMapped(src, position)
    }

    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer {
        ensureOpen()
        return channel.map(mode, position, size)
    }

    override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
        ensureOpen()
        return channel.lock(position, size, shared)
    }

    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? {
        ensureOpen()
        return channel.tryLock(position, size, shared)
    }

    override fun implCloseChannel() {
        synchronized(lock) {
            var failure: IOException? = null

            MemoryMappedStore.removeMappedFileSegments(fileId)

            try {
                if (channel.isOpen) {
                    channel.truncate(logicalSize)
                    channel.force(true)
                }
            } catch (exception: IOException) {
                failure = exception
            }

            try {
                channel.close()
            } catch (exception: IOException) {
                if (failure == null) {
                    failure = exception
                }
            }

            failure?.let { throw it }
        }
    }

    private fun writeMapped(src: ByteBuffer, position: Long): Int {
        ensureOpen()
        val initialRemaining = src.remaining()
        var current = position
        while (src.hasRemaining()) {
            val destination = getBuffer(current)
            destination.position(getBufferLocation(current))
            current += copy(src, destination)
        }
        logicalSize = maxOf(logicalSize, current)
        return initialRemaining
    }

    private fun getBuffer(position: Long): ByteBuffer {
        val key = keyForPosition(position)
        return MemoryMappedStore.getMappedFileSegmentBuffer(key) {
            mapSegment(key)
        }
    }

    private fun getBufferLocation(position: Long) = (position % bufferSliceSize).toInt()

    private fun keyForPosition(position: Long): MappedFileSegmentKey =
        MappedFileSegmentKey(fileId, (position / bufferSliceSize).toInt())

    private fun mapSegment(key: MappedFileSegmentKey): MappedFileSegment =
        MappedFileSegmentFactory.map(
            channel = channel,
            offset = key.idx.toLong() * bufferSliceSize,
            size = bufferSliceSize
        ) {
            MemoryMappedStore.evictLeastRecentlyUsedMappedFileSegment()
        }

    private fun ensureOpen() {
        if (!isOpen) {
            throw ClosedChannelException()
        }
        if (!channel.isOpen) {
            throw ClosedChannelException()
        }
    }

    private fun validateArrayRange(size: Int, offset: Int, length: Int) {
        require(offset >= 0) { "Offset must not be negative" }
        require(length >= 0) { "Length must not be negative" }
        require(offset <= size && length <= size - offset) { "Offset and length exceed array size" }
    }

    companion object {
        private const val DEFAULT_TRANSFER_BUFFER_SIZE = 16 * 1024
    }
}
