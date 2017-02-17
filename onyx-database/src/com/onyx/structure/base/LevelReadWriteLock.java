package com.onyx.structure.base;


/**
 * Created by timothy.osborn on 4/7/15.
 */
public interface LevelReadWriteLock
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