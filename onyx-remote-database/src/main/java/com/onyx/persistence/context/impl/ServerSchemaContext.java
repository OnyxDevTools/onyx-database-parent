package com.onyx.persistence.context.impl;

import com.onyx.client.push.PushPublisher;
import com.onyx.persistence.query.impl.DefaultQueryCacheController;
import com.onyx.persistence.query.impl.RemoteQueryCacheController;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * Schema context that defines local stores for data storage and partitioning. This can only be accessed by a single process. Databases must
 * not have multiple process accessed at the same time.
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.context.SchemaContext
 * @since 1.3.0
 * <p>
 * <pre>
 * <code>
 *
 *
 * PersistenceManagerFactory fac = new ServerPersistenceManagerFactory();
 * fac.setDatabaseLocation("/MyDatabaseLocation");
 * fac.setSchemaContext(new ServerSchemaContext()); //Define Server Schema Context
 * fac.setCredentials("username", "password");
 * fac.initialize();
 *
 * PersistenceManager manager = fac.getPersistenceManager();
 *
 * fac.close();
 *
 * </code>
 * </pre>
 */
public class ServerSchemaContext extends DefaultSchemaContext {

    /**
     * Constructor.
     *
     * @param contextId Database identifier that must be unique and tied to its process
     */
    public ServerSchemaContext(String contextId) {
        super(contextId);
        this.queryCacheController = new RemoteQueryCacheController(this);
    }

    /**
     * The start method will redefine the query caching mechinism after already started.
     * The reason for that is because the initiialization of the system entities will utilize
     * the default query cacher.
     *
     * @since 1.3.0
     */
    public void start() {
        super.start();
        this.queryCacheController = new RemoteQueryCacheController(this);
    }

    /**
     * Set Push publisher.  The push publisher is used to push notifications down
     * to clients.  Push notifications are not iniatialized by the client.  instead
     * they are send independently from the server and do not verify receipt.
     *
     * Push notifications are used by the query subscriber / cacher to send query changes
     * to clients.
     *
     * @param pushPublisher Object responsible for sending push notifications
     *
     * @since 1.3.0
     */
    public void setPushPublisher(PushPublisher pushPublisher) {
        ((RemoteQueryCacheController)this.queryCacheController).setPushPublisher(pushPublisher);
    }
}
