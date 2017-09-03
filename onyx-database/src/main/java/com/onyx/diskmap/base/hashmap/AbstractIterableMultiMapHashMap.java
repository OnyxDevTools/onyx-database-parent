package com.onyx.diskmap.base.hashmap;

import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.node.SkipListHeadNode;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.diskmap.store.Store;
import java.util.*;

/**
 * This class allows iterating through a nested index.  The first level of indexing is using the Hash Table.  The second
 * maintains another cursor.  In this case, it is most likely a skip list.
 *
 * @param <K> Key
 * @param <V> Value
 */
@SuppressWarnings("unchecked")
public abstract class AbstractIterableMultiMapHashMap<K, V> extends AbstractIterableHashMap<K, V> {

    /**
     * Constructor
     *
     * @param store File storage
     * @param header Head of the data structore
     * @param isHeadless Whether it is used in a stateless manner.
     * @param loadFactor Indicates how large to allocate the initial data structure
     *
     * @since 1.2.0
     */
    protected AbstractIterableMultiMapHashMap(Store store, Header header, @SuppressWarnings("SameParameterValue") boolean isHeadless, int loadFactor) {
        super(store, header, isHeadless, loadFactor);
    }

    /**
     * Values as dictionaries / hash map
     * @return Set of maps
     * @since 1.2.0
     */
    public Set<Map> dictionarySet() {
        return new DictionarySet();
    }

    /**
     * Set of references.  In this case, the record pointer which is a skip list node.
     * @return Set of skip list nodes
     * @since 1.2.0
     */
    public Set<SkipListNode> referenceSet() {
        return new ReferenceSet();
    }

    /**
     * Set of keys within the disk map
     * @return Set of key types
     * @since 1.2.0
     */
    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    /**
     * Set of all the skip lists within the hash table
     * @return Set implementation that iterates through the top level index structure
     * @since 1.2.0
     */
    protected Set mapSet() {
        return new MapSet();
    }

    /**
     * Iterator of the values within the entire map.
     * @return Collection of values for the map's key value pair
     * @since 1.2.0
     */
    @Override
    public Collection<V> values() {
        return new ValueSet();
    }

    /**
     * Set of entries including the key and value
     * @return Set implementation with custom iterator
     * @since 1.2.0
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    class MultiMapIterator implements Iterator {
        final Iterator otherMapIterator;
        Iterator cursorIterator = null;

        boolean isDictionary = false;
        boolean isReference = false;

        MultiMapIterator() {
            otherMapIterator = AbstractIterableMultiMapHashMap.this.skipListMaps().iterator();
        }

        MultiMapIterator(boolean isDictionary, boolean isReference) {
            this.isDictionary = isDictionary;
            this.isReference = isReference;
            otherMapIterator = AbstractIterableMultiMapHashMap.this.skipListMaps().iterator();
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = (cursorIterator != null) && cursorIterator.hasNext();
            while (!hasNext) {
                hasNext = otherMapIterator.hasNext();
                if (!hasNext)
                    return false;
                long mapReference = (long) otherMapIterator.next();
                if(mapReference == 0)
                    continue;
                SkipListHeadNode node = findNodeAtPosition(mapReference);
                setHead(node);
                if (isDictionary)
                    cursorIterator = AbstractIterableMultiMapHashMap.super.entrySet().iterator();
                else if(isReference)
                    cursorIterator = AbstractIterableMultiMapHashMap.super.referenceSet().iterator();
                else
                    cursorIterator = AbstractIterableMultiMapHashMap.super.entrySet().iterator();
                hasNext = cursorIterator.hasNext();
            }

            return hasNext;
        }

        @Override
        public Object next() {
            return cursorIterator.next();
        }
    }

    private class MultiMapSkipListIterator implements Iterator {
        final Iterator otherMapIterator;

        MultiMapSkipListIterator() {
            otherMapIterator = AbstractIterableMultiMapHashMap.this.skipListMaps().iterator();
        }

        @Override
        public boolean hasNext() {
            return otherMapIterator.hasNext();
        }

        @Override
        public Object next() {
            return findNodeAtPosition((long)otherMapIterator.next());
        }
    }

    private class MultiMapKeyIterator extends MultiMapIterator {
        @Override
        public Object next() {
            return ((Entry) super.next()).getKey();
        }
    }

    private class MultiMapValueIterator extends MultiMapIterator {
        @Override
        public Object next() {
            return ((Entry) super.next()).getValue();
        }
    }

    private class KeySet extends EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapKeyIterator();
        }
    }

    private class MapSet extends EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapSkipListIterator();
        }
    }

    private class DictionarySet extends EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapIterator(true, false);
        }
    }

    private class ReferenceSet extends EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapIterator(false, true);
        }
    }

    private class ValueSet extends EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapValueIterator();
        }
    }

    class EntrySet extends AbstractSet {
        @Override
        public Iterator iterator() {
            return new MultiMapIterator();
        }

        @Override
        public int size() {
            return (int)AbstractIterableMultiMapHashMap.this.longSize();
        }
    }
}
