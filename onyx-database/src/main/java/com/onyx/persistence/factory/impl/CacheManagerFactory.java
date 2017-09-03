package com.onyx.persistence.factory.impl;

import com.onyx.exception.InitializationException;
import com.onyx.persistence.context.impl.CacheSchemaContext;
import com.onyx.persistence.factory.PersistenceManagerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Persistence manager factory for an in memory database.  This utilizes off heap memory buffers.  This is responsible for configuring a database that does not persist to disk.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   CacheManagerFactory factory = new CacheManagerFactory();
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the in memory database
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.factory.PersistenceManagerFactory
 */
public class CacheManagerFactory extends EmbeddedPersistenceManagerFactory implements PersistenceManagerFactory
{

    /**
     * Default Constructor
     */
    @SuppressWarnings("unused")
    public CacheManagerFactory()
    {
        this(DEFAULT_INSTANCE);
    }

    /**
     * Default Constructor with instance name
     */
    @SuppressWarnings("WeakerAccess")
    public CacheManagerFactory(String instance)
    {
        super(tempDatabaseLocation(), instance);
        this.instance = instance;
        this.setSchemaContext(new CacheSchemaContext(instance));
    }

    static String tempDatabaseLocation() {
        File tempDirectory = null;
        try {
            tempDirectory = File.createTempFile("temp", Long.toString(System.nanoTime()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return tempDirectory.getPath();
    }
    /**
     * Initialize the in memory database
     *
     * @throws InitializationException Only one instance of the in memory database can be instantiated per process
     */
    @Override
    public void initialize() throws InitializationException
    {
        this.getPersistenceManager();
        context.start();
    }
}
