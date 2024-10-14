package com.onyx.persistence.context

import com.onyx.lang.concurrent.impl.DefaultClosureReadWriteLock
import com.onyx.persistence.context.impl.CacheSchemaContext
import com.onyx.persistence.context.impl.DefaultSchemaContext
import java.util.*


/**
 * Created by Tim Osborn on 9/6/17.
 *
 * This class tracks all of the open contexts.  This was moved out to
 * take advantage of Kotlin's value
 *
 * @since 2.0.0
 */
object Contexts {

    private val lock = DefaultClosureReadWriteLock()
    private val contexts = TreeMap<String, SchemaContext>()

    /**
     * Add Context to catalog.  This will uniquely store via contextId
     *
     * @param context Context to add or replace
     * @since 2.0.0
     */
    @JvmStatic
    fun put(context:SchemaContext) = lock.writeLock {
        contexts[context.contextId] = context
    }

    /**
     * Get Schema Context by contextId
     *
     * @since 2.0.0
     * @return Schema context within the catalog
     */
    @JvmStatic
    fun get(contextId:String) = lock.optimisticReadLock { contexts[contextId] }

    /**
     * Get First context within the catalog.  This is in the event it has not been added
     * correctly
     *
     * @since 2.0.0
     *
     * @return First context within catalog
     */
    @JvmStatic
    fun first() = lock.optimisticReadLock { contexts.values.first() }

    /**
     * Get Last context within the catalog.  This is in the event it has not been added
     * correctly
     *
     * @since 2.0.0
     *
     * @return Last context within catalog
     */
    @JvmStatic
    fun firstRemote() = lock.optimisticReadLock { contexts.values.first { it::class != DefaultSchemaContext::class && it::class != CacheSchemaContext::class }}

    @JvmStatic
    fun clear() = contexts.clear()

}