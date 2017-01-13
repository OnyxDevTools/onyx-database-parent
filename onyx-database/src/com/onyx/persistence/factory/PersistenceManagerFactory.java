package com.onyx.persistence.factory;

import com.onyx.exception.InitializationException;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;

/**
 * Persistence manager factory configures the database and encapsulates the instantiation of the Persistence Manager
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("/MyDatabaseLocation")
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the in memory database
 *
 *   or ...
 *
 *   PersistenceManagerFactory factory = new CacheManagerFactory();
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the in memory database
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.factory.impl.CacheManagerFactory
 * @see com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
 *
 */
public interface PersistenceManagerFactory
{

    /**
     * Set Database Location.  A directory on local file system or remote endpoint
     *
     * @since 1.0.0
     * @param location Database Location
     */
    void setDatabaseLocation(String location);

    /**
     * Get Database Location
     *
     * @since 1.0.0
     * @return Local database location on disk
     */
    String getDatabaseLocation();

    /**
     * Set Schema Context.  Schema context determines how the data is structured and what mechanism for data storage is used
     * If this is not specified, it will instantiate a corresponding schema context
     *
     * @since 1.0.0
     *
     * @see com.onyx.persistence.context.impl.DefaultSchemaContext
     *
     * @param context Schema Context implementation
     */
    void setSchemaContext(SchemaContext context);

    /**
     * Get Schema Context
     *
     * @since 1.0.0
     * @return Schema Context
     */

    SchemaContext getSchemaContext();

    /**
     * Initialize the database connection and storage mechanisms
     *
     * @since 1.0.0
     * @throws InitializationException Failure to start database due to either invalid credentials or a lock on the database already exists.
     */
    void initialize() throws InitializationException;

    /**
     * Safe shutdown of database
     * @since 1.0.0
     * @throws java.io.IOException Failure to write checksum to database storage
     * @throws com.onyx.exception.SingletonException Accessing singletons that cannot have more than one instance
     */
    void close();

    /**
     * Set Credentials. Set username and password
     *
     * @since 1.0.0
     * @param user Set username
     * @param password Set Password
     */
    void setCredentials(String user, String password);

    /**
     * Get Credentials formatted for HTTP Basic Authentication to be inserted into Cookie
     * @since 1.0.0
     *
     * @return Formatted Credentials
     */
    String getCredentials();

    /**
     * Getter for persistence manager
     *
     * @since 1.0.0
     * @return Instantiated Persistence Manager
     *
     * @see com.onyx.persistence.manager.PersistenceManager
     * @see com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
     */
    PersistenceManager getPersistenceManager();
}
