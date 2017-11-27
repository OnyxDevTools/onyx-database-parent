package com.onyx.lang.concurrent.impl

import com.onyx.interactors.classfinder.ApplicationClassFinder
import com.onyx.lang.concurrent.ClosureReadWriteLock

class DefaultClosureReadWriteLock : ClosureReadWriteLock {

    private val lockImplementation:ClosureReadWriteLock = try {
        ApplicationClassFinder.forName("java.util.concurrent.locks.StampedLock")
        StampedClosureReadWriteLock()
    } catch (e:Exception) {
        ClosureReadWriteCompatLock()
    }

    override fun <T> optimisticReadLock(consumer: () -> T): T = lockImplementation.optimisticReadLock(consumer)
    override fun <T> readLock(consumer: () -> T): T = lockImplementation.readLock(consumer)
    override fun <T> writeLock(consumer: () -> T): T = lockImplementation.writeLock(consumer)

}