package com.onyx.persistence.stream

import com.onyx.persistence.manager.PersistenceManager

/**
 * Created by Tim Osborn on 5/19/16.
 *
 * This is a lambda for the Onyx Database Stream API.  This will iterate through entities
 */
interface QueryStream<in T> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param entity Managed entity
     * @param persistenceManager the second input argument is a PersistenceManager
     */
    fun accept(entity: T, persistenceManager: PersistenceManager)
}
