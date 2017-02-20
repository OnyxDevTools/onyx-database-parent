package com.onyx.diskmap.base.concurrent;

/**
 * Created by tosborn1 on 2/20/17.
 */
public class EmptyLevelReadWriteLock implements LevelReadWriteLock {
    @Override
    public long lockReadLevel(int level) {
        return 0;
    }

    @Override
    public void unlockReadLevel(int level, long stamp) {

    }

    @Override
    public long lockWriteLevel(int level) {
        return 0;
    }

    @Override
    public void unlockWriteLevel(int level, long stamp) {

    }
}
