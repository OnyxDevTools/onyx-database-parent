package com.onyx.query;

import com.onyx.persistence.IManagedEntity;

import java.util.List;

/**
 * Created by tosborn1 on 3/21/17.
 */
public interface QueryListener {

    void onItemsUpdated(List<IManagedEntity> items);

    void onItemsAdded(List<IManagedEntity> items);

    void onItemsRemoved(List<IManagedEntity> items);

}
