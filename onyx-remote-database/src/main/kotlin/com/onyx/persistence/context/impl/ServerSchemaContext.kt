package com.onyx.persistence.context.impl

import com.onyx.network.push.PushPublisher
import com.onyx.interactors.cache.QueryCacheInteractor
import com.onyx.interactors.query.impl.RemoteQueryCacheInteractor

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * Schema context that defines local stores for data storage and partitioning. This can only be accessed by a single process. Databases must
 * not have multiple process accessed at the same time.
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.context.SchemaContext
 *
 * @since 1.3.0
 *
 *
 * PersistenceManagerFactory fac = new ServerPersistenceManagerFactory("/MyDatabaseLocation");
 * fac.setCredentials("username", "password");
 * fac.initialize();
 *
 * PersistenceManager manager = fac.getPersistenceManager();
 *
 * fac.close();
 *
 */
open class ServerSchemaContext(contextId: String, location: String) : DefaultSchemaContext(contextId, location) {

    override var queryCacheInteractor:QueryCacheInteractor = RemoteQueryCacheInteractor(this)

    /**
     * Set Push publisher.  The push publisher is used to push notifications down
     * to clients.  Push notifications are not initialized by the client.  instead
     * they are send independently from the server and do not verify receipt.
     *
     * Push notifications are used by the query subscriber / cache to send query changes
     * to clients.
     *
     * @param pushPublisher Object responsible for sending push notifications
     *
     * @since 1.3.0
     */
    fun setPushPublisher(pushPublisher: PushPublisher) {
        (this.queryCacheInteractor as RemoteQueryCacheInteractor).pushPublisher = pushPublisher
    }
}
