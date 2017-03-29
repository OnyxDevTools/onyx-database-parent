package com.onyx.persistence.context.impl;

import com.onyx.client.push.PushPublisher;
import com.onyx.persistence.query.impl.DefaultQueryCacheController;
import com.onyx.persistence.query.impl.RemoteQueryCacheController;


/**
 * Created by tosborn1 on 3/27/17.
 */
public class ServerSchemaContext extends DefaultSchemaContext {

    /**
     * Constructor.
     *
     * @param contextId Database identifier that must be unique and tied to its process
     */
    public ServerSchemaContext(String contextId) {
        super(contextId);
        this.queryCacheController = new DefaultQueryCacheController(this);
    }

    public void start() {
        super.start();
        this.queryCacheController = new RemoteQueryCacheController(this);
    }

    public void setPushPublisher(PushPublisher pushPublisher) {
        ((RemoteQueryCacheController)this.queryCacheController).setPushPublisher(pushPublisher);
    }

    /**
     * Shutdown schema context. Close files, connections or any other IO mechanisms used within the context
     *
     * @since 1.0.0
     */
    public void shutdown() {
        super.shutdown();
    }

}
