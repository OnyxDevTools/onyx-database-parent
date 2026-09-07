package com.onyx.interactors.transaction.impl

import com.onyx.extension.common.compressLz77
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission

internal const val WAL_FILE_EXTENSION = ".wal"

private val WAL_FILE_NAME = Regex("^(\\d+)\\.wal$")
private val LZ77_MAGIC = byteArrayOf(
    'L'.code.toByte(),
    'Z'.code.toByte(),
    '7'.code.toByte(),
    '7'.code.toByte()
)
private const val WAL_TRANSACTION_HEADER_SIZE = 5
private const val WAL_PADDING_TYPE = 0
private val WAL_TRANSACTION_TYPES = 1..4
private const val WAL_SCAN_BUFFER_SIZE = 64 * 1024

internal fun ByteArray.isCompressedWalFrame(): Boolean =
    size >= LZ77_MAGIC.size && LZ77_MAGIC.indices.all { this[it] == LZ77_MAGIC[it] }

internal data class IndexedWalFile(
    val index: Long,
    val file: File
)

@Throws(IOException::class)
internal fun File.listWalFiles(): List<IndexedWalFile> {
    val files = listFiles() ?: throw IOException("Unable to list WAL directory: $path")
    return files
        .mapNotNull { file ->
            if (!file.isFile) return@mapNotNull null
            val index = WAL_FILE_NAME.matchEntire(file.name)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?: return@mapNotNull null
            IndexedWalFile(index, file)
        }
        .sortedBy(IndexedWalFile::index)
}

internal fun Path.isCompressedWal(): Boolean {
    if (!Files.isRegularFile(this) || Files.size(this) < LZ77_MAGIC.size) return false

    FileChannel.open(this, StandardOpenOption.READ).use { channel ->
        val header = ByteBuffer.allocate(LZ77_MAGIC.size)
        while (header.hasRemaining()) {
            if (channel.read(header) < 0) return false
        }
        return header.array().isCompressedWalFrame()
    }
}

/**
 * Removes unused memory-mapped capacity and an incomplete final transaction from a regular WAL.
 *
 * A writable memory mapping may leave the physical file larger than the logical transaction stream
 * when the process exits before normal WAL finalization. Reopening at that physical size would put a
 * zero-filled hole between the old and new transactions. Older writers may already have appended
 * after such a hole. Preserve every complete transaction on both sides by compacting into a forced
 * replacement before atomically installing it. Invalid non-zero headers still fail without changing
 * the original file; only a terminal incomplete record may be discarded.
 */
