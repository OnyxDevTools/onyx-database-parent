package com.onyx.structure.base;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * Created by timothy.osborn on 4/7/15.
 */
public interface LevelReadWriteLock extends ReadWriteLock
{
    /**
     * Read Lock for level
     * @param level level in which you want to lock
     */
    void lockReadLevel(int level);

    /**
     * Read Un-Lock for level
     * @param level level in which you want to un-lock
     */
    void unlockReadLevel(int level);

    /**
     * Write Lock for level
     * @param level level in which you want to lock
     */
    void lockWriteLevel(int level);

    /**
     * Write Un-Lock for level
     * @param level level in which you want to un-lock
     */
    void unlockWriteLevel(int level);
}