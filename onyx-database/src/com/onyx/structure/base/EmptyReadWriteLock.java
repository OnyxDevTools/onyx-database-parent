package com.onyx.structure.base;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Created by tosborn1 on 1/9/17.
 */
public class EmptyReadWriteLock implements ReadWriteLock {

    private EmptyLock emptyLock = new EmptyLock();

    @Override
    public Lock readLock() {
        return emptyLock;
    }

    @Override
    public Lock writeLock() {
        return emptyLock;
    }

    class EmptyLock implements Lock
    {

        @Override
        public void lock() {

        }

        @Override
        public void lockInterruptibly() throws InterruptedException {

        }

        @Override
        public boolean tryLock() {
            return false;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public void unlock() {

        }

        @Override
        public Condition newCondition() {
            return null;
        }
    }
}
