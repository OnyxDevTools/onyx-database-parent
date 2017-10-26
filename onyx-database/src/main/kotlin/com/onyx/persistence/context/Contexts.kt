package com.onyx.persistence.context


/**
 * Created by Tim Osborn on 9/6/17.
 *
 * This class tracks all of the open contexts.  This was moved out to
 * take advantage of Kotlin's value
 *
 * @since 2.0.0
 */
object Contexts {

    private val contexts = HashMap<String, SchemaContext>()

    /**
     * Add Context to catalog.  This will uniquely store via contextId
     *
     * @param context Context to add or replace
     * @since 2.0.0
     */
    @JvmStatic
    fun put(context:SchemaContext) = synchronized(contexts) {
        contexts[context.contextId] = context
    }

    /**
     * Get Schema Context by contextId
     *
     * @since 2.0.0
     * @return Schema context within the catalog
     */
    @JvmStatic
    fun get(contextId:String) = synchronized(contexts) { contexts[contextId] }

    /**
     * Get First context within the catalog.  This is in the event it has not been added
     * correctly
     *
     * @since 2.0.0
     *
     * @return First context within catalog
     */
    @JvmStatic
    fun first() = synchronized(contexts) { contexts.values.first() }

    @JvmStatic
    fun clear() = contexts.clear()
}