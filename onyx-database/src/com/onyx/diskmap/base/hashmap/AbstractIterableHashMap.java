package com.onyx.diskmap.base.hashmap;

import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.store.Store;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

/**
 * This class maintains the iterator capablities for the hash table.
 *
 * @param <K> Key
 * @param <V> Value
 */
abstract class AbstractIterableHashMap<K, V> extends AbstractCachedHashMap<K, V> {

    /**
     * Constructor
     *
     * @param fileStore File storage
     * @param header Head of the data structore
     * @param headless Whether it is used in a stateless manner.
     * @param loadFactor Indicates how large to allocate the initial data structure
     *
     * @since 1.2.0
     */
    AbstractIterableHashMap(Store fileStore, Header header, boolean headless, int loadFactor) {
        super(fileStore, header, headless, loadFactor);
    }

    /**
     * Collection of skip list references
     * @return Collection of skip list references
     */
    Collection skipListMaps() {
        return new SkipListMapSet();
    }

    /**
     * Iterates through the skip list references.
     */
    private class SkipListMapIterator implements Iterator {
        int index = 0;

        @Override
        public boolean hasNext() {
            return index < getMapCount();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object next() {
            try {
                int mapId = getMapIdentifier(index);
                return getReference(mapId);
            }
            finally {
                index ++;
            }
        }
    }

    private class SkipListMapSet extends AbstractCollection {
        @Override
        public Iterator iterator() {
            return new SkipListMapIterator();
        }

        @Override
        public int size() {
            return getMapCount();
        }
    }
}
