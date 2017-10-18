package com.onyx.lang.concurrent.impl

import com.onyx.extension.common.async
import com.onyx.lang.concurrent.ClosureLock
import kotlinx.coroutines.experimental.Deferred

class DefaultClosureLock : ClosureLock {

    /**
     * This method performs a lambda function by locking on whatever object you pass in.
     *
     * @param lock The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    override fun <T> performWithLock(lock: Any, consumer: (lock: Any) -> T):T = synchronized(lock) {
        consumer.invoke(lock)
    }

    /**
     * This method performs a lambda function by locking on whatever object you pass in.  The only difference
     * is that it locks on itself.
     *
     * @param consumer Function to invoke
     * @return The result from the function
     */
    override fun <T> queue(consumer: () -> T):Deferred<T> = async {
        consumer.invoke()
    }
}