package com.onyx.interactors.transaction.impl

import com.onyx.buffer.copy
import com.onyx.diskmap.store.impl.FileChannelStore
import com.onyx.diskmap.store.impl.MappedFileSegment
import com.onyx.diskmap.store.impl.MappedFileSegmentFactory
import com.onyx.diskmap.store.impl.MappedFileSegmentKey
import com.onyx.diskmap.store.impl.MemoryMappedStore
import com.onyx.exception.TransactionException
import com.onyx.extension.common.Block
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
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Transaction WAL store that appends through the shared memory-mapped segment cache used by [MemoryMappedStore].
 */
open class MemoryMappedTransactionStore(val location: String) : TransactionStore {

    private val journalFileIndex = AtomicLong(0L)
    private var lastWalFileChannel: FileChannel? = null
    private var lastWalFile: File? = null
    private val transactionFileLock = Block()
    private val asynchronousFlushFailure = AtomicReference<Throwable?>()
    private val flushExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "onyx-wal-flush-${flushThreadIndex.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
    private var isClosing = false

    private val walDirectory: String
        get() = this.location + File.separator + "wal" + File.separator

    @Throws(TransactionException::class)
    override fun getTransactionFile(): FileChannel = synchronized(transactionFileLock) {
        if (isClosing) {
            throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_OPEN_FILE)
        }
        throwIfAsynchronousFlushFailed()

