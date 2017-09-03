package com.onyx.persistence.factory.impl;

import com.onyx.exception.InitializationException;
import com.onyx.persistence.context.impl.ServerSchemaContext;


/**
 * Created by tosborn1 on 3/27/17.
 *
 * Persistence manager factory for a server based database.
 *
 * This is a server persistence manager factory.  It is overridden so that the context
 * type is also a server schema context.  It differes due to the query caching policies.
 *
 * @author Tim Osborn
 * @since 1.3.0 Introduced as work done on query subscribers.
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new ServerPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("/MyDatabaseLocation")
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the in embedded database
 *
 *   or ...
 *
 *   PersistenceManagerFactory factory = new ServerPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("/MyDatabaseLocation")
 *   factory.setContext(new ServerSchemaContext());
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
public class ServerPersistenceManagerFactory extends EmbeddedPersistenceManagerFactory {

    public ServerPersistenceManagerFactory(String location, String instance) {
        super(location, instance);
    }

    @SuppressWarnings("unused")
    public ServerPersistenceManagerFactory(String location) {
        this(location, location);
    }

    /**
     * Initialize the database connection and storage mechanisms
     *
     * @since 1.0.0
     * @throws InitializationException Failure to start database due to either invalid credentials or a lock on the database already exists.
     */
    @Override
    public void initialize() throws InitializationException
    {
        if(context == null)
        {
            context = new ServerSchemaContext(location);
            context.setLocation(location);
        }

        super.initialize();
    }
}
