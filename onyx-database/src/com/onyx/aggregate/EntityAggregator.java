package com.onyx.aggregate;

import com.onyx.persistence.manager.PersistenceManager;

/**
 * Created by tosborn1 on 5/19/16.
 */
public interface EntityAggregator<T> extends Aggregator {

    void aggregate(PersistenceManager persistenceManager, T entity);

}
