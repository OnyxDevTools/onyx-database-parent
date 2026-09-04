@file:Suppress("UNCHECKED_CAST")

package com.onyx.interactors.transaction.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemPartitionEntry
import com.onyx.exception.TransactionException
import com.onyx.extension.common.metadata
import com.onyx.extension.withBuffer
import com.onyx.extension.createNewEntity
import com.onyx.interactors.transaction.TransactionInteractor
import com.onyx.interactors.transaction.data.*
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.interactors.transaction.TransactionStore

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.min

/**
 * Created by Tim Osborn on 3/25/16.
 *
 * Handles logging of a transaction
 */
open class DefaultTransactionInteractor(private val transactionStore: TransactionStore, private val persistenceManager: PersistenceManager) : TransactionInteractor {

    private val transactionWriteLock = Any()
    private val transactionMetadataBuffer = ByteBuffer.allocate(TRANSACTION_METADATA_SIZE)
    private val transactionWriteBuffers = arrayOf(transactionMetadataBuffer, transactionMetadataBuffer)

    private fun writeTransaction(transactionType:Byte, buffer: ByteBuffer) {
        withBuffer(buffer) { transBuffer ->
            try {
                synchronized(transactionWriteLock) {
                    transactionMetadataBuffer.clear()
                    transactionMetadataBuffer.put(transactionType)
                    transactionMetadataBuffer.putInt(transBuffer.limit())
                    transactionMetadataBuffer.flip()

                    transactionWriteBuffers[1] = transBuffer
                    try {
                        val file = transactionStore.getTransactionFile()
                        file.writeFully(transactionWriteBuffers)
                    } finally {
                        // Do not retain a large, non-pooled serialization buffer between transactions.
                        transactionWriteBuffers[1] = transactionMetadataBuffer
                    }
                }
            } catch (e: TransactionException) {
                throw e
            } catch (_: Exception) {
                throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE)
            }
        }
    }

    /**
     * Write a save transaction to a WAL file
     *
     * @param entity Entity to save
     */
    @Throws(TransactionException::class)
    override fun writeSave(entity: IManagedEntity) {
        if (entity is SystemEntity || entity is SystemPartitionEntry) return
        val map = (entity as ManagedEntity).toMap(persistenceManager.context)
        val value = hashMapOf<String, Any?>(
            "type" to entity::class.java.canonicalName,
            "value" to map
        )
        writeTransaction(SAVE, BufferStream.toBuffer(value, persistenceManager.context))
    }

    /**
     * Write a query update to the WAL transaction
     *
     * @param query Query to update
     */
    @Throws(TransactionException::class)
    override fun writeQueryUpdate(query: Query) {
        writeTransaction(UPDATE_QUERY, BufferStream.toBuffer(query, persistenceManager.context))
    }

    /**
     * Write a Delete transaction to a WAL File
     *
     * @param entity Deleted entity
     */
    @Throws(TransactionException::class)
    override fun writeDelete(entity: IManagedEntity) {
        val map = (entity as ManagedEntity).toMap(persistenceManager.context)
        val value = hashMapOf<String, Any?>(
            "type" to entity::class.java.canonicalName,
            "value" to map
        )
        writeTransaction(DELETE, BufferStream.toBuffer(value, persistenceManager.context))
    }

    /**
     * Write a delete query to a WAL file
     * @param query Query to write transaction of
     */
    @Throws(TransactionException::class)
    override fun writeDeleteQuery(query: Query) {
        writeTransaction(DELETE_QUERY, BufferStream.toBuffer(query, persistenceManager.context))
    }

    /**
     * Rebuild Database From a directory of WAL transaction files to a new database location.
     * This will construct an entire database by rolling forward all of the transaction logs.
     * Note.  This may not be terribly efficient.  If you goal is to copy a database, it may
     * be easier to copy the entire storage.  This is mainly used to recover a database.
     *
     * There is a third optional parameter.  This indicates a consumer that will evaluate whether or not
     * the transaction should be included in re-building the database.  Perhaps there was a reason why the database became corrupt or
     * unrecoverable.  This is used to prevent it from happening again.  Say for instance, I ran a delete all on an entity.  Whoops, you may want to avoid that.
     *
     * @param fromDirectoryPath Directory containing WAL transaction files.
     * @param executeTransaction Function that determines whether or not you should execute the transaction
     */
    @Throws(TransactionException::class)
    override fun recoverDatabase(fromDirectoryPath: String, executeTransaction: (Transaction) -> Boolean) {
        val walDirectory = File(fromDirectoryPath)
        if (!walDirectory.exists() || !walDirectory.isDirectory) {
            throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_RECOVER_FROM_DIRECTORY)
        }

        val walFiles = try {
            walDirectory.listWalFiles()
        } catch (cause: IOException) {
            throw TransactionException(
                TransactionException.TRANSACTION_FAILED_TO_RECOVER_FROM_DIRECTORY,
                null,
                cause
            )
        }
        walFiles.forEach { walFile ->
            applyTransactionLog(walFile.file.path, executeTransaction)
        }
    }

    /**
     * Roll Database Forward an entire transaction log.
     *
     * Sometimes it may be necessary to take an entire list of transactions and apply them to an entire database.
     * An example usage would be if you had replication and experienced a network outage.  In that case in order to synchronize, you
     * could utilize this method.
     *
     * @param walTransactionFile File that contains transaction log.
     * @param executeTransaction Function that determines whether or not you should execute the transaction
     * Individual transaction application failures are reported and skipped so later transactions
     * can still be recovered. Structural or I/O failures in the WAL throw [TransactionException].
     *
     * @throws TransactionException If the WAL cannot be read safely
     */
    @Throws(TransactionException::class)
    override fun applyTransactionLog(
        walTransactionFile: String,
        executeTransaction: (Transaction) -> Boolean,
    ): Boolean = replayTransactionLog(
        walTransactionFile = walTransactionFile,
        skipFailedTransactions = true,
        executeTransaction = executeTransaction,
    )

    @Throws(TransactionException::class)
    override fun applyTransactionLog(
        walTransactionFile: String,
        skipFailedTransactions: Boolean,
        executeTransaction: (Transaction) -> Boolean,
    ): Boolean = if (skipFailedTransactions) {
        applyTransactionLog(walTransactionFile, executeTransaction)
    } else {
        replayTransactionLog(walTransactionFile, false, executeTransaction)
    }

    private fun replayTransactionLog(
        walTransactionFile: String,
        skipFailedTransactions: Boolean,
        executeTransaction: (Transaction) -> Boolean,
    ): Boolean {
        var transaction: Transaction? = null
        try {
            openWalReadSource(Path.of(walTransactionFile)).use { source ->
                WalReadBuffer(source.channel, source.logSize, WAL_READ_BUFFER_SIZE).use { wal ->
                    while (true) {
                        val transactionOffset = source.logSize - wal.bytesRemaining
                        try {
                            transaction = null
                            val metadataBytesAvailable = wal.ensureAvailable(TRANSACTION_METADATA_SIZE)
                            if (metadataBytesAvailable == 0) {
                                break
                            }
                            if (metadataBytesAvailable < TRANSACTION_METADATA_SIZE) {
                                if (wal.isZeroFilled()) {
                                    break
                                }
                                throw WalFormatException("WAL transaction header is incomplete")
                            }

                            val transactionType = wal.byte
                            val transactionDataLength = wal.int

                            if (transactionType == PADDING && transactionDataLength == 0) {
                                break
                            }
                            if (transactionType !in TRANSACTION_TYPES || transactionDataLength <= 0) {
                                throw WalFormatException("WAL transaction header is invalid")
                            }
                            if (transactionDataLength.toLong() > wal.bytesRemaining) {
                                throw WalFormatException("WAL transaction data is incomplete")
                            }

                            val pooledTransactionBuffer = transactionDataLength > wal.capacity
                            val transactionBuffer = if (pooledTransactionBuffer) {
                                BufferPool.allocateAndLimit(transactionDataLength)
                            } else {
                                wal.readSlice(transactionDataLength)
                                    ?: throw WalFormatException("WAL transaction data is incomplete")
                            }

                            try {
                                if (pooledTransactionBuffer) {
                                    val transactionBytesRead = wal.readFully(transactionBuffer)
                                    if (transactionBytesRead < transactionDataLength) {
                                        throw WalFormatException("WAL transaction data is incomplete")
                                    }
                                    transactionBuffer.flip()
                                }

                                when (transactionType) {
                                    SAVE -> {
                                        val value = BufferStream.fromBuffer(transactionBuffer, persistenceManager.context) as Map<String, Any?>
                                        val className = value["type"] as? String
                                            ?: throw IllegalArgumentException(
                                                "SAVE transaction payload is missing a valid entity type",
                                            )
                                        if (className != SystemPartitionEntry::class.java.name) {
                                            val instance = metadata(persistenceManager.context.contextId).classForName(className).createNewEntity<ManagedEntity>(this.persistenceManager.context.contextId)
                                            instance.fromMap(value["value"] as Map<String, Any?>, persistenceManager.context)
                                            transaction = SaveTransaction(instance)

                                            if (executeTransaction.invoke(transaction!!)) {
                                                instance.ignoreListeners = true
                                                try {
                                                    this.persistenceManager.saveEntity<IManagedEntity>(instance)
                                                } finally {
                                                    instance.ignoreListeners = false
                                                }
                                            }
                                        }
                                    }
                                    DELETE -> {
                                        val value = BufferStream.fromBuffer(transactionBuffer, persistenceManager.context) as Map<String, Any?>
                                        val className = value["type"] as? String
                                            ?: throw IllegalArgumentException(
                                                "DELETE transaction payload is missing a valid entity type",
                                            )
                                        val instance = metadata(persistenceManager.context.contextId).classForName(className).createNewEntity<ManagedEntity>(this.persistenceManager.context.contextId)
                                        instance.fromMap(value["value"] as Map<String, Any?>, persistenceManager.context)
                                        transaction = DeleteTransaction(instance)
                                        if (executeTransaction.invoke(transaction!!)) {
                                            instance.ignoreListeners = true
                                            try {
                                                this.persistenceManager.deleteEntity(instance)
                                            } finally {
                                                instance.ignoreListeners = false
                                            }
                                        }
                                    }
                                    UPDATE_QUERY -> {
                                        val query = BufferStream.fromBuffer(transactionBuffer, persistenceManager.context) as Query
                                        transaction = UpdateQueryTransaction(query)
                                        if (executeTransaction.invoke(transaction!!)) {
                                            this.persistenceManager.executeUpdate(query)
                                        }
                                    }
                                    DELETE_QUERY -> {
                                        val query = BufferStream.fromBuffer(transactionBuffer, persistenceManager.context) as Query
                                        transaction = DeleteQueryTransaction(query)
                                        if (executeTransaction.invoke(transaction!!)) {
                                            this.persistenceManager.executeDelete(query)
                                        }
                                    }
                                }
                            } finally {
                                if (pooledTransactionBuffer) {
                                    BufferPool.recycle(transactionBuffer)
                                }
                            }
                        } catch (cause: WalFormatException) {
                            throw TransactionException(
                                TransactionException.TRANSACTION_FAILED_TO_READ_FILE,
                                transaction,
                                cause
                            )
                        } catch (cause: IOException) {
                            throw TransactionException(
                                TransactionException.TRANSACTION_FAILED_TO_READ_FILE,
                                transaction,
                                cause
                            )
                        } catch (cause: TransactionException) {
                            handleTransactionReplayFailure(
                                walTransactionFile,
                                transactionOffset,
                                transaction,
                                cause,
                                skipFailedTransactions,
                            )
                        } catch (cause: Exception) {
                            handleTransactionReplayFailure(
                                walTransactionFile,
                                transactionOffset,
                                transaction,
                                cause,
                                skipFailedTransactions,
                            )
                        }
                    }
                }
            }
        } catch (cause: TransactionException) {
            throw cause
        } catch (cause: Exception) {
            throw TransactionException(
                TransactionException.TRANSACTION_FAILED_TO_READ_FILE,
                transaction,
                cause
            )
        }

        return true
    }

    private fun handleTransactionReplayFailure(
        walTransactionFile: String,
        transactionOffset: Long,
        transaction: Transaction?,
        cause: Exception,
        skipFailedTransactions: Boolean,
    ) {
        if (!skipFailedTransactions) {
            val transactionName = transaction?.javaClass?.simpleName ?: "unknown transaction"
            throw TransactionException(
                "Failed to replay $transactionName from WAL '$walTransactionFile' at byte $transactionOffset",
                transaction,
                cause,
            )
        }
        onTransactionReplayFailure(walTransactionFile, transactionOffset, transaction, cause)
    }

    /** Reports a failed transaction without preventing later WAL records from being replayed. */
    protected open fun onTransactionReplayFailure(
        walTransactionFile: String,
        transactionOffset: Long,
        transaction: Transaction?,
        cause: Exception
    ) {
        val transactionName = transaction?.javaClass?.simpleName ?: "unknown transaction"
        System.err.println(
            "Failed to apply $transactionName from WAL '$walTransactionFile' at byte $transactionOffset: " +
                (cause.message ?: cause.javaClass.name)
        )
        cause.printStackTrace(System.err)
    }

    companion object {
        private const val PADDING: Byte = 0
        private const val SAVE: Byte = 1
        private const val DELETE: Byte = 2
        private const val DELETE_QUERY: Byte = 3
        private const val UPDATE_QUERY: Byte = 4
        private const val TRANSACTION_METADATA_SIZE = 5
        private const val WAL_READ_BUFFER_SIZE = 256 * 1024
        private val TRANSACTION_TYPES = setOf(SAVE, DELETE, DELETE_QUERY, UPDATE_QUERY)
    }
}

