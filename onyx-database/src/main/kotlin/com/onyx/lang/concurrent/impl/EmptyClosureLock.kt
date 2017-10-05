package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureLock

/**
 * Created by tosborn1 on 2/20/17.
 *
 * This implementation ignores locking and does not provide any concurrency blocking.
 *
 * The purpose is so that it can be injected into a stateless implementation of a map.
 */
class EmptyClosureLock : ClosureLock {

    /**
     * This method does not perform any blocking it is an empty implemented method that invokes the consumer
     *
     * @param lock     The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    override fun <T> performWithLock(lock: Any, consumer: (lock:Any) -> T): T = consumer.invoke(lock)
}
