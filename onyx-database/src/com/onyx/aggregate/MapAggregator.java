package com.onyx.aggregate;

import com.onyx.persistence.manager.PersistenceManager;

import java.util.Map;

/**
 * Created by tosborn1 on 5/19/16.
 */
public interface MapAggregator extends Aggregator {

    void aggregate(PersistenceManager persistenceManager, Map<String, Object> entity);

}