private fun FileChannel.writeFully(buffers: Array<ByteBuffer>) {
    while (buffers[0].hasRemaining() || buffers[1].hasRemaining()) {
        write(buffers)
    }
}

private class WalFormatException(message: String) : IllegalStateException(message)

private class WalReadSource(
    val channel: ReadableByteChannel,
    val logSize: Long
) : AutoCloseable {
    override fun close() {
        channel.close()
    }
}

private fun openWalReadSource(walFile: Path): WalReadSource {
    val fileChannel = FileChannel.open(walFile, StandardOpenOption.READ)
    try {
        val storedSize = fileChannel.size()
        fileChannel.position(0)

        if (storedSize < LZ77_HEADER_MAGIC_SIZE) {
            return WalReadSource(fileChannel, storedSize)
        }

        val header = ByteBuffer.allocate(LZ77_HEADER_MAGIC_SIZE)
        while (header.hasRemaining()) {
            if (fileChannel.read(header) < 0) {
                throw IOException("WAL compression header is incomplete")
            }
        }
        fileChannel.position(0)

        if (!header.array().isCompressedWalFrame()) {
            return WalReadSource(fileChannel, storedSize)
        }
        if (storedSize < LZ77_HEADER_SIZE) {
            throw IOException("WAL compression header is incomplete")
        }

        val compressionHeader = ByteBuffer.allocate(LZ77_HEADER_SIZE)
        while (compressionHeader.hasRemaining()) {
            if (fileChannel.read(compressionHeader) < 0) {
                throw IOException("WAL compression header is incomplete")
            }
        }
        val compressionHeaderBytes = compressionHeader.array()
        if (compressionHeaderBytes[LZ77_VERSION_OFFSET].toInt() and 0xff != LZ77_VERSION) {
            throw IOException("Unsupported WAL compression version")
        }
        val regularWalSize = compressionHeaderBytes.lz77OutputSize().toLong()
        val compressedPayloadSize = storedSize - LZ77_HEADER_SIZE

        return when (compressionHeaderBytes[LZ77_ENCODING_OFFSET].toInt() and 0xff) {
            LZ77_RAW -> {
                if (compressedPayloadSize != regularWalSize) {
                    throw IOException("Invalid raw WAL compression frame size")
                }
                WalReadSource(fileChannel, regularWalSize)
            }
            LZ77_COMPRESSED -> WalReadSource(
                Lz77WalReadChannel(fileChannel, compressedPayloadSize, regularWalSize),
                regularWalSize
            )
            else -> throw IOException("Unsupported WAL compression encoding")
        }
    } catch (cause: Throwable) {
        runCatching { fileChannel.close() }
        throw cause
    }
}

