package com.onyx.concurrent.impl

import com.onyx.concurrent.DispatchReadWriteLock

class EmptyDispatchReadWriteLock : DispatchReadWriteLock {

    override fun <T> optimisticReadLock(consumer: () -> T): T = consumer.invoke()

    override fun <T> readLock(consumer: () -> T): T = consumer.invoke()

    override fun <T> writeLock(consumer: () -> T): T = consumer.invoke()

}