package com.onyx.lang.concurrent

/**
 * Created by timothy.osborn on 4/7/15.
 *
 * This class allows read-write locks only for various levels.  There are 10 levels starting with 0 based counting.
 *
 * If you lock level 1 it will have no impact on the other levels.
 *
 * @since 1.2.0 This was changed to use a stamp lock for additional performance gains.  Since it cannot be used recursively.
 */
interface ClosureLock {

    /**
     * This method performs a lambda function by locking on whatever object you pass in.
     *
     * @param lock The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    fun <T> performWithLock(lock: Any, consumer: (lock: Any) -> T):T

}