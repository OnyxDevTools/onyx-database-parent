package com.onyx.concurrent.impl

import com.onyx.concurrent.DispatchLock
import java.util.*

/**
 * Created by tosborn1 on 8/4/15.
 *
 *
 * This is the default implementation of the DispatchLock that implements it using 10 different StampLocks
 */
class DefaultDispatchLock : DispatchLock {

    private val references = WeakHashMap<Any, Any>()

    /**
     * This method performs a lambda function by locking on whatever object you pass in.  In this case it has
     * to be the exact reference.
     *
     * @param lock     The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    override fun <T> performWithLock(lock: Any, consumer: (lock:Any) -> T): T {
        var lockReference = lock

        var objectToLockOn: Any?
        synchronized(references) {
            objectToLockOn = references[lockReference]
            if (objectToLockOn == null) {
                references.put(lockReference, lockReference)
            } else {
                lockReference = objectToLockOn!!
            }
        }

        synchronized(lockReference) {
            @Suppress("UNCHECKED_CAST")
            return consumer.invoke(lockReference)
        }
    }

}
