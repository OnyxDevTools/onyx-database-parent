package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.store.Store;

import java.util.*;

/**
 * Created by tosborn1 on 2/15/17.
 */
public abstract class AbstractIterableHashMap<K, V> extends AbstractCachedHashMap<K, V> {

    AbstractIterableHashMap(Store fileStore, Header header, boolean headless, int loadFactor) {
        super(fileStore, header, headless, loadFactor);
    }

    private Set keys = new KeySet();
    private Set values = new ValueSet();
    private Set entries = new EntrySet();

    @Override
    public Set<K> keySet() {
        return keys;
    }

    @Override
    public Collection<V> values() {
        return values;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entries;
    }

    class HashMapIterator implements Iterator {
        int index = 0;

        @Override
        public boolean hasNext() {
            return index < count;
        }

        @Override
        public Object next() {
            try {
                int mapId = getMapIdentifier(index);
                return new AbstractMap.SimpleEntry(mapId, getReference(mapId));
            }
            finally {
                index ++;
            }
        }
    }

    class HashMapKeyIterator extends HashMapIterator {
        @Override
        public Object next() {
            return ((Entry) super.next()).getKey();
        }
    }

    class HashMapValueIterator extends HashMapIterator {
        @Override
        public Object next() {
            return ((Entry) super.next()).getValue();
        }
    }

    class KeySet extends EntrySet {
        @Override
        public Iterator iterator() {
            return new HashMapKeyIterator();
        }
    }

    class ValueSet extends EntrySet {
        @Override
        public Iterator iterator() {
            return new HashMapValueIterator();
        }
    }

    class EntrySet implements Set {
        @Override
        public Iterator iterator() {
            return new HashMapIterator();
        }

        ///////////////////////////////////////////////////////
        //
        // Empty methods not used
        //
        ///////////////////////////////////////////////////////

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(Object o) {
            return false;
        }

        @Override
        public Object[] toArray() {
            return new Object[0];
        }

        @Override
        public boolean add(Object o) {
            return false;
        }

        @Override
        public boolean remove(Object o) {
            return false;
        }

        @Override
        public boolean addAll(Collection c) {
            return false;
        }

        @Override
        public void clear() {

        }

        @Override
        public boolean removeAll(Collection c) {
            return false;
        }

        @Override
        public boolean retainAll(Collection c) {
            return false;
        }

        @Override
        public boolean containsAll(Collection c) {
            return false;
        }

        @Override
        public Object[] toArray(Object[] a) {
            return new Object[0];
        }

    }
}
