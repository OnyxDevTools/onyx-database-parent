package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureReadWriteLock

class EmptyClosureReadWriteLock : ClosureReadWriteLock {

    override fun <T> optimisticReadLock(consumer: () -> T): T = consumer.invoke()

    override fun <T> readLock(consumer: () -> T): T = consumer.invoke()

    override fun <T> writeLock(consumer: () -> T): T = consumer.invoke()

}