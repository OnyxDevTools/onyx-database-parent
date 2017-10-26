package com.onyx.interactors.transaction

import com.onyx.exception.TransactionException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.interactors.transaction.data.Transaction

/**
 * Created by Tim Osborn on 3/25/16.
 *
 * The purpose of this class is to write a WAL transaction to keep a history of all transactions
 */
interface TransactionInteractor {

    /**
     * Write a save transaction to a WAL file
     *
     * @param entity Entity to save
     */
    @Throws(TransactionException::class)
    fun writeSave(entity: IManagedEntity)

    /**
     * Write a query update to the WAL transaction
     *
     * @param query Query to update
     */
    @Throws(TransactionException::class)
    fun writeQueryUpdate(query: Query)

    /**
     * Write a Delete transaction to a WAL File
     *
     * @param entity Deleted entity
     */
    @Throws(TransactionException::class)
    fun writeDelete(entity: IManagedEntity)

    /**
     * Write a delete query to a WAL file
     * @param query Query to write to WAL
     */
    @Throws(TransactionException::class)
    fun writeDeleteQuery(query: Query)

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
    fun recoverDatabase(fromDirectoryPath: String, executeTransaction:  (Transaction) -> Boolean)


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
    fun applyTransactionLog(walTransactionFile: String, executeTransaction: (Transaction) -> Boolean): Boolean

}
