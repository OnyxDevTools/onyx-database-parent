@file:Suppress("UNCHECKED_CAST")

package com.onyx.interactors.transaction.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemPartitionEntry
import com.onyx.exception.TransactionException
import com.onyx.extension.common.metadata
import com.onyx.extension.withBuffer
import com.onyx.extension.common.openFileChannel
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
import java.nio.channels.FileChannel
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

        val filePaths = walDirectory.list()!!
        filePaths.sort()
        filePaths.forEach {
            applyTransactionLog(fromDirectoryPath + File.separator + it, executeTransaction)
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
     * @throws TransactionException If a transaction failed to execute, this will be thrown
     */
    @Throws(TransactionException::class)
    override fun applyTransactionLog(walTransactionFile: String, executeTransaction:  (Transaction) -> Boolean): Boolean {
        val channel = walTransactionFile.openFileChannel()
        if (channel == null || !channel.isOpen) {
            throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_READ_FILE)
        }

        var transaction: Transaction? = null
        try {
            channel.position(0)
            val logSize = channel.size()
            WalReadBuffer(channel, logSize, WAL_READ_BUFFER_SIZE).use { wal ->
                while (true) {
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
                            throw IllegalStateException("WAL transaction header is incomplete")
                        }

                        val transactionType = wal.byte
                        val transactionDataLength = wal.int

                        if (transactionType == PADDING && transactionDataLength == 0) {
                            break
                        }
                        if (transactionType !in TRANSACTION_TYPES || transactionDataLength <= 0) {
                            throw IllegalStateException("WAL transaction header is invalid")
                        }
                        if (transactionDataLength.toLong() > wal.bytesRemaining) {
                            throw IllegalStateException("WAL transaction data is incomplete")
                        }

                        val pooledTransactionBuffer = transactionDataLength > wal.capacity
                        val transactionBuffer = if (pooledTransactionBuffer) {
                            BufferPool.allocateAndLimit(transactionDataLength)
                        } else {
                            wal.readSlice(transactionDataLength)
                                ?: throw IllegalStateException("WAL transaction data is incomplete")
                        }

                        try {
                            if (pooledTransactionBuffer) {
                                val transactionBytesRead = wal.readFully(transactionBuffer)
                                if (transactionBytesRead < transactionDataLength) {
                                    throw IllegalStateException("WAL transaction data is incomplete")
                                }
                                transactionBuffer.flip()
                            }

                            when (transactionType) {
                                SAVE -> {
                                    val value = BufferStream.fromBuffer(transactionBuffer, persistenceManager.context) as Map<String, Any?>
                                    val className = value["type"] as? String
                                    if (className != null && !className.contains("SystemPartitionEntry")) {
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
                                    if (className != null) {
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
                    } catch (cause: TransactionException) {
                        println("Failure to apply transaction")
                    } catch (cause: Exception) {
                        println("Failure to apply transaction")
                    }
                }
            }
        } catch (_: IOException) {
            throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_READ_FILE)
        } finally {
            try {
                channel.close()
            } catch (_: IOException) {
            }
        }

        return true
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

private class WalReadBuffer(
    private val channel: FileChannel,
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

/**
 * Helper method to apply a transaction log to an existing database
 */
fun PersistenceManager.recover(transactionLog: String) {
    this.context.transactionInteractor.recoverDatabase(transactionLog) { true }
}
