package com.onyx.persistence.query.impl;

import com.onyx.client.push.PushPublisher;
import com.onyx.client.push.PushSubscriber;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.query.CachedResults;
import com.onyx.query.QueryListener;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This class is responsible for controlling the query cache for a remote server.
 */
public class RemoteQueryCacheController extends DefaultQueryCacheController {

    private PushPublisher pushPublisher;

    public RemoteQueryCacheController(SchemaContext context) {
        super(context);
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
        results.subscribe(remoteQueryListener);
    }

    /**
     * Unsubscribe query.  The only difference between this class is that it will also remove the push notification subscriber.
     *
     * @param query Query to unsubscribe from
     * @return Whether the listener was listening to begin with
     * @since 1.3.0
     */
    public boolean unsubscribe(Query query) {
        final RemoteQueryListener remoteQueryListener = (RemoteQueryListener) this.pushPublisher.getRegisteredSubscriberIdentity((PushSubscriber) query.getChangeListener());
        if (remoteQueryListener != null) {
            this.pushPublisher.deRegiserSubscriberIdentity(remoteQueryListener);
            final CachedResults cachedResults = getCachedQueryResults(query);
            if (cachedResults != null) {
                return cachedResults != null && cachedResults.unsubscribe(remoteQueryListener);
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
