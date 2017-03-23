package com.onyx.query;

import com.onyx.persistence.query.Query;

/**
 * Created by tosborn1 on 3/21/17.
 */
public interface QuerySubscriberController {

    void register(Query query);

    void unregester(Query query);

}
