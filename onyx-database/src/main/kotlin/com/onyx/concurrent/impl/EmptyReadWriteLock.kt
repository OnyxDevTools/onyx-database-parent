package com.onyx.concurrent.impl

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock

/**
 * Created by tosborn1 on 1/9/17.
 *
 * This is a passive lock that has no locking capabilities.  It is used to override locking on base level classes.
 */
class EmptyReadWriteLock : ReadWriteLock {

    // A lock that does not lock
    override fun readLock(): Lock = emptyLock
    override fun writeLock(): Lock = emptyLock

    /**
     * Implementation of a lock that has no impact.
     */
    class EmptyLock : Lock {
        private val condition = newCondition()
        override fun lock() {}
        @Throws(InterruptedException::class)
        override fun lockInterruptibly() {}
        @Throws(InterruptedException::class)
        override fun tryLock(time: Long, unit: TimeUnit): Boolean =  true
        override fun tryLock(): Boolean = false
        override fun unlock() {}
        override fun newCondition(): Condition = condition
    }

    companion object {
        val emptyLock = EmptyLock()
    }
}
