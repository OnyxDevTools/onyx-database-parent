package com.onyx.query;


/**
 * Created by tosborn1 on 3/21/17.
 *
 * Template for query change listener.  This is the contract you adhere to while
 * listening for changes in a query.  If an entity is inserted, updated, or deleted
 * that match the orignal query this listener is associated to, it will fire one
 * of these delegate methods.
 */
@SuppressWarnings("unused")
public interface QueryListener<T> {

    /**
     * Item has been modified.  This ocurres when an entity met the original criteria
     * when querying the database and was updated.  The updated values still match the critieria
     *
     * @param entity Entity updated via the persistence manager
     *
     * @since 1.3.0
     */
    void onItemUpdated(T item);

    /**
     * Item has been inserted.  This ocurres when an entity was saved and it meets the query criteria.
     *
     * @param entity Entity inserted via the persistence manager
     *
     * @since 1.3.0
     */
    void onItemAdded(T item);

    /**
     * Item has been deleted or no longer meets the critieria of the query.
     *
     * @param entity Entity persisted via the persistence manager
     *
     * @since 1.3.0
     */
    void onItemRemoved(T item);

}
