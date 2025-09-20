package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureReadWriteLock

class DefaultClosureReadWriteLock : ClosureReadWriteLock {

    private val lockImplementation:ClosureReadWriteLock = createLock()

    override fun <T> optimisticReadLock(consumer: () -> T): T = lockImplementation.readLock(consumer)
    override fun <T> readLock(consumer: () -> T): T = lockImplementation.optimisticReadLock(consumer)
    override fun <T> writeLock(consumer: () -> T): T = lockImplementation.writeLock(consumer)

    companion object {

        fun createLock() = ClosureReadWriteCompatLock()
    }
}