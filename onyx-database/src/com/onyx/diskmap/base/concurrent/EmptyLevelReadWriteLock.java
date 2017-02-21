package com.onyx.diskmap.base.concurrent;

/**
 * Created by tosborn1 on 2/20/17.
 *
 * This implementation igores locking and does not provide any concurrency blocking.
 *
 * The purpose is so that it can be injected into a stateless implementation of a map.
 */
public class EmptyLevelReadWriteLock implements LevelReadWriteLock {

    @Override
    public long lockReadLevel(int level) {
        return 0;
    }

    @Override
    public void unlockReadLevel(int level, long stamp) {}

    @Override
    public long lockWriteLevel(int level) {
        return 0;
    }

    @Override
    public void unlockWriteLevel(int level, long stamp) {}
}
