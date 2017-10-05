package com.onyx.concurrent.impl

import com.onyx.concurrent.DispatchLock

class DefaultDispatchLock : DispatchLock {

    override fun <T> performWithLock(lock: Any, consumer: (lock: Any) -> T): T = synchronized(lock) {
        consumer.invoke(lock)
    }

}