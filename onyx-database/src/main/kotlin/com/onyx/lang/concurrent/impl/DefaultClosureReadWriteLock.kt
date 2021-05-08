package com.onyx.lang.concurrent.impl

import com.onyx.interactors.classfinder.ApplicationClassFinder
import com.onyx.lang.concurrent.ClosureReadWriteLock

class DefaultClosureReadWriteLock : ClosureReadWriteLock {

    private val lockImplementation:ClosureReadWriteLock = createLock()

    override fun <T> optimisticReadLock(consumer: () -> T): T = lockImplementation.readLock(consumer)
    override fun <T> readLock(consumer: () -> T): T = lockImplementation.readLock(consumer)
    override fun <T> writeLock(consumer: () -> T): T = lockImplementation.writeLock(consumer)

    companion object {

        fun createLock() = if(lockClass === LockType.STAMP_LOCK) StampedClosureReadWriteLock() else ClosureReadWriteCompatLock()

        private val lockClass by lazy {
            try {
                ApplicationClassFinder.forName("java.util.concurrent.locks.StampedLock")
                LockType.STAMP_LOCK
            } catch (e:Exception){
                LockType.REENTRANT_READ_WRITE
            }
        }

        enum class LockType{
            STAMP_LOCK, REENTRANT_READ_WRITE
        }
    }
}