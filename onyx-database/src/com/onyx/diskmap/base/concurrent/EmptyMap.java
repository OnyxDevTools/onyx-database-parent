package com.onyx.diskmap.base.concurrent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by tosborn1 on 2/19/17.
 * <p>
 * This class is a map that does not perform any actions.  The purpose is so that it can be injected to ignore caching
 */
public class EmptyMap implements Map {
    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
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
        return null;
    }

    @Override
    public Object put(Object key, Object value) {
        return null;
    }

    @Override
    public Object remove(Object key) {
        return null;
    }

    @Override
    public void putAll(Map m) {}

    @Override
    public void clear() {}

    @Override
    public Set keySet() {
        return null;
    }

    @Override
    public Collection values() {
        return null;
    }

    @Override
    public Set<Entry> entrySet() {
        return null;
    }
}
