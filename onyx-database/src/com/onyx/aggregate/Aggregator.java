package com.onyx.aggregate;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Created by tosborn1 on 5/19/16.
 */
public interface Aggregator
{

    BiConsumer<IManagedEntity, PersistenceManager> getConsumer();

}
