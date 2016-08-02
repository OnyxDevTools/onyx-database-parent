package com.onyx.persistence.context.impl;

import com.onyx.exception.SingletonException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Schema context that defines remote resources for a database.  Use this when connecting to a remote database not to be confused with a Web REST API Database.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *
 * PersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
 * fac.setDatabaseLocation("onx://52.13.31.322:8080");
 * fac.setCredentials("username", "password");
 * fac.initialize();
 *
 * PersistenceManager manager = fac.getPersistenceManager();
 *
 * fac.close();
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.context.SchemaContext
 */
public class RemoteSchemaContext extends DefaultSchemaContext implements SchemaContext
{
    private String remoteEndpoint;

    protected PersistenceManager defaultRemotePersistenceManager = null;

    protected PersistenceManager socketPersistenceManager = null;

    /**
     * Default Constructor
     * @since 1.0.0
     */
    public RemoteSchemaContext(String contextId)
    {
        super(contextId);
        try
        {
            location = Files.createTempDirectory("onx").toString();
        }
        catch (IOException ignore){}
    }

    /**
     * Setter for default persistence manager
     *
     * This is not meant to be a public API.  This is called within the persistence manager factory.  It is used to access system data.
     *
     * @since 1.0.0
     * @param defaultPersistenceManager Default Persistence Manager used to access system level entities
     */
    public void setSystemPersistenceManager(PersistenceManager defaultPersistenceManager)
    {
        this.systemPersistenceManager = defaultPersistenceManager;
    }

    /**
     * Set Default Remote Persistence Manager
     * This is not meant to be a public API and is set by the factory
     * @since 1.0.0
     * @param defaultPersistenceManager Default Persistence manager for System Entities
     */
    public void setDefaultRemotePersistenceManager(PersistenceManager defaultPersistenceManager)
    {
        this.defaultRemotePersistenceManager = defaultPersistenceManager;
    }

    /**
     * Override to use the remote persistence manager.  This is used by
     *
     * @see com.onyx.persistence.collections.LazyRelationshipCollection
     * @see com.onyx.persistence.collections.LazyQueryCollection
     *
     * @since 1.0.0
     * @return System entity persistence manager
     */
    @Override
    public PersistenceManager getSystemPersistenceManager()
    {
        // NOTE: THIS USES THE DEFAULT ON PURPOSE.  So that it talks to the remote server rather than system
        return defaultRemotePersistenceManager;
    }

    /**
     * Set Default Socket Persistence Manager
     * This is not meant to be a public API and is set by the factory.  This is used when de-serializing the Lazy Collections
     * so that the lazy collection can use it to hydrate items within it.
     * @since 1.0.0
     * @param socketPersistenceManager Default Persistence manager for System Entities
     */
    public void setSocketPersistenceManager(PersistenceManager socketPersistenceManager)
    {
        this.socketPersistenceManager = socketPersistenceManager;
    }

    /**
     * Used in order to keep fluidity within the following classes on api calls.
     * If one of the collections were instantiated using the RMI socket externalizing.
     * This is used by the externalize to keep consistency on how the collection was initiated.
     *
     * @see com.onyx.persistence.collections.LazyRelationshipCollection
     * @see com.onyx.persistence.collections.LazyQueryCollection
     *
     * @since 1.0.0
     * @return Socket entity persistence manager
     */
    public PersistenceManager getSocketPersistenceManager()
    {
        return socketPersistenceManager;
    }

    /**
     * Start the context and initiate the local cache information
     * @since 1.0.0
     */
    public void start()
    {
        if(location == null)
        {
            try
            {
                location = Files.createTempDirectory("onyx.oxd").toString();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        super.start();
    }

    /**
     * Close the local database cache store
     * @since 1.0.0
     */
    public void shutdown() throws SingletonException
    {
        super.shutdown();
    }

    /**
     * Get Remote Endpoint
     * @since 1.0.0
     * @return Remote Endpoint URI String
     */
    public String getRemoteEndpoint()
    {
        return remoteEndpoint;
    }

    /**
     * Set Remote Endpoint.  This should be done by the database connection factory.  Also it should be formatted onx://...
     * @since 1.0.0
     * @param remoteEndpoint Remote Endpoint
     */
    public void setRemoteEndpoint(String remoteEndpoint)
    {
        this.remoteEndpoint = remoteEndpoint;
    }

    /**
     * Get Remote File Base
     * @since 1.0.0
     * @return Remote File Location
     */
    public String getRemoteFileBase()
    {
        return location.replaceFirst("ws://", "http://");
    }
}
