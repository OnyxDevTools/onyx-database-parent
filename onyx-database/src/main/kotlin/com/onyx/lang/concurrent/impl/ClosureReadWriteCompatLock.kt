package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

class ClosureReadWriteCompatLock : ClosureReadWriteLock {

    private val reentrantReadWriteLock = ReentrantReadWriteLock()

    override fun <T> readLock(consumer: () -> T): T {
        reentrantReadWriteLock.readLock().lock()
        return try {
            consumer.invoke()
        } finally {
            reentrantReadWriteLock.readLock().unlock()
        }
    }

    override fun <T> optimisticReadLock(consumer: () -> T): T {
        reentrantReadWriteLock.readLock().lock()
        return try {
            consumer.invoke()
        } finally {
            reentrantReadWriteLock.readLock().unlock()
        }
    }

    override fun <T> writeLock(consumer: () -> T): T {
        reentrantReadWriteLock.writeLock().lock()
        return try {
            consumer.invoke()
        } finally {
            reentrantReadWriteLock.writeLock().unlock()
        }
    }
}