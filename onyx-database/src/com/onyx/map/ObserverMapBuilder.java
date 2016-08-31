package com.onyx.map;

import com.onyx.map.node.Header;
import com.onyx.map.store.InMemoryStore;
import com.onyx.map.store.Store;
import com.onyx.persistence.context.SchemaContext;
import java.util.Observable;

/**
 * Created by tosborn1 on 8/30/16.
 *
 * This class was created in order to get around having table locks in Onyx Database.
 * Rather than doing that, we observe whether or not we should have the ability to
 * clean up stale deleted or updated data.  If not, we preserve it so that the data is in the state that
 * it was when a query was started.  When we get a signal that there are no pending queries in progress,
 * we now have the ability to clean up the stale data.
 */
public class ObserverMapBuilder extends DefaultMapBuilder {

    protected Observable observable = null;

    /**
     * Default constructor with storage file path, schema context, and observer value
     *
     * @since 1.0.2
     * @param filePath Storage file path
     * @param context Schema context to the database
     * @param observable Observer for the disk maps to observe
     */
    public ObserverMapBuilder(String filePath, SchemaContext context, Observable observable) {
        super(filePath, context);
        this.observable = observable;
    }

    /**
     * Overridden so we can return a custom Observable disk map
     *
     * @since 1.0.2
     * @param store The physical store
     * @param header The master map header
     * @return Instantiated and configured disk map
     */
    protected DiskMap newDiskMap(Store store, Header header)
    {
        ObservableDiskMap newDiskMap = null;
        if(store instanceof InMemoryStore) {
            newDiskMap = new ObservableDiskMap(store, header, true);
        }
        else {
            newDiskMap = new ObservableDiskMap(store, header);
        }

        if(observable != null)
            observable.addObserver(newDiskMap);
        return newDiskMap;
    }
}
