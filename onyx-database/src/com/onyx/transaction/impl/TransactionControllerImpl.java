package com.onyx.transaction.impl;

import com.onyx.buffer.BufferStream;
import com.onyx.exception.TransactionException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.transaction.*;
import com.onyx.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Created by tosborn1 on 3/25/16.
 */
public class TransactionControllerImpl implements TransactionController
{

    protected static final byte SAVE = 1;
    protected static final byte DELETE = 2;
    protected static final byte DELETE_QUERY = 3;
    protected static final byte UPDATE_QUERY = 4;

    private final String contextId;
    private final PersistenceManager persistenceManager;

    protected ReentrantLock transactionLock = new ReentrantLock(true);

    /**
     * Constructor with schema Context
     */
    public TransactionControllerImpl(SchemaContext context, PersistenceManager persistenceManager)
    {
        this.contextId = context.getContextId();
        this.persistenceManager = persistenceManager;
    }

    /**
     * Write a save transaction to a WAL file
     *
     * @param entity Entity to save
     */
    public void writeSave(IManagedEntity entity) throws TransactionException
    {
        final FileChannel file = getContext().getTransactionFile();

        transactionLock.lock();
        try {
            final ByteBuffer buffer = BufferStream.toBuffer(entity);
            final ByteBuffer totalBuffer = ObjectBuffer.allocate(buffer.limit() + 5);
            totalBuffer.put(SAVE);
            totalBuffer.putInt(buffer.limit());
            totalBuffer.put(buffer);
            totalBuffer.rewind();

            file.write(totalBuffer);
        } catch (Exception e) {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE);
        }
        finally {
            transactionLock.unlock();
        }
    }

    /**
     * Write a query update to the WAL transaction
     *
     * @param query Query to update
     */
    public void writeQueryUpdate(Query query) throws TransactionException
    {

        final FileChannel file = getContext().getTransactionFile();

        transactionLock.lock();
        try {
            final ByteBuffer buffer = BufferStream.toBuffer(query);
            final ByteBuffer totalBuffer = ObjectBuffer.allocate(buffer.limit() + 5);
            totalBuffer.put(UPDATE_QUERY);
            totalBuffer.putInt(buffer.limit());
            totalBuffer.put(buffer);
            totalBuffer.rewind();

            file.write(totalBuffer);
        } catch (Exception e) {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE);
        }
        finally {
            transactionLock.unlock();
        }
    }

    /**
     * Write a Delete transaction to a WAL File
     *
     * @param entity Deleted entity
     */
    public void writeDelete(IManagedEntity entity) throws TransactionException
    {

        final FileChannel file = getContext().getTransactionFile();

        transactionLock.lock();
        try {
            final ByteBuffer buffer = BufferStream.toBuffer(entity);
            final ByteBuffer totalBuffer = ObjectBuffer.allocate(buffer.limit() + 5);
            totalBuffer.put(DELETE);
            totalBuffer.putInt(buffer.limit());
            totalBuffer.put(buffer);
            totalBuffer.rewind();

            file.write(totalBuffer);
        } catch (Exception e) {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE);
        }
        finally {
            transactionLock.unlock();
        }
    }

    /**
     * Write a delete query to a WAL file
     * @param query
     */
    public void writeDeleteQuery(Query query) throws TransactionException
    {
        final FileChannel file = getContext().getTransactionFile();

        transactionLock.lock();

        try {
            final ByteBuffer buffer = BufferStream.toBuffer(query);
            final ByteBuffer totalBuffer = ObjectBuffer.allocate(buffer.limit() + 5);
            totalBuffer.put(DELETE_QUERY);
            totalBuffer.putInt(buffer.limit());
            totalBuffer.put(buffer);
            totalBuffer.rewind();

            file.write(totalBuffer);
        } catch (Exception e) {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE);
        }
        finally {
            transactionLock.unlock();
        }
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
    public void recoverDatabase(String fromDirectoryPath, Function<Transaction, Boolean> executeTransaction) throws TransactionException
    {
        File walDirectory = new File(fromDirectoryPath);
        if(!walDirectory.exists() || !walDirectory.isDirectory())
        {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_RECOVER_FROM_DIRECTORY);
        }

        String[] filePaths = walDirectory.list();
        Arrays.sort(filePaths);

        String transactionFilePath = null;
        for(int i = 0; i < filePaths.length; i++)
        {
            transactionFilePath = filePaths[i];
            try {
                applyTransactionLog(fromDirectoryPath + File.separator + transactionFilePath, executeTransaction);
            }
            catch (TransactionException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Roll Database Forward an entire transaction log.
     *
     * Sometimes it may be necessary to take an entire list of transactions and apply them to an entire database.
     * An example usage would be if you had replication and experienced a network outage.  In that case in order to synchronize, you
     * could utilize this method.
     *
     * @param walTransactionFilePath File that contains transaction log.
     * @param executeTransaction Function that determines whether or not you should execute the transaction
     * @throws TransactionException If a transaction failed to execute, this will be thrown
     */
    public boolean applyTransactionLog(String walTransactionFilePath, Function<Transaction, Boolean> executeTransaction) throws TransactionException
    {
        final FileChannel channel = FileUtil.openFileChannel(walTransactionFilePath);

        if(channel == null || !channel.isOpen())
        {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_READ_FILE);
        }

        ByteBuffer metadataBuffer = ObjectBuffer.allocate(5);
        Transaction transaction = null;

        try {
            channel.position(0);
            while(channel.position() < channel.size()) {

                try {
                    channel.read(metadataBuffer);
                    metadataBuffer.rewind();

                    byte transactionType = metadataBuffer.get();
                    int transactionDataLength = metadataBuffer.getInt();

                    final ByteBuffer transactionBuffer = BufferStream.allocate(transactionDataLength);
                    channel.read(transactionBuffer);
                    transactionBuffer.rewind();

                    if (transactionType == SAVE) {
                        IManagedEntity entity = (IManagedEntity) BufferStream.fromBuffer(transactionBuffer);
                        transaction = new SaveTransaction(entity);
                        if(executeTransaction.apply(transaction) == true)
                        {
                            ((ManagedEntity)entity).ignoreListeners = true;
                            this.persistenceManager.saveEntity(entity);
                            ((ManagedEntity)entity).ignoreListeners = false;
                        }
                    } else if (transactionType == DELETE) {
                        IManagedEntity entity = (IManagedEntity) BufferStream.fromBuffer(transactionBuffer);
                        transaction = new DeleteTransaction(entity);
                        if(executeTransaction.apply(transaction) == true)
                        {
                            ((ManagedEntity)entity).ignoreListeners = true;
                            this.persistenceManager.deleteEntity(entity);
                            ((ManagedEntity)entity).ignoreListeners = false;
                        }
                    } else if (transactionType == UPDATE_QUERY) {
                        Query query = (Query) BufferStream.fromBuffer(transactionBuffer);
                        transaction = new UpdateQueryTransaction(query);
                        if(executeTransaction.apply(transaction) == true)
                        {
                            this.persistenceManager.executeUpdate(query);
                        }
                    } else if (transactionType == DELETE_QUERY) {
                        Query query = (Query) BufferStream.fromBuffer(transactionBuffer);
                        transaction = new DeleteQueryTransaction(query);
                        if(executeTransaction.apply(transaction) == true)
                        {
                            this.persistenceManager.executeDelete(query);
                        }
                    }

                    BufferStream.recycle(transactionBuffer);
                    metadataBuffer.rewind();
                    transactionBuffer.clear();
                }
                catch (Exception cause)
                {
                    throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_EXECUTE, transaction, cause);
                }
            }
        } catch (IOException e) {
            throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_READ_FILE);
        }

        return true;
    }

    protected SchemaContext getContext()
    {
        return DefaultSchemaContext.registeredSchemaContexts.get(contextId);
    }
}
