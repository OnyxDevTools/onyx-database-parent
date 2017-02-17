package com.onyx.structure.base;

import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.structure.node.Header;
import com.onyx.structure.store.Store;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * Created by tosborn1 on 2/15/17.
 */
public class DiskHashMap extends AbstractIterableHashMap {

    private LevelReadWriteLock levelReadWriteLock = new LevelReadWriteLock() {
        @Override
        public void lockReadLevel(int level) {

        }

        @Override
        public void unlockReadLevel(int level) {

        }

        @Override
        public void lockWriteLevel(int level) {

        }

        @Override
        public void unlockWriteLevel(int level) {

        }
    };

    DiskHashMap(Store fileStore, Header header, boolean headless, int loadFactor) {
        super(fileStore, header, headless, loadFactor);
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public Object get(Object key) {
        return getReference(((Integer) key).intValue());
    }

    @Override
    public Object put(Object key, Object value) {
        return insertReference(((Integer) key).intValue(), ((Long)value).longValue());
    }

    @Override
    public Object remove(Object key) {
        return null;
    }

    @Override
    public void putAll(Map m) {

    }

    @Override
    public void clear() {
        cache.clear();
        header.firstNode = 0L;

    }


    @Override
    public long getRecID(Object key) {
        return 0;
    }

    @Override
    public Object getWithRecID(long recordId) {
        return null;
    }

    @Override
    public Map getMapWithRecID(long recordId) {
        return null;
    }

    @Override
    public Object getAttributeWithRecID(String attribute, long reference) throws AttributeTypeMismatchException {
        return null;
    }

    @Override
    public Set referenceSet() {
        return null;
    }

    public LevelReadWriteLock getReadWriteLock() {
        return levelReadWriteLock;
    }
}
