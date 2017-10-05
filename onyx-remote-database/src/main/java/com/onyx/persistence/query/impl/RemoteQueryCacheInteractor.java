package com.onyx.persistence.query.impl;

import com.onyx.client.push.PushPublisher;
import com.onyx.client.push.PushSubscriber;
import com.onyx.interactors.cache.impl.DefaultQueryCacheInteractor;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.interactors.cache.data.CachedResults;
import com.onyx.persistence.query.QueryListener;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This class is responsible for controlling the query cache for a remote server.
 */
public class RemoteQueryCacheInteractor extends DefaultQueryCacheInteractor {

    private PushPublisher pushPublisher;

    public RemoteQueryCacheInteractor(SchemaContext context) {
        super(context);
    }

    /**
     * This method is used to subscribe irrespective of a query being ran.
     * Overridden in order to associate the remote query listener as the
     * push subscriber information will be set.
     *
     * @param query Query object with defined listener
     *
     * @since 1.3.1
     */
    @Override
    public void subscribe(Query query) {
        RemoteQueryListener remoteQueryListener = (RemoteQueryListener) this.pushPublisher.getRegisteredSubscriberIdentity((PushSubscriber) query.getChangeListener());
        query.setChangeListener(remoteQueryListener);
        super.subscribe(query);
    }

    /**
     * Subscribe a query listener with associated cached results. This is overridden so that we can get the true identity
     * of the push subscriber through the publisher.  The publisher keeps track of all registered subscribers.
     *
     * @param results       Results to listen to
     * @param queryListener listner to respond to cache changes
     * @since 1.3.0
     */
    public void subscribe(CachedResults results, QueryListener queryListener) {
        RemoteQueryListener remoteQueryListener = (RemoteQueryListener) this.pushPublisher.getRegisteredSubscriberIdentity((PushSubscriber) queryListener);
        if(remoteQueryListener != null) {
            results.subscribe(remoteQueryListener);
        }
    }

    /**
     * Unsubscribe query.  The only difference between this class is that it will also remove the push notification subscriber.
     *
     * @param query Query to unsubscribe from
     * @return Whether the listener was listening to begin with
     * @since 1.3.0
     */
    public boolean unSubscribe(Query query) {
        final RemoteQueryListener remoteQueryListener = (RemoteQueryListener) this.pushPublisher.getRegisteredSubscriberIdentity((PushSubscriber) query.getChangeListener());
        if (remoteQueryListener != null) {
            this.pushPublisher.deRegiserSubscriberIdentity(remoteQueryListener);
            final CachedResults cachedResults = getCachedQueryResults(query);
            if (cachedResults != null) {
                return cachedResults != null && cachedResults.unSubscribe(remoteQueryListener);
            }
        }
        return false;
    }

    /**
     * Push publisher manages communication responses back to the client.
     *
     * @param pushPublisher Server mechanism responsible for push notifications
     *
     * @since 1.3.0
     */
    public void setPushPublisher(PushPublisher pushPublisher) {
        this.pushPublisher = pushPublisher;
    }
}
