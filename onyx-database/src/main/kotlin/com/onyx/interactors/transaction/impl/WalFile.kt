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
