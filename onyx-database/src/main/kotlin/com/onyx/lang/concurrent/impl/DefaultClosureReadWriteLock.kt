package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureReadWriteLock
import java.util.concurrent.locks.StampedLock

class DefaultClosureReadWriteLock : ClosureReadWriteLock {

    private var lockImplementation = StampedLock()

    override fun <T> optimisticReadLock(consumer: () -> T): T {
        var stamp = lockImplementation.tryOptimisticRead()
        val returnValue = consumer.invoke()
        if(!lockImplementation.validate(stamp)) {
            stamp = lockImplementation.readLock()
            return try {
                consumer.invoke()
            } finally {
                lockImplementation.unlockRead(stamp)
            }
        }
        return returnValue
    }

    override fun <T> readLock(consumer: () -> T): T {
        val stamp = lockImplementation.readLock()
        return try {
            consumer.invoke()
        }
        finally {
            lockImplementation.unlockRead(stamp)
        }
    }

    override fun <T> writeLock(consumer: () -> T): T {
        val stamp = lockImplementation.writeLock()
        return try {
            consumer.invoke()
        } finally {
            lockImplementation.unlockWrite(stamp)
        }
    }
}