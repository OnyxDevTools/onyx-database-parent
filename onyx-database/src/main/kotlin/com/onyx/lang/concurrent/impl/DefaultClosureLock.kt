package com.onyx.lang.concurrent.impl

import com.onyx.lang.concurrent.ClosureLock
import com.onyx.lang.concurrent.ClosureReadWriteLock

class DefaultClosureLock : ClosureLock {

    private val lockImplementation: ClosureReadWriteLock = DefaultClosureReadWriteLock.createLock()

    override fun <T> perform(consumer: () -> T): T = lockImplementation.writeLock(consumer)

}