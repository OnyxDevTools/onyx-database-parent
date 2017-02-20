package com.onyx.diskmap.base.concurrent;

import java.util.concurrent.locks.StampedLock;

/**
 * Created by tosborn1 on 8/4/15.
 *
 * This is the default implementation of the LevelReadWriteLock that implements it using 10 different StampLocks
 */
public class DefaultLevelReadWriteLock implements LevelReadWriteLock
{
    // Lock for each level
    public StampedLock[] locks;

    /**
     * Constructor.  Instantiate the level locks
     */
    public DefaultLevelReadWriteLock()
    {
        locks = new StampedLock[10];
        locks[0] = new StampedLock();
        locks[1] = new StampedLock();
        locks[2] = new StampedLock();
        locks[3] = new StampedLock();
        locks[4] = new StampedLock();
        locks[5] = new StampedLock();
        locks[6] = new StampedLock();
        locks[7] = new StampedLock();
        locks[8] = new StampedLock();
        locks[9] = new StampedLock();
    }


    public long lockReadLevel(int level)
    {
        return locks[level].readLock();
    }

    public void unlockReadLevel(int level, long stamp)
    {
        locks[level].unlockRead(stamp);
    }

    public long lockWriteLevel(int level)
    {
        return locks[level].writeLock();
    }

    public void unlockWriteLevel(int level, long stamp)
    {
        locks[level].unlockWrite(stamp);
    }
}
