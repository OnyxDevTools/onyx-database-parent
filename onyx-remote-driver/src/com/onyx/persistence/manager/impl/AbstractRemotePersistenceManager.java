package com.onyx.persistence.manager.impl;

import com.onyx.client.DefaultDatabaseEndpoint;
import com.onyx.persistence.context.impl.RemoteSchemaContext;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.util.ObjectUtil;


/**
 * Base Methods for communicating to an Onyx Remote Database
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
public abstract class AbstractRemotePersistenceManager implements PersistenceManager {

    public static final ObjectUtil objectUtil = ObjectUtil.getInstance();

    // Schema Context
    protected RemoteSchemaContext context = null;

    public SchemaContext getContext()
    {
        return context;
    }

    public void setContext(SchemaContext context)
    {
        this.context = (RemoteSchemaContext) context;
    }

    public AbstractRemotePersistenceManager(SchemaContext context)
    {
        this.context = (RemoteSchemaContext)context;
    }

    // Persistence Manager Factory
    protected RemotePersistenceManagerFactory factory = null;

    public PersistenceManagerFactory getFactory()
    {
        return factory;
    }

    public void setFactory(PersistenceManagerFactory factory)
    {
        this.factory = (RemotePersistenceManagerFactory) factory;
    }

    protected DefaultDatabaseEndpoint endpoint = null;

    public void setDatabaseEndpoint(DefaultDatabaseEndpoint endpoint)
    {
        this.endpoint = endpoint;
    }


}
