package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureLock

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

}