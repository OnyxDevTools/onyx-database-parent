package com.onyx.query;

import com.onyx.persistence.IManagedEntity;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Created by tosborn1 on 3/21/17.
 *
 * This method denotes a cached query containing its references and potentially its values
 *
 * @since 1.3.0 When query caching was implemented
 */
public class CachedResults {

    // Query reference/values
    private final Map references;

    /**
     * Constructor containing query resutls
     * @param results The references of a query
     *
     *                Prerequisite, if this is a sorted query, you must send in a TreeMap so that
     *                the query order is retained.
     * @since 1.3.0
     */
    public CachedResults(Map results)
    {
        this.references = results;
    }

    /**
     * Get the references of a query
     * @return Entity references
     *
     * @since 1.3.0
     */
    public Map getReferences() {
        return references;
    }

    // Query event listeners
    private final Set<QueryListener> listeners = new HashSet<>();

    /**
     * Subscribe a query event listener
     * @param queryListener Listener to add
     *
     * @since 1.3.0
     */
    public void subscribe(QueryListener queryListener)
    {
        listeners.add(queryListener);
    }

    /**
     * Unsubscribe a query event listener
     * @param queryListener Query listener to unsubscribe
     * @return Whether it was subscribed to begin with
     * @since 1.3.0
     */
    public boolean unsubscribe(QueryListener queryListener)
    {
        return listeners.remove(queryListener);
    }

    /**
     * Remove an entity from the query cache.  This will also
     * invoke the subscribed listeners.
     *
     * @param reference Key reference value
     * @param entity Managed entity removed from the query cache
     * @param event What type of event this is.  If it were an upate
     *              than it should not invoke the listeners.  Only if it is
     *              a true removal
     *
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    public void remove(Object reference, IManagedEntity entity, QueryListenerEvent event, boolean meetsCriteria)
    {
        Object removed = references.remove(reference);
        listeners.remove(null); // Clean out old references
        if(removed != null && (event == QueryListenerEvent.DELETE || (!meetsCriteria && event == QueryListenerEvent.PRE_UPDATE))) {

            Set<QueryListener> listenersToRemove = new HashSet();
            for(QueryListener listener : listeners)
            {
                try {
                    listener.onItemRemoved(entity);
                } catch (Exception e)
                {
                    listenersToRemove.add(listener);
                }
            }
            listeners.removeAll(listenersToRemove);
        }
    }

    /**
     * Get registered listeners
     *
     * @return Set of unique listeners
     */
    public Set<QueryListener> getListeners() {
        return listeners;
    }

    /**
     * Put a value into the query cache.  Either an entity has been inserted or
     * updated.
     *
     * @param reference Entity reference
     * @param value Entity or selection map
     * @param event What type of query event.  Can be an insert or an update
     *
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    public void put(Object reference, Object value, QueryListenerEvent event)
    {
        references.put(reference, value);
        listeners.remove(null);
        final Set<QueryListener> listenersToRemove = new HashSet();
        for(QueryListener listener : listeners)
        {
            try {
                if (event == QueryListenerEvent.INSERT) {
                    listener.onItemAdded(value);
                } else if (event == QueryListenerEvent.UPDATE) {
                    listener.onItemUpdated(value);
                }
            } catch (Exception e)
            {
                listenersToRemove.add(listener);
            }
        }
        listeners.removeAll(listenersToRemove);
    }
}
