package com.onyx.interactors.cache.data

import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryListener
import com.onyx.persistence.query.QueryListenerEvent
import java.util.HashSet

/**
 * Created by tosborn1 on 3/21/17.
 *
 * This method denotes a cached query containing its references and potentially its values
 *
 * @since 1.3.0 When query caching was implemented
 */
class CachedResults(var references: MutableMap<Reference, Any?>?) {

    val listeners = HashSet<QueryListener<Any>>()

    /**
     * Subscribe a query event listener
     * @param queryListener Listener to add
     *
     * @since 1.3.0
     */
    @Suppress("UNCHECKED_CAST")
    fun subscribe(queryListener: QueryListener<*>) {
        listeners.add(queryListener as QueryListener<Any>)
    }

    /**
     * Un-subscribe a query event listener
     * @param queryListener Query listener to un-subscribe
     * @return Whether it was subscribed to begin with
     * @since 1.3.0
     */
    fun unSubscribe(queryListener: QueryListener<*>): Boolean = listeners.remove(queryListener)

    /**
     * Remove an entity from the query cache.  This will also
     * invoke the subscribed listeners.
     *
     * @param reference Key reference value
     * @param entity Managed entity removed from the query cache
     * @param event What type of event this is.  If it were an upate
     * than it should not invoke the listeners.  Only if it is
     * a true removal
     *
     * @since 1.3.0
     */
    fun remove(reference: Any, entity: IManagedEntity, event: QueryListenerEvent, meetsCriteria: Boolean) {
        var removed: Any? = null
        if (references != null)
            removed = references!!.remove(reference)
        listeners.remove(NULL_LISTENER) // Clean out old references
        if (removed != null && (event === QueryListenerEvent.DELETE || !meetsCriteria && event === QueryListenerEvent.PRE_UPDATE)) {
            val listenersToRemove = HashSet<QueryListener<*>>()
            listeners.forEach {
                try {
                    it.onItemRemoved(entity)
                } catch (e: Exception) {
                    listenersToRemove.add(it)
                }
            }
            listeners.removeAll(listenersToRemove)
        } else if (event === QueryListenerEvent.DELETE && meetsCriteria) {
            val listenersToRemove = HashSet<QueryListener<*>>()
            listeners.forEach {
                try {
                    it.onItemRemoved(entity)
                } catch (e: Exception) {
                    listenersToRemove.add(it)
                }
            }
            listeners.removeAll(listenersToRemove)
        }
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
    fun put(reference: Reference, value: Any, event: QueryListenerEvent) {
        if (references != null)
            references!!.put(reference, value)
        listeners.remove(NULL_LISTENER)
        val listenersToRemove = HashSet<QueryListener<Any>>()
        listeners.forEach {
            try {
                if (event === QueryListenerEvent.INSERT) {
                    it.onItemAdded(value)
                } else if (event === QueryListenerEvent.UPDATE) {
                    it.onItemUpdated(value)
                }
            } catch (e: Exception) {
                listenersToRemove.add(it)
            }
        }
        listeners.removeAll(listenersToRemove)
    }

    companion object {
        val NULL_LISTENER: QueryListener<*>? = null
    }
}
