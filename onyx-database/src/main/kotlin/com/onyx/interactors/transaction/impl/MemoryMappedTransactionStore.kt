package com.onyx.interactors.transaction.impl

import com.onyx.diskmap.store.impl.FileChannelStore
import com.onyx.diskmap.store.impl.WholeFileMapping
import com.onyx.exception.TransactionException
import com.onyx.extension.common.openFileChannel
import com.onyx.interactors.transaction.TransactionStore
import java.io.File
import java.io.IOException
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Memory-mapped transaction WAL with independent background forcing.
 */
open class MemoryMappedTransactionStore(val location: String) : TransactionStore {

    private var journalFileIndex = 0L
    @Volatile
    private var lastWalFileChannel: FileChannel? = null
    private var lastWalFile: File? = null
    private val transactionFileLock = Any()
    // Only flushing and shutdown acquire this lock. Appenders never do.
    private val periodicForceLock = Any()
    @Volatile private var nextWal: PreparedWal? = null
    private val asynchronousFlushFailure = AtomicReference<Throwable?>()
    private val flushExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "onyx-wal-flush-${flushThreadIndex.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
    @Volatile private var isClosing = false
    private val periodicForceTarget = PeriodicWalForceTarget(::forceCurrentWal)

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
                journalingDirector.listFiles { _, name -> name.matches(PREPARED_WAL_NAME) }
                    ?.forEach { Files.deleteIfExists(it.toPath()) }

                val walFiles = journalingDirector.listWalFiles()
                walFiles.dropLast(1).forEach { sealedWalFile ->
                    if (!sealedWalFile.file.toPath().isCompressedWal()) {
                        sealedWalFile.file.toPath().normalizeRegularWalForReopen()
                        compressWalFileOrThrow(sealedWalFile.file.toPath())
                    }
                }

                val latestWalFile = walFiles.lastOrNull()
                if (latestWalFile != null) {
                    journalFileIndex =
                        if (latestWalFile.file.toPath().isCompressedWal()) {
                            latestWalFile.index + 1L
                        } else {
                            latestWalFile.index
                        }
                }

                lastWalFile = File(directory + journalFileIndex + WAL_FILE_EXTENSION)
                lastWalFileChannel = openMappedWalFile(lastWalFile!!.path)
                prepareNextWal()
                PeriodicWalFlusher.register(periodicForceTarget)
            }

            // The threshold is soft: keep appending if preparation is still busy.
            // Never join background I/O from the transaction write path.
            val prepared = nextWal
            if (lastWalFileChannel!!.size() >= maxJournalSize && prepared != null) {
                val rotatedWalFile = lastWalFileChannel!!
                val rotatedWalPath = lastWalFile!!.toPath()
                val nextJournalFileIndex = journalFileIndex + 1L
                val nextWalPath = File(walDirectory + nextJournalFileIndex + WAL_FILE_EXTENSION)
                Files.move(prepared.path, nextWalPath.toPath())
                (rotatedWalFile as MemoryMappedTransactionFileChannel).retire()
                journalFileIndex = nextJournalFileIndex
                lastWalFile = nextWalPath
                lastWalFileChannel = prepared.channel
                nextWal = null
                // Queue preparation before the old file's potentially slow finalization.
                prepareNextWal()
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
                PeriodicWalFlusher.unregister(periodicForceTarget)
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
                flushExecutor.execute {
                    try {
                        // Preparation is queued on this same executor, so its
                        // result is published before this cleanup can run.
                        nextWal?.let { prepared ->
                            nextWal = null
                            try {
                                prepared.channel.close()
                            } finally {
                                Files.deleteIfExists(prepared.path)
                            }
                        }
                    } catch (failure: Throwable) {
                        recordAsynchronousFlushFailure(failure)
                    }
                }
                flushExecutor.shutdown()
            }
        }

        // unregister() does not join a callback already running. Wait outside
        // the write lock, including publication of any periodic flush failure.
        synchronized(periodicForceLock) { }
        awaitAsynchronousFlushes()
        throwIfAsynchronousFlushFailed()
    }

    protected open val maxJournalSize: Long
        get() = MAX_JOURNAL_SIZE

    private fun forceCurrentWal() = synchronized(periodicForceLock) {
        if (isClosing) return@synchronized
        try {
            lastWalFileChannel?.let(::forceWalFile)
        } catch (failure: Throwable) {
            recordAsynchronousFlushFailure(failure)
            throw failure
        }
    }

    protected open fun forceWalFile(walFile: FileChannel) {
        (walFile as MemoryMappedTransactionFileChannel).forceInBackground()
    }

    protected open fun prepareWalFile(walPath: Path): FileChannel =
        openMappedWalFile(walPath.toString()).also { channel ->
            try {
                channel.forceInBackground()
            } catch (failure: Throwable) {
                runCatching { channel.close() }
                throw failure
            }
        }

    internal fun isNextWalPrepared(): Boolean = nextWal != null

    private fun prepareNextWal() {
        val path = Path.of(walDirectory, ".onyx-next-${journalFileIndex + 1L}.pending")
        flushExecutor.execute {
            try {
                nextWal = PreparedWal(path, prepareWalFile(path))
            } catch (failure: Throwable) {
                recordAsynchronousFlushFailure(failure)
                runCatching { Files.deleteIfExists(path) }
            }
        }
    }

    private fun openMappedWalFile(filePath: String): MemoryMappedTransactionFileChannel {
        val file = File(filePath)
        val existingFile = file.exists()
        if (!existingFile) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        } else if (file.length() > 0L) {
            file.toPath().normalizeRegularWalForReopen()
        }

        val channel = file.path.openFileChannel()
            ?: throw IOException("Unable to open WAL file channel")

        return try {
            MemoryMappedTransactionFileChannel(channel, min(maxJournalSize, MAX_JOURNAL_SIZE))
        } catch (failure: Throwable) {
            runCatching { channel.close() }
            throw failure
        }
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
        private val PREPARED_WAL_NAME = Regex("\\.onyx-next-\\d+\\.pending")
        private const val MAX_JOURNAL_SIZE = 1024L * 1024L * 20L
        private val flushThreadIndex = AtomicLong()
    }

    private data class PreparedWal(val path: Path, val channel: FileChannel)
}

