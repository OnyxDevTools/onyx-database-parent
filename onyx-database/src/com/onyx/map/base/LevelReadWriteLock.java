package com.onyx.map.base;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Created by timothy.osborn on 4/7/15.
 */
public interface LevelReadWriteLock extends ReadWriteLock
{
    /**
     * Get Read lock for level
     *
     * @param level
     * @return
     */
    Lock readLock(int level);

    /**
     * Get Write Lock for Level
     * @param level
     * @return
     */
    Lock writeLock(int level);
}