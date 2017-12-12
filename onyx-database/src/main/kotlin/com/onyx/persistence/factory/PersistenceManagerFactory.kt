package com.onyx.persistence.factory

import com.onyx.diskmap.store.StoreType
import com.onyx.exception.InitializationException
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.interactors.encryption.EncryptionInteractor

/**
 * Persistence manager factory configures the database and encapsulates the instantiation of the Persistence Manager
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 *
 * PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 * factory.setCredentials("username", "password");
 * factory.setLocation("/MyDatabaseLocation")
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in memory database
 *
 * or ...
 *
 * PersistenceManagerFactory factory = new CacheManagerFactory();
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in memory database
 *
 *
 * @see com.onyx.persistence.factory.impl.CacheManagerFactory
 * @see com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
 */
interface PersistenceManagerFactory {

    /**
     * Get Credentials formatted for HTTP Basic Authentication to be inserted into Cookie
     * @since 1.0.0
     *
     * @return Formatted Credentials
     */
    val credentials: String

    /**
     * Persistence Manager handles the data access for Database
     *
     * @since 1.0.0
     *
     * @see com.onyx.persistence.manager.PersistenceManager
     * @see com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
     */
    val persistenceManager: PersistenceManager

    /**
     * A directory on local file system or remote endpoint
     *
     * @since 1.0.0
     */
    val databaseLocation: String

    /**
     * Schema context determines how the data is structured and what mechanism for data storage is used
     * If this is not specified, it will instantiate a corresponding schema context
     *
     * @since 1.0.0
     *
     * @see com.onyx.persistence.context.impl.DefaultSchemaContext
     *
     */
    var schemaContext: SchemaContext

    /**
     * Specify encryption Interactor.  By default this will select the DefaultEncryptionInteractor instance.
     * In order to make your database secure, override the instance of encryption interactor
     *
     * @since 2.0.0
     *
     */
    var encryption: EncryptionInteractor

    /**
     * Store type.  This determines if the store should use either memory mapped file or nio file
     * Note: Using Memory mapped files could be volatile.  If the safe keeping of the data is high priority,
     * I recommend using StoreType.FILE
     *
     * @since 2.0.0
     */
    var storeType: StoreType

    /**
     * Initialize the database connection and storage mechanisms
     *
     * @since 1.0.0
     * @throws InitializationException Failure to start database due to either invalid credentials or a lock on the database already exists.
     */
    @Throws(InitializationException::class)
    fun initialize()

    /**
     * Safe shutdown of database
     * @since 1.0.0
     */
    fun close()

    /**
     * Set Credentials. Set username and password
     *
     * @since 1.0.0
     * @param user Set username
     * @param password Set Password
     */
    fun setCredentials(user: String, password: String)
}
