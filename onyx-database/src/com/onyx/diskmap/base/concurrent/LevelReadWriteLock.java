package com.onyx.diskmap.base.concurrent;

/**
 * Created by timothy.osborn on 4/7/15.
 *
 * This class allows read-write locks only for various levels.  There are 10 levels starting with 0 based counting.
 *
 * If you lock level 1 it will have no impact on the other levels.
 *
 * @since 1.2.0 This was changed to use a stamp lock for additional performance gains.  Since it cannot be used recursively.
 */
public interface LevelReadWriteLock
{
    /**
     * Read Lock for level.
     * @param level level in which you want to lock
     * @return Stamp of the lock
     */
    long lockReadLevel(int level);

    /**
     * Read Un-Lock for level
     * @param level level in which you want to un-lock
     * @param stamp Stamp of the lock
     */
    void unlockReadLevel(int level, long stamp);

    /**
     * Write Lock for level
     * @param level level in which you want to lock
     * @return Stamp of the lock
     */
    long lockWriteLevel(int level);

    /**
     * Write Un-Lock for level
     * @param level level in which you want to un-lock
     * @param stamp Stamp of the lock
     */
    void unlockWriteLevel(int level, long stamp);
}