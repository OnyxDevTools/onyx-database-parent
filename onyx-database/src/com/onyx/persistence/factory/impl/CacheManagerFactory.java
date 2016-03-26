package com.onyx.persistence.factory.impl;

import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.context.impl.CacheSchemaContext;

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
    public CacheManagerFactory()
    {
        this(DEFAULT_INSTANCE);
    }

    /**
     * Default Constructor with instance name
     */
    public CacheManagerFactory(String instance)
    {
        this.instance = instance;
        this.setSchemaContext(new CacheSchemaContext(instance));

        File tempDirectory = null;
        try {
            tempDirectory = File.createTempFile("temp", Long.toString(System.nanoTime()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.setDatabaseLocation(tempDirectory.toPath().toString());
    }

    /**
     * Initialize the in memory database
     *
     * @throws InitializationException Only one instance of the in memory database can be instantiated per process
     */
    @Override
    public void initialize() throws InitializationException
    {
        context.start();
    }
}
