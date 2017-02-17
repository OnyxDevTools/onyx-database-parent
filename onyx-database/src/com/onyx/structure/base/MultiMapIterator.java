package com.onyx.structure.base;

import com.onyx.structure.DiskMap;
import com.onyx.structure.node.Header;
import com.onyx.structure.node.SkipListHeadNode;
import com.onyx.structure.store.Store;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Created by tosborn1 on 1/9/17.
 */
public class MultiMapIterator<K, V> extends DiskSkipList<K, V> {

    protected DiskMap<Integer, Long> defaultDiskMap = null;

    /**
     * Constructor
     *
     * @param store
     */
    public MultiMapIterator(Store store, Header header, boolean isHeadless) {
        super(store, header, isHeadless, true);

        keys = new MultiMapIterator.KeySet();
        values = new MultiMapIterator.ValueSet();
        entries = new MultiMapIterator.EntrySet();
        dict = new MultiMapIterator.DictionarySet();
        maps = new MultiMapIterator.MapSet();

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Iterate-able properties on structure
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////


    protected Set references;

    /**
     * Getter for set of references
     *
     * @return the reference collection
     * @see AbstractIterableSkipList.KeyCollection
     * @see AbstractIterableSkipList.AbstractNodeCollection
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set referenceSet() {
        return references;
    }

    /**
     * Getter for set of keys
     *
     * @return
     * @see KeyCollectionMulti
     * @see AbstractMultiNodeCollection
     */
    protected Set keys;

    @Override
    public Set<K> keySet() {
        return keys;
    }

    /**
     * Getter for set of maps
     * <p>
     * This is a collection of the underlying map structures.  It is used for
     * iterating through the sub structures.
     */
    protected Set maps;

    public Set<K> mapSet() {
        return maps;
    }

    /**
     * Getter for set of keys
     *
     * @return
     * @see DictionaryCollectionMulti
     * @see AbstractMultiNodeCollection
     */
    protected Set dict;

    public Set<Map> dictionarySet() {
        return dict;
    }

    /**
     * Getter for values
     *
     * @return
     * @see KeyCollectionMulti
     * @see AbstractMultiNodeCollection
     */

    protected Set values;

    @Override
    public Collection<V> values() {
        return values;
    }

    /**
     * Getter for values.  This contains lazy loaded disk structure entries
     *
     * @return
     * @see KeyCollectionMulti
     * @see AbstractMultiNodeCollection
     */
    protected Set entries;

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entries;
    }

    /**
     * For Each Iterator
     *
     * @param action
     */
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Iterator it = this.entrySet().iterator();
        Entry<K, V> val = null;
        while (it.hasNext()) {
            val = (Entry<K, V>) it.next();
            action.accept(val.getKey(), val.getValue());
        }
    }

    /**
     * Not sure if this is supported correctly but, Its filled out
     *
     * @param function
     */
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Iterator it = this.values().iterator();
        Entry<K, V> val = null;
        while (it.hasNext()) {
            val = (Entry<K, V>) it.next();
            function.apply(val.getKey(), val.getValue());
        }
    }

    class MapIterator implements Iterator {

        Iterator iterator = defaultDiskMap.values().iterator();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Object next() {
            long reference = (long) iterator.next();
            return findNodeAtPosition(reference);
        }
    }

    class HashMapIterator implements Iterator {
        Iterator otherMapIterator;
        Iterator cursorIterator = null;

        boolean isDictionary = false;

        public HashMapIterator() {
            otherMapIterator = defaultDiskMap.values().iterator();
        }

        public HashMapIterator(boolean isDictionary) {
            this.isDictionary = isDictionary;
            otherMapIterator = defaultDiskMap.values().iterator();
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
                    return false;

                SkipListHeadNode node = findNodeAtPosition(mapReference);
                setHead(node);
                if (isDictionary)
                    cursorIterator = MultiMapIterator.super.dictionarySet().iterator();
                else
                    cursorIterator = MultiMapIterator.super.entrySet().iterator();
                hasNext = cursorIterator.hasNext();
            }

            return hasNext;
        }

        @Override
        public Object next() {
            return cursorIterator.next();
        }
    }

    class HashMapKeyIterator extends MultiMapIterator.HashMapIterator {
        public HashMapKeyIterator() {
            super(false);
        }

        @Override
        public Object next() {
            return ((Entry) super.next()).getKey();
        }
    }

    class HashMapValueIterator extends MultiMapIterator.HashMapIterator {
        @Override
        public Object next() {
            return ((Entry) super.next()).getValue();
        }
    }

    class KeySet extends MultiMapIterator.EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapIterator.HashMapKeyIterator();
        }
    }

    class DictionarySet extends MultiMapIterator.EntrySet {

        @Override
        public Iterator iterator() {
            return new MultiMapIterator.HashMapIterator(true);
        }
    }


    class MapSet extends MultiMapIterator.EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapIterator.MapIterator();
        }
    }

    class ValueSet extends MultiMapIterator.EntrySet {
        @Override
        public Iterator iterator() {
            return new MultiMapIterator.HashMapValueIterator();
        }
    }

    class EntrySet implements Set {
        @Override
        public Iterator iterator() {
            return new MultiMapIterator.HashMapIterator();
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
