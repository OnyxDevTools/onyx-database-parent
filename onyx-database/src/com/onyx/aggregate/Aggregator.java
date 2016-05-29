package com.onyx.aggregate;

import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;

import java.util.Map;

/**
 * Created by tosborn1 on 5/19/16.
 */
public interface Aggregator
{

    void run(String instance, Query query);

    default void run(Query query)
    {
        run(EmbeddedPersistenceManagerFactory.DEFAULT_INSTANCE, query);
    }

}
