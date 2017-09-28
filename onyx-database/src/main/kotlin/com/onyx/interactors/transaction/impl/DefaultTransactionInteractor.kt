package com.onyx.interactors.transaction.impl

import com.onyx.buffer.BufferStream
import com.onyx.exception.TransactionException
import com.onyx.extension.withBuffer
import com.onyx.extension.common.openFileChannel
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

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Handles logging of a transaction
 */
class DefaultTransactionInteractor(private val transactionStore: TransactionStore, private val persistenceManager: PersistenceManager) : TransactionInteractor {

    private fun writeTransaction(transactionType:Byte, buffer: ByteBuffer) {

        val file = transactionStore.getTransactionFile()
        val totalBuffer:ByteBuffer?

        try {
            totalBuffer = BufferStream.allocateAndLimit(buffer.limit() + 5)
            withBuffer(totalBuffer) {
                it.put(transactionType)
                it.putInt(buffer.limit())
                it.put(buffer)
                it.flip()
                file.write(it)
            }
        } catch (e: Exception) {
            throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE)
        } finally {
            BufferStream.recycle(buffer)
        }
    }

    /**
     * Write a save transaction to a WAL file
     *
     * @param entity Entity to save
     */
    @Throws(TransactionException::class)
    override fun writeSave(entity: IManagedEntity) = synchronized(this) {
        writeTransaction(SAVE, BufferStream.toBuffer(entity, persistenceManager.context))
    }

    /**
     * Write a query update to the WAL transaction
     *
     * @param query Query to update
     */
    @Throws(TransactionException::class)
    override fun writeQueryUpdate(query: Query) = synchronized(this) {
        writeTransaction(UPDATE_QUERY, BufferStream.toBuffer(query, persistenceManager.context))
    }

    /**
     * Write a Delete transaction to a WAL File
     *
     * @param entity Deleted entity
     */
    @Throws(TransactionException::class)
    override fun writeDelete(entity: IManagedEntity) = synchronized(this) {
        writeTransaction(DELETE, BufferStream.toBuffer(entity, persistenceManager.context))
    }

    /**
     * Write a delete query to a WAL file
     * @param query Query to write transaction of
     */
    @Throws(TransactionException::class)
    override fun writeDeleteQuery(query: Query) = synchronized(this) {
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
        val metadataBuffer = BufferStream.allocateAndLimit(5)

        withBuffer(metadataBuffer) {
            try {
                channel.position(0)
                while (channel.position() < channel.size()) {

                    try {
                        channel.read(metadataBuffer)
                        metadataBuffer.flip()

                        val transactionType = metadataBuffer.get()
                        val transactionDataLength = metadataBuffer.int

                        val tBuffer = BufferStream.allocateAndLimit(transactionDataLength)
                        withBuffer(tBuffer) { transactionBuffer ->
                            channel.read(transactionBuffer)
                            transactionBuffer.rewind()

                            when (transactionType) {
                                SAVE -> {
                                    val entity = BufferStream.fromBuffer(transactionBuffer, persistenceManager.context) as IManagedEntity
                                    transaction = SaveTransaction(entity)
                                    if (executeTransaction.invoke(transaction!!)) {
                                        (entity as ManagedEntity).ignoreListeners = true
                                        this.persistenceManager.saveEntity<IManagedEntity>(entity)
                                        entity.ignoreListeners = false
                                    }
                                }
                                DELETE -> {
                                    val entity = BufferStream.fromBuffer(transactionBuffer, persistenceManager.context) as IManagedEntity
                                    transaction = DeleteTransaction(entity)
                                    if (executeTransaction.invoke(transaction!!)) {
                                        (entity as ManagedEntity).ignoreListeners = true
                                        this.persistenceManager.deleteEntity(entity)
                                        entity.ignoreListeners = false
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

                            transactionBuffer.clear()
                        }
                        metadataBuffer.flip()
                    } catch (cause: Exception) {
                        throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_EXECUTE, transaction!!, cause)
                    }
                }
            } catch (e: IOException) {
                throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_READ_FILE)
            }
        }

        return true
    }

    companion object {
        private val SAVE: Byte = 1
        private val DELETE: Byte = 2
        private val DELETE_QUERY: Byte = 3
        private val UPDATE_QUERY: Byte = 4
    }
}
