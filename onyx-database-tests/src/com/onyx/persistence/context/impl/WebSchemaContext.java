package com.onyx.persistence.context.impl;

import com.onyx.exception.SingletonException;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.diskmap.DefaultMapBuilder;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.serializer.Serializers;
import com.onyx.diskmap.store.StoreType;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Web Schema Context is used when connecting to a RESTful web service database.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *
 * PersistenceManagerFactory fac = new WebPersistenceManagerFactory();
 * fac.setDatabaseLocation("http://52.13.31.322:8080");
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
 * @see SchemaContext
 */
public class WebSchemaContext extends DefaultSchemaContext implements SchemaContext
{
    private MapBuilder metadataMapBuilder = null;

    private Serializers serializers = null;

    private String remoteEndpoint;

    /**
     * Default Constructor
     */
    public WebSchemaContext(String contextId)
    {
        super(contextId);
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
     * Start the context
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
            metadataMapBuilder = new DefaultMapBuilder(location + "/local", StoreType.MEMORY_MAPPED_FILE, this);
            serializers = metadataMapBuilder.getSerializers();
        }
        super.start();
    }

    /**
     * Close the local database cache store
     * @since 1.0.0
     */
    public void shutdown() throws SingletonException
    {
        if(metadataMapBuilder != null)
        {
            metadataMapBuilder.close();
        }
        super.shutdown();
    }

    /**
     * Get Remote Endpoint
     * @since 1.0.0
     * @return Remote Endpoint URL
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
}