private class Lz77WalReadChannel(
    private val channel: FileChannel,
    compressedPayloadSize: Long,
    private val outputSize: Long
) : ReadableByteChannel {
    private val compressedBuffer = ByteBuffer.allocate(LZ77_INPUT_BUFFER_SIZE).apply { limit(0) }
    private val history = ByteArray(LZ77_HISTORY_SIZE)
    private var compressedBytesRemaining = compressedPayloadSize
    private var outputPosition = 0L
    private var literalBytesRemaining = 0
    private var matchBytesRemaining = 0
    private var matchDistance = 0
    private var matchLengthNibble = 0
    private var phase = Lz77ReadPhase.TOKEN
    private var open = true
    private var finished = false
    private var readFailure: IOException? = null

    override fun read(destination: ByteBuffer): Int {
        if (!open) throw ClosedChannelException()
        if (!destination.hasRemaining()) return 0
        if (finished) return -1

        val initialRemaining = destination.remaining()
        try {
            while (destination.hasRemaining() && !finished) {
                when (phase) {
                    Lz77ReadPhase.TOKEN -> readToken()
                    Lz77ReadPhase.LITERALS -> readLiterals(destination)
                    Lz77ReadPhase.MATCH -> readMatch(destination)
                }
            }
        } catch (cause: IOException) {
            readFailure = cause
            throw cause
        }

        val bytesRead = initialRemaining - destination.remaining()
        return if (bytesRead == 0 && finished) -1 else bytesRead
    }

    override fun isOpen(): Boolean = open

    override fun close() {
        if (!open) return

        var failure: IOException? = readFailure
        if (failure == null && !finished) {
            val drainBuffer = ByteBuffer.allocate(LZ77_DRAIN_BUFFER_SIZE)
            try {
                while (!finished) {
                    drainBuffer.clear()
                    if (read(drainBuffer) < 0) break
                }
            } catch (cause: IOException) {
                failure = cause
            }
        }

        open = false
        try {
            channel.close()
        } catch (cause: IOException) {
            if (failure == null) {
                failure = cause
            } else {
                failure.addSuppressed(cause)
            }
        }
        failure?.let { throw it }
    }

    private fun readToken() {
        if (compressedBytesRemaining == 0L) {
            if (outputPosition != outputSize) {
                throw IOException("Truncated WAL compression payload")
            }
            finished = true
            return
        }

        val token = readCompressedByte()
        literalBytesRemaining = token ushr 4
        if (literalBytesRemaining == LZ77_LENGTH_NIBBLE_LIMIT) {
            literalBytesRemaining = readExtendedLength(literalBytesRemaining)
        }
        if (literalBytesRemaining.toLong() > outputSize - outputPosition) {
            throw IOException("WAL compression literals exceed output size")
        }

        matchLengthNibble = token and LZ77_LENGTH_NIBBLE_LIMIT
        phase = Lz77ReadPhase.LITERALS
    }

    private fun readLiterals(destination: ByteBuffer) {
        while (destination.hasRemaining() && literalBytesRemaining > 0) {
            writeOutputByte(destination, readCompressedByte())
            literalBytesRemaining--
        }
        if (literalBytesRemaining > 0) return

        if (compressedBytesRemaining == 0L) {
            if (outputPosition != outputSize) {
                throw IOException("WAL compression output size does not match frame")
            }
            finished = true
            return
        }

        if (compressedBytesRemaining < LZ77_MATCH_DISTANCE_SIZE) {
            throw IOException("Truncated WAL compression match distance")
        }
        matchDistance = readCompressedByte() or (readCompressedByte() shl 8)
        if (matchDistance !in 1..minOf(outputPosition, LZ77_MAX_DISTANCE.toLong()).toInt()) {
            throw IOException("Invalid WAL compression match distance")
        }

        matchBytesRemaining = matchLengthNibble + LZ77_MIN_MATCH
        if (matchLengthNibble == LZ77_LENGTH_NIBBLE_LIMIT) {
            matchBytesRemaining = readExtendedLength(matchBytesRemaining)
        }
        if (matchBytesRemaining.toLong() > outputSize - outputPosition) {
            throw IOException("WAL compression match exceeds output size")
        }
        phase = Lz77ReadPhase.MATCH
    }

    private fun readMatch(destination: ByteBuffer) {
        while (destination.hasRemaining() && matchBytesRemaining > 0) {
            val sourcePosition = outputPosition - matchDistance
            val value = history[(sourcePosition and LZ77_HISTORY_MASK.toLong()).toInt()].toInt() and 0xff
            writeOutputByte(destination, value)
            matchBytesRemaining--
        }
        if (matchBytesRemaining > 0) return

        if (compressedBytesRemaining == 0L) {
            throw IOException("Truncated WAL compression payload")
        }
        phase = Lz77ReadPhase.TOKEN
    }

    private fun writeOutputByte(destination: ByteBuffer, value: Int) {
        if (outputPosition >= outputSize) {
            throw IOException("WAL compression data exceeds output size")
        }
        val byte = value.toByte()
        destination.put(byte)
        history[(outputPosition and LZ77_HISTORY_MASK.toLong()).toInt()] = byte
        outputPosition++
    }

    private fun readExtendedLength(base: Int): Int {
        var length = base
        var next: Int
        do {
            next = readCompressedByte()
            if (length > Int.MAX_VALUE - next) {
                throw IOException("WAL compression length overflow")
            }
            length += next
        } while (next == 255)
        return length
    }

    private fun readCompressedByte(): Int {
        if (compressedBytesRemaining <= 0L) {
            throw IOException("Truncated WAL compression payload")
        }
        if (!compressedBuffer.hasRemaining()) {
            refillCompressedBuffer()
        }
        compressedBytesRemaining--
        return compressedBuffer.get().toInt() and 0xff
    }

    private fun refillCompressedBuffer() {
        compressedBuffer.clear()
        compressedBuffer.limit(minOf(compressedBuffer.capacity().toLong(), compressedBytesRemaining).toInt())
        var bytesRead: Int
        do {
            bytesRead = channel.read(compressedBuffer)
        } while (bytesRead == 0)
        if (bytesRead < 0) {
            throw IOException("Truncated WAL compression payload")
        }
        compressedBuffer.flip()
    }
}

