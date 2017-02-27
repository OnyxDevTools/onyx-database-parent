package com.onyx.diskmap.base.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Created by tosborn1 on 1/9/17.
 *
 * This is a passive lock that has no locking capabilities.  It is used to override locking on base level classes.
 */
public class EmptyReadWriteLock implements ReadWriteLock {

    // A lock that does not lock
    private final EmptyLock emptyLock = new EmptyLock();

    @Override
    public Lock readLock() {
        return emptyLock;
    }

    @Override
    public Lock writeLock() {
        return emptyLock;
    }

    /**
     * Implementation of a lock that has no impact.
     */
    private class EmptyLock implements Lock
    {
        @Override
        public void lock() {}

        @Override
        public void lockInterruptibly() throws InterruptedException {}

        @Override
        public boolean tryLock() {
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return true;
        }

        @Override
        public void unlock() {}

        @Override
        public Condition newCondition() {
            return null;
        }
    }
}
