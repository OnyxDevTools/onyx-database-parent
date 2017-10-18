package com.onyx.interactors.query.impl

import com.onyx.client.push.PushPublisher
import com.onyx.client.push.PushSubscriber
import com.onyx.interactors.cache.impl.DefaultQueryCacheInteractor
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.interactors.cache.data.CachedResults
import com.onyx.persistence.query.QueryListener
import com.onyx.persistence.query.impl.RemoteQueryListener

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This class is responsible for controlling the query cache for a remote server.
 */
class RemoteQueryCacheInteractor(context: SchemaContext) : DefaultQueryCacheInteractor(context) {

    var pushPublisher: PushPublisher? = null

    /**
     * This method is used to subscribe irrespective of a query being ran.
     * Overridden in order to associate the remote query listener as the
     * push subscriber information will be set.
     *
     * @param query Query object with defined listener
     *
     * @since 1.3.1
     */
    override fun subscribe(query: Query) {
        val remoteQueryListener = this.pushPublisher!!.getRegisteredSubscriberIdentity(query.changeListener as PushSubscriber?) as RemoteQueryListener<*>
        query.changeListener = remoteQueryListener
        super.subscribe(query)
    }

    /**
     * Subscribe a query listener with associated cached results. This is overridden so that we can get the true identity
     * of the push subscriber through the publisher.  The publisher keeps track of all registered subscribers.
     *
     * @param results       Results to listen to
     * @param queryListener listner to respond to cache changes
     * @since 1.3.0
     */
    override fun subscribe(results: CachedResults, queryListener: QueryListener<*>) {
        val remoteQueryListener = this.pushPublisher!!.getRegisteredSubscriberIdentity(queryListener as PushSubscriber) as RemoteQueryListener<*>?
        if (remoteQueryListener != null) {
            results.subscribe(remoteQueryListener)
        }
    }

    /**
     * Unsubscribe query.  The only difference between this class is that it will also remove the push notification subscriber.
     *
     * @param query Query to unsubscribe from
     * @return Whether the listener was listening to begin with
     * @since 1.3.0
     */
    override fun unSubscribe(query: Query): Boolean {
        val remoteQueryListener = this.pushPublisher!!.getRegisteredSubscriberIdentity(query.changeListener as PushSubscriber?) as RemoteQueryListener<*>?
        if (remoteQueryListener != null) {
            this.pushPublisher!!.deRegisterSubscriberIdentity(remoteQueryListener)
            val cachedResults = getCachedQueryResults(query)
            cachedResults?.unSubscribe(remoteQueryListener)
        }
        return false
    }
}
