package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

class ClosureReadWriteCompatLock : ClosureReadWriteLock {

    private val reentrantReadWriteLock = ReentrantReadWriteLock()
    private val readLock = reentrantReadWriteLock.readLock()
    private val writeLock = reentrantReadWriteLock.writeLock()

    override fun <T> readLock(consumer: () -> T): T {
        readLock.lock()
        return try {
            consumer.invoke()
        } finally {
            readLock.unlock()
        }
    }

    override fun <T> optimisticReadLock(consumer: () -> T): T {
        readLock.lock()
        return try {
            consumer.invoke()
        } finally {
            readLock.unlock()
        }
    }

    override fun <T> writeLock(consumer: () -> T): T {
        writeLock.lock()
        return try {
            consumer.invoke()
        } finally {
            writeLock.unlock()
        }
    }
}