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
 * Removes unused memory-mapped capacity or an incomplete final transaction from a regular WAL.
 *
 * A writable memory mapping may leave the physical file larger than the logical transaction stream
 * when the process exits before normal WAL finalization. Reopening at that physical size would put a
 * zero-filled hole between the old and new transactions. Only a terminal tail is removed here; data
 * after a padding marker is treated as corruption and is never discarded automatically.
 */
@Throws(IOException::class)
internal fun Path.normalizeRegularWalForReopen(): Long {
    if (isCompressedWal()) {
        throw IOException("Cannot normalize compressed WAL $this as a writable WAL")
    }

    FileChannel.open(
        this,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE
    ).use { channel ->
        val physicalSize = channel.size()
        val logicalSize = channel.regularWalLogicalSize(this)
        if (logicalSize < physicalSize) {
            channel.truncate(logicalSize)
            channel.force(true)
        }
        return logicalSize
    }
}

@Throws(IOException::class)
private fun FileChannel.regularWalLogicalSize(walFile: Path): Long {
    val physicalSize = size()
    val header = ByteBuffer.allocate(WAL_TRANSACTION_HEADER_SIZE)
    var position = 0L

    while (position < physicalSize) {
        if (physicalSize - position < WAL_TRANSACTION_HEADER_SIZE) {
            // A crash can leave only part of the final transaction header durable.
            return position
        }

        header.clear()
        var readPosition = position
        while (header.hasRemaining()) {
            val bytesRead = read(header, readPosition)
            if (bytesRead < 0) return position
            if (bytesRead == 0) continue
            readPosition += bytesRead
        }
        header.flip()

        val transactionType = header.get().toInt() and 0xff
        val transactionLength = header.int
        if (transactionType == WAL_PADDING_TYPE && transactionLength == 0) {
            if (hasNonZeroBytes(position, physicalSize)) {
                throw IOException(
                    "WAL $walFile contains non-zero data after padding at byte $position"
                )
            }
            return position
        }
        if (transactionType !in WAL_TRANSACTION_TYPES || transactionLength <= 0) {
            throw IOException(
                "WAL $walFile has an invalid transaction header at byte $position"
            )
        }

        val nextPosition = position + WAL_TRANSACTION_HEADER_SIZE + transactionLength.toLong()
        if (nextPosition > physicalSize) {
            // The final transaction was interrupted before its complete payload reached the file.
            return position
        }
        position = nextPosition
    }

    return position
}

@Throws(IOException::class)
private fun FileChannel.hasNonZeroBytes(startPosition: Long, endPosition: Long): Boolean {
    val buffer = ByteBuffer.allocate(WAL_SCAN_BUFFER_SIZE)
    var position = startPosition
    while (position < endPosition) {
        buffer.clear()
        buffer.limit(minOf(buffer.capacity().toLong(), endPosition - position).toInt())
        val bytesRead = read(buffer, position)
        if (bytesRead < 0) break
        if (bytesRead == 0) continue
        buffer.flip()
        while (buffer.hasRemaining()) {
            if (buffer.get() != 0.toByte()) return true
        }
        position += bytesRead
    }
    return false
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