@Throws(IOException::class)
internal fun Path.normalizeRegularWalForReopen(): Long {
    if (isCompressedWal()) {
        throw IOException("Cannot normalize compressed WAL $this as a writable WAL")
    }

    var replacement: Path? = null
    try {
        val logicalSize = FileChannel.open(
            this,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE
        ).use { channel ->
            val ranges = channel.regularWalTransactionRanges(this)
            val logicalSize = ranges.sumOf { it.end - it.start }
            if (ranges.size <= 1 && (ranges.firstOrNull()?.start ?: 0L) == 0L) {
                if (logicalSize < channel.size()) {
                    channel.truncate(logicalSize)
                    channel.force(true)
                }
            } else {
                val permissions = posixPermissionsOrNull()
                val temporary = Files.createTempFile(toAbsolutePath().parent, ".${fileName}.", ".tmp")
                replacement = temporary
                FileChannel.open(temporary, StandardOpenOption.WRITE).use { output ->
                    val buffer = ByteBuffer.allocate(WAL_SCAN_BUFFER_SIZE)
                    for (range in ranges) {
                        var position = range.start
                        while (position < range.end) {
                            buffer.clear()
                            buffer.limit(minOf(buffer.capacity().toLong(), range.end - position).toInt())
                            val bytesRead = channel.read(buffer, position)
                            if (bytesRead < 0) throw IOException("WAL $this changed during recovery")
                            if (bytesRead == 0) continue
                            buffer.flip()
                            while (buffer.hasRemaining()) output.write(buffer)
                            position += bytesRead
                        }
                    }
                    if (permissions != null) Files.setPosixFilePermissions(temporary, permissions)
                    output.force(true)
                }
            }
            logicalSize
        }

        replacement?.let { temporary ->
            // Do not fall back to overwriting the original: an interrupted repair must remain retryable.
            Files.move(temporary, this, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            val parent = toAbsolutePath().parent
            if (Files.getFileStore(parent).supportsFileAttributeView("posix")) {
                FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
            }
        }
        return logicalSize
    } catch (failure: Throwable) {
        try {
            replacement?.let { Files.deleteIfExists(it) }
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
        throw failure
    }
}

private data class WalTransactionRange(val start: Long, val end: Long)

@Throws(IOException::class)
private fun FileChannel.regularWalTransactionRanges(walFile: Path): List<WalTransactionRange> {
    val physicalSize = size()
    val ranges = ArrayList<WalTransactionRange>()
    val header = ByteBuffer.allocate(WAL_TRANSACTION_HEADER_SIZE)
    var position = 0L
    var rangeStart = position

    while (position < physicalSize) {
        header.clear()
        header.limit(minOf(WAL_TRANSACTION_HEADER_SIZE.toLong(), physicalSize - position).toInt())
        var readPosition = position
        while (header.hasRemaining()) {
            val bytesRead = read(header, readPosition)
            if (bytesRead < 0) throw IOException("WAL $walFile changed during recovery")
            if (bytesRead == 0) continue
            readPosition += bytesRead
        }
        header.flip()

        val transactionType = header.get().toInt() and 0xff
        if (transactionType == WAL_PADDING_TYPE) {
            if (position > rangeStart) ranges += WalTransactionRange(rangeStart, position)
            position = firstNonZeroPosition(position, physicalSize)
            rangeStart = position
            continue
        }
        if (transactionType !in WAL_TRANSACTION_TYPES) {
            throw IOException(
                "WAL $walFile has an invalid transaction header at byte $position"
            )
        }
        if (header.remaining() < Int.SIZE_BYTES) {
            // A crash can leave only part of the final transaction header durable.
            break
        }
        val transactionLength = header.int
        if (transactionLength <= 0) {
            throw IOException("WAL $walFile has an invalid transaction header at byte $position")
        }

        val nextPosition = position + WAL_TRANSACTION_HEADER_SIZE + transactionLength.toLong()
        if (nextPosition > physicalSize) {
            // The final transaction was interrupted before its complete payload reached the file.
            break
        }
        position = nextPosition
    }

    if (position > rangeStart) ranges += WalTransactionRange(rangeStart, position)
    return ranges
}

@Throws(IOException::class)
private fun FileChannel.firstNonZeroPosition(startPosition: Long, endPosition: Long): Long {
    val buffer = ByteBuffer.allocate(WAL_SCAN_BUFFER_SIZE)
    var position = startPosition
    while (position < endPosition) {
        buffer.clear()
        buffer.limit(minOf(buffer.capacity().toLong(), endPosition - position).toInt())
        val bytesRead = read(buffer, position)
        if (bytesRead < 0) throw IOException("WAL changed while scanning padding at byte $position")
        if (bytesRead == 0) continue
        buffer.flip()
        while (buffer.hasRemaining()) {
            if (buffer.get() != 0.toByte()) return position + buffer.position() - 1L
        }
        position += bytesRead
    }
    return endPosition
}

/**
 * Compresses a sealed WAL and replaces its regular contents at the same path, atomically when supported.
 * The original WAL remains intact unless the complete compressed replacement has been forced.
 */
@Throws(IOException::class)
internal fun replaceWithCompressedWal(walFile: Path) {
    if (walFile.isCompressedWal()) return

    val compressedWal = Files.readAllBytes(walFile).compressLz77()
    val originalPermissions = walFile.posixPermissionsOrNull()
    val parent = walFile.toAbsolutePath().parent
        ?: throw IOException("WAL file does not have a parent directory")
    val replacement = Files.createTempFile(parent, ".${walFile.fileName}.", ".tmp")

    try {
        FileChannel.open(
            replacement,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        ).use { channel ->
            val bytes = ByteBuffer.wrap(compressedWal)
            while (bytes.hasRemaining()) {
                channel.write(bytes)
            }
            if (originalPermissions != null) {
                Files.setPosixFilePermissions(replacement, originalPermissions)
            }
            channel.force(true)
        }

        try {
            Files.move(
                replacement,
                walFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(replacement, walFile, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(replacement)
    }
}

private fun Path.posixPermissionsOrNull(): Set<PosixFilePermission>? = try {
    Files.getPosixFilePermissions(this)
} catch (_: UnsupportedOperationException) {
    null
}
