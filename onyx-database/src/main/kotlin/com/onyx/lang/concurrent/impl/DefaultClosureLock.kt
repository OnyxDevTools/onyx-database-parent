package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureLock

class DefaultClosureLock : ClosureLock {

    override fun <T> performWithLock(lock: Any, consumer: (lock: Any) -> T): T = synchronized(lock) {
        consumer.invoke(lock)
    }

}