private enum class Lz77ReadPhase {
    TOKEN,
    LITERALS,
    MATCH
}

private class WalReadBuffer(
    private val channel: ReadableByteChannel,
    private var unreadFileBytes: Long,
    bufferCapacity: Int
) : AutoCloseable {
    private val buffer = WAL_READ_BUFFERS.poll()?.apply {
        clear()
        order(ByteOrder.BIG_ENDIAN)
        limit(0)
    } ?: ByteBuffer.allocateDirect(bufferCapacity)
        .order(ByteOrder.BIG_ENDIAN)
        .apply { limit(0) }

    val capacity: Int
        get() = buffer.capacity()

    val byte: Byte
        get() = buffer.get()

    val int: Int
        get() = buffer.int

    val bytesRemaining: Long
        get() = buffer.remaining().toLong() + unreadFileBytes

    fun ensureAvailable(required: Int): Int {
        require(required in 0..capacity) { "Requested WAL bytes exceed the read buffer capacity" }

        while (buffer.remaining() < required && unreadFileBytes > 0) {
            buffer.compact()
            val originalLimit = buffer.limit()
            val requested = min(buffer.remaining().toLong(), unreadFileBytes).toInt()
            buffer.limit(buffer.position() + requested)
            val bytesRead = try {
                channel.read(buffer)
            } finally {
                buffer.limit(originalLimit)
            }
            buffer.flip()

            if (bytesRead <= 0) {
                if (bytesRead < 0) unreadFileBytes = 0
                break
            }
            unreadFileBytes -= bytesRead
        }

        return buffer.remaining()
    }

    fun readSlice(byteCount: Int): ByteBuffer? {
        if (ensureAvailable(byteCount) < byteCount) return null

        val sliceEnd = buffer.position() + byteCount
        val originalLimit = buffer.limit()
        buffer.limit(sliceEnd)
        return try {
            buffer.slice().order(ByteOrder.BIG_ENDIAN)
        } finally {
            buffer.position(sliceEnd)
            buffer.limit(originalLimit)
        }
    }

    fun readFully(destination: ByteBuffer): Int {
        var total = copyBufferedBytes(destination)
        while (destination.hasRemaining() && unreadFileBytes > 0) {
            val originalLimit = destination.limit()
            val requested = min(destination.remaining().toLong(), unreadFileBytes).toInt()
            destination.limit(destination.position() + requested)
            val bytesRead = try {
                channel.read(destination)
            } finally {
                destination.limit(originalLimit)
            }

            if (bytesRead <= 0) {
                if (bytesRead < 0) unreadFileBytes = 0
                break
            }
            unreadFileBytes -= bytesRead
            total += bytesRead
        }
        return total
    }

    fun isZeroFilled(): Boolean {
        for (index in buffer.position() until buffer.limit()) {
            if (buffer.get(index) != 0.toByte()) return false
        }
        return true
    }

    override fun close() {
        buffer.clear()
        buffer.limit(0)
        WAL_READ_BUFFERS.offer(buffer)
    }

    private fun copyBufferedBytes(destination: ByteBuffer): Int {
        val byteCount = min(buffer.remaining(), destination.remaining())
        if (byteCount == 0) return 0

        val originalLimit = buffer.limit()
        buffer.limit(buffer.position() + byteCount)
        destination.put(buffer)
        buffer.limit(originalLimit)
        return byteCount
    }

    private companion object {
        val WAL_READ_BUFFERS = ArrayBlockingQueue<ByteBuffer>(4)
    }
}