internal class MemoryMappedTransactionFileChannel(
    private val channel: FileChannel,
    reserveBytes: Long,
    private val mappedForce: (MemorySegment) -> Unit = { it.force() }
) : FileChannel() {

    private val lock = Any()
    private val forceLock = Any()
    private val growthQuantum = WholeFileMapping.defaultGrowthQuantum(FileChannelStore.isSmallDevice)
    // Reserve a normal segment plus room for its crossing record. Larger records
    // or delayed preparation can grow it without waiting for background forcing.
    // This is mapped file capacity, not heap.
    private val initialCapacity = if (reserveBytes > growthQuantum) {
        Math.addExact(reserveBytes, growthQuantum)
    } else {
        growthQuantum
    }

    private var currentPosition = channel.position()
    private var logicalSize = channel.size()
    private var wholeFileMapping: WholeFileMapping? = WholeFileMapping(
        channel = channel,
        growthQuantum = growthQuantum,
        initialRequiredCapacity = maxOf(logicalSize, initialCapacity),
        forceEnabled = true,
        mappedForce = mappedForce
    )
    private var acceptsWrites = true

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
            // The gathering call already holds the channel lock. Advance after
            // each buffer so a later failure preserves partial-write position.
            val written = writeMapped(srcs[index], currentPosition)
            currentPosition += written
            total += written.toLong()
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

    override fun truncate(size: Long): FileChannel = synchronized(forceLock) {
        synchronized(lock) {
            ensureOpen()
            ensureWritable()
            require(size >= 0) { "Size must not be negative" }
            if (size >= logicalSize) return this

            replaceMappingAfterTruncate(size)
            logicalSize = size
            currentPosition = min(currentPosition, logicalSize)
            this
        }
    }

    override fun force(metaData: Boolean) = synchronized(forceLock) {
        val mapping = synchronized(lock) {
            ensureOpen()
            currentMapping()
        }
        mapping.force()
        channel.force(metaData)
    }

    fun forceInBackground() = synchronized(forceLock) {
        val mapping = synchronized(lock) {
            // Rotation may have closed a channel captured by the periodic worker.
            if (!isOpen) return
            ensureOpen()
            currentMapping()
        }
        mapping.force()
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

    override fun implCloseChannel(): Unit = synchronized(forceLock) {
        synchronized(lock) {
            var failure: IOException? = null
            val closingMapping = wholeFileMapping
            wholeFileMapping = null

            try {
                closingMapping?.close()
            } catch (exception: Throwable) {
                failure = exception as? IOException ?: IOException("Unable to close mapped WAL data", exception)
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
            }

            failure?.let { throw it }
        }
    }

    private fun writeMapped(src: ByteBuffer, position: Long): Int {
        ensureOpen()
        ensureWritable()
        val written = currentMapping().write(src, position)
        if (written > 0) {
            logicalSize = maxOf(logicalSize, Math.addExact(position, written.toLong()))
        }
        return written
    }

    private fun currentMapping(): WholeFileMapping =
        wholeFileMapping ?: throw ClosedChannelException()

    private fun replaceMappingAfterTruncate(size: Long) {
        val previous = currentMapping()
        wholeFileMapping = null

        try {
            previous.close()
            channel.truncate(size)
            wholeFileMapping = WholeFileMapping(
                channel = channel,
                growthQuantum = growthQuantum,
                initialRequiredCapacity = maxOf(size, initialCapacity),
                forceEnabled = true,
                mappedForce = mappedForce
            )
        } catch (failure: Throwable) {
            runCatching { channel.close() }
            throw failure
        }
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
        // The sealed mapping remains owned by this channel until asynchronous
        // finalization closes it; only further writes are rejected immediately.
        acceptsWrites = false
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
