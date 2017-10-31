package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureLock
import com.onyx.lang.map.OptimisticLockingMap
import java.util.*
import kotlin.collections.HashMap

/**
 * Created by Tim Osborn on 8/4/15.
 *
 *
 * This is the default implementation of the ClosureLock that implements it using 10 different StampLocks
 */
@Suppress("UNUSED")
class ReferenceClosureLock : ClosureLock {

    private val references = OptimisticLockingMap<Any, Any>(WeakHashMap())

    /**
     * This method performs a lambda function by locking on whatever object you pass in.  In this case it has
     * to be the exact reference.
     *
     * @param lock     The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    override fun <T> performWithLock(lock: Any, consumer: (lock:Any) -> T): T {
        synchronized(references.getOrPut(lock) { lock }) {
            @Suppress("UNCHECKED_CAST")
            return consumer.invoke(lock)
        }
    }

}
