package com.onyx.interactors.cache.data

import com.onyx.extension.common.async
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryListener
import com.onyx.persistence.query.QueryListenerEvent
import java.util.HashSet

/**
 * Created by Tim Osborn on 3/21/17.
 *
 * This method denotes a cached query containing its references and potentially its values
 *
 * @since 1.3.0 When query caching was implemented
 */
class CachedResults(references: MutableMap<Reference, Any?>? = null) {

    var references: MutableMap<Reference, Any?>? = references
        set(value) = synchronized(this) { field = value}

    val listeners = HashSet<QueryListener<Any>>()

    /**
     * Subscribe a query event listener
     * @param queryListener Listener to add
     *
     * @since 1.3.0
     */
    @Suppress("UNCHECKED_CAST")
    fun subscribe(queryListener: QueryListener<*>) = synchronized(listeners) { listeners.add(queryListener as QueryListener<Any>) }

    /**
     * Un-subscribe a query event listener
     * @param queryListener Query listener to un-subscribe
     * @return Whether it was subscribed to begin with
     * @since 1.3.0
     */
    fun unSubscribe(queryListener: QueryListener<*>): Boolean = synchronized(listeners) { listeners.remove(queryListener) }

    /**
     * Remove an entity from the query cache.  This will also
     * invoke the subscribed listeners.
     *
     * @param reference Key reference value
     * @param entity Managed entity removed from the query cache
     * @param event What type of event this is.  If it were an update
     * than it should not invoke the listeners.  Only if it is
     * a true removal
     *
     * @since 1.3.0
     */
    fun remove(reference: Any, entity: IManagedEntity, event: QueryListenerEvent, meetsCriteria: Boolean) {
        val removed:Any? = synchronized(this) { references?.remove(reference) }
        when {
            removed != null && (event === QueryListenerEvent.DELETE || !meetsCriteria && event === QueryListenerEvent.PRE_UPDATE) -> dispatchRemoveEvent(entity)
            event === QueryListenerEvent.DELETE && meetsCriteria -> dispatchRemoveEvent(entity)
            else -> {}
        }
    }

    private fun dispatchAddedEvent(entity:Any) = dispatchEvent(entity) { listener, any -> listener.onItemAdded(any) }
    private fun dispatchRemoveEvent(entity:Any) = dispatchEvent(entity) { listener, any -> listener.onItemRemoved(any) }
    private fun dispatchUpdateEvent(entity:Any) = dispatchEvent(entity) { listener, any -> listener.onItemUpdated(any) }

    /**
     * Dispatch event for all listeners.  If the listener is no longer valid remove it.
     *
     * @param entity Entity that was acted upon that matches listener criteria
     */
    private fun dispatchEvent(entity: Any, body:(QueryListener<Any>,Any) -> Unit) {
        async {
            synchronized(listeners) {
                listeners.remove(NULL_LISTENER)
                val listenersToRemove = HashSet<QueryListener<*>>()
                listeners.forEach {
                    try {
                        body.invoke(it, entity)
                    } catch (e: Exception) {
                        listenersToRemove.add(it)
                    }
                }
                listeners.removeAll(listenersToRemove)
            }
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
        synchronized(this) {
            references?.put(reference, value)
        }
        when(event) {
            QueryListenerEvent.UPDATE -> dispatchUpdateEvent(value)
            QueryListenerEvent.INSERT -> dispatchAddedEvent(value)
            else -> { }
        }
    }

    companion object {
        val NULL_LISTENER: QueryListener<*>? = null
    }
}