private const val LZ77_HEADER_MAGIC_SIZE = 4
private const val LZ77_HEADER_SIZE = 10
private const val LZ77_VERSION_OFFSET = 4
private const val LZ77_ENCODING_OFFSET = 5
private const val LZ77_VERSION = 1
private const val LZ77_RAW = 0
private const val LZ77_COMPRESSED = 1
private const val LZ77_LENGTH_NIBBLE_LIMIT = 15
private const val LZ77_MIN_MATCH = 4
private const val LZ77_MATCH_DISTANCE_SIZE = 2L
private const val LZ77_MAX_DISTANCE = 0xffff
private const val LZ77_HISTORY_SIZE = 1 shl 16
private const val LZ77_HISTORY_MASK = LZ77_HISTORY_SIZE - 1
private const val LZ77_INPUT_BUFFER_SIZE = 64 * 1024
private const val LZ77_DRAIN_BUFFER_SIZE = 16 * 1024

private fun ByteArray.lz77OutputSize(): Int {
    if (size < LZ77_HEADER_SIZE) {
        throw IOException("WAL compression header is incomplete")
    }
    val outputSize =
        (this[6].toInt() and 0xff) or
            ((this[7].toInt() and 0xff) shl 8) or
            ((this[8].toInt() and 0xff) shl 16) or
            ((this[9].toInt() and 0xff) shl 24)
    if (outputSize < 0) {
        throw IOException("WAL decompressed size is invalid")
    }
    return outputSize
}

/**
 * Helper method to apply a transaction log to an existing database
 */
fun PersistenceManager.recover(transactionLog: String) {
    this.context.transactionInteractor.recoverDatabase(transactionLog) { true }
}