        try {
            if (lastWalFileChannel == null) {
                val directory = walDirectory
                val journalingDirector = File(directory)
                if (!journalingDirector.exists()) {
                    journalingDirector.mkdirs()
                }

                val walFiles = journalingDirector.listWalFiles()
                walFiles.dropLast(1).forEach { sealedWalFile ->
                    if (!sealedWalFile.file.toPath().isCompressedWal()) {
                        compressWalFileOrThrow(sealedWalFile.file.toPath())
                    }
                }

                val latestWalFile = walFiles.lastOrNull()
                if (latestWalFile != null) {
                    journalFileIndex.set(
                        if (latestWalFile.file.toPath().isCompressedWal()) {
                            latestWalFile.index + 1L
                        } else {
                            latestWalFile.index
                        }
                    )
                }

                lastWalFile = File(directory + journalFileIndex.get() + WAL_FILE_EXTENSION)
                lastWalFileChannel = openMappedWalFile(lastWalFile!!.path)
            }

            if (lastWalFileChannel!!.size() >= maxJournalSize) {
                val rotatedWalFile = lastWalFileChannel!!
                val rotatedWalPath = lastWalFile!!.toPath()
                val directory = walDirectory
                val nextJournalFileIndex = journalFileIndex.get() + 1L
                val nextWalPath = File(directory + nextJournalFileIndex + WAL_FILE_EXTENSION)
                val nextWalFile = openMappedWalFile(nextWalPath.path)
                (rotatedWalFile as MemoryMappedTransactionFileChannel).retire()
                journalFileIndex.set(nextJournalFileIndex)
                lastWalFile = nextWalPath
                lastWalFileChannel = nextWalFile
                finalizeWalFileAsynchronously(rotatedWalFile, rotatedWalPath, compress = true)
            }

            return lastWalFileChannel!!
        } catch (cause: IOException) {
            throw TransactionException(
                TransactionException.TRANSACTION_FAILED_TO_OPEN_FILE,
                null,
                cause
            )
        }
    }

    override fun close() {
        synchronized(transactionFileLock) {
            if (!isClosing) {
                isClosing = true
                lastWalFileChannel?.let {
                    var canCompress = true
                    val toppedOff = try {
                        it.size() >= maxJournalSize
                    } catch (failure: Throwable) {
                        recordAsynchronousFlushFailure(failure)
                        canCompress = false
                        false
                    }
                    try {
                        (it as MemoryMappedTransactionFileChannel).retire()
                    } catch (failure: Throwable) {
                        recordAsynchronousFlushFailure(failure)
                        canCompress = false
                    }
                    try {
                        finalizeWalFileAsynchronously(
                            it,
                            lastWalFile?.toPath(),
                            compress = toppedOff && canCompress
                        )
                    } catch (failure: Throwable) {
                        recordAsynchronousFlushFailure(failure)
                        try {
                            finalizeWalFile(it)
                        } catch (closeFailure: Throwable) {
                            recordAsynchronousFlushFailure(closeFailure)
                        }
                    } finally {
                        lastWalFileChannel = null
                        lastWalFile = null
                    }
                }
                flushExecutor.shutdown()
            }
        }

        awaitAsynchronousFlushes()
        throwIfAsynchronousFlushFailed()
    }

    protected open val maxJournalSize: Long
        get() = MAX_JOURNAL_SIZE

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

    /**
     * Finalizes a WAL file after it has been removed from the transaction write path.
     * Closing the mapped channel forces its mapped data, truncates the mapped reservation
     * to the logical WAL length, forces that length metadata, and then closes the file.
     */
    protected open fun finalizeWalFile(walFile: FileChannel) {
        walFile.close()
    }

    protected open fun compressWalFile(walFile: Path) {
        replaceWithCompressedWal(walFile)
    }

    private fun compressWalFileOrThrow(walFile: Path) {
        try {
            compressWalFile(walFile)
        } catch (cause: Exception) {
            throw TransactionException(
                TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE,
                null,
                cause
            )
        }
    }

    private fun finalizeWalFileAsynchronously(
        walFile: FileChannel,
        walFilePath: Path?,
        compress: Boolean
    ) {
        flushExecutor.execute {
            try {
                finalizeWalFile(walFile)
                if (compress) {
                    compressWalFile(checkNotNull(walFilePath))
                }
            } catch (failure: Throwable) {
                recordAsynchronousFlushFailure(failure)
            }
        }
    }

    private fun recordAsynchronousFlushFailure(failure: Throwable) {
        val currentFailure = asynchronousFlushFailure.get()
        if (currentFailure == null) {
            if (!asynchronousFlushFailure.compareAndSet(null, failure)) {
                asynchronousFlushFailure.get()
                    ?.takeIf { it !== failure }
                    ?.addSuppressed(failure)
            }
        } else if (currentFailure !== failure) {
            currentFailure.addSuppressed(failure)
        }
    }

    private fun throwIfAsynchronousFlushFailed() {
        val failure = asynchronousFlushFailure.get() ?: return
        throw TransactionException(
            TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE,
            null,
            failure
        )
    }

    private fun awaitAsynchronousFlushes() {
        var interrupted = false
        while (!flushExecutor.isTerminated) {
            try {
                flushExecutor.awaitTermination(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val MAX_JOURNAL_SIZE = 1024L * 1024L * 20L
        private val flushThreadIndex = AtomicLong()
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
    private var acceptsWrites = true
    private var detachedSegments: List<MappedFileSegment>? = null

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
        ensureWritable()
        require(size >= 0) { "Size must not be negative" }
        MemoryMappedStore.removeMappedFileSegments(fileId)
        channel.truncate(size)
        logicalSize = min(logicalSize, size)
        currentPosition = min(currentPosition, size)
        this
    }

    override fun force(metaData: Boolean) = synchronized(lock) {
        ensureOpen()
        val retiredSegments = detachedSegments
        if (retiredSegments == null) {
            MemoryMappedStore.forceMappedFileSegments(fileId)
        } else {
            retiredSegments.forEach { it.force() }
        }
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
        ensureWritable()
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
        ensureWritable()
        require(position >= 0) { "Position must not be negative" }
        writeMapped(src, position)
    }

    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer = synchronized(lock) {
        ensureOpen()
        if (mode != MapMode.READ_ONLY) {
            ensureWritable()
        }
        channel.map(mode, position, size)
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
            val mappedSegments = detachedSegments
                ?: MemoryMappedStore.retireMappedFileSegments(fileId)
            detachedSegments = null

            try {
                MemoryMappedStore.forceAndCloseMappedFileSegments(mappedSegments)
            } catch (exception: Throwable) {
                failure = exception as? IOException ?: IOException("Unable to force mapped WAL data", exception)
            }

            try {
                if (failure == null && channel.isOpen) {
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
                } else {
                    failure.addSuppressed(exception)
                }
            } finally {
                MemoryMappedStore.releaseRetiredMappedFile(fileId)
            }

            failure?.let { throw it }
        }
    }

    private fun writeMapped(src: ByteBuffer, position: Long): Int {
        ensureOpen()
        ensureWritable()
        val initialRemaining = src.remaining()
        var current = position
        while (src.hasRemaining()) {
            val copied = withBuffer(current, markDirty = true) { destination ->
                destination.position(getBufferLocation(current))
                copy(src, destination)
            }
            current += copied
        }
        logicalSize = maxOf(logicalSize, current)
        return initialRemaining
    }

    private fun <T> withBuffer(position: Long, markDirty: Boolean, action: (ByteBuffer) -> T): T {
        val key = keyForPosition(position)
        return MemoryMappedStore.withMappedFileSegmentBuffer(
            key = key,
            mapper = { mapSegment(key) },
            markDirty = markDirty,
            action = action
        )
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

    fun retire() = synchronized(lock) {
        ensureOpen()
        acceptsWrites = false
        detachedSegments = MemoryMappedStore.retireMappedFileSegments(fileId)
    }

    private fun ensureWritable() {
        if (!acceptsWrites) {
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
