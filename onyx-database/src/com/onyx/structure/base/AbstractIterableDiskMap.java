package com.onyx.structure.base;

import com.onyx.structure.node.BitMapNode;
import com.onyx.structure.node.Header;
import com.onyx.structure.node.RecordReference;
import com.onyx.structure.store.Store;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Created by timothy.osborn on 3/26/15.
 */
public abstract class AbstractIterableDiskMap<K, V> extends AbstractCachedBitMap<K,V> implements Map<K, V> {

    protected LevelReadWriteLock readWriteLock = new DefaultLevelReadWriteLock();

    /**
     * Constructor
     *
     * @param store
     */
    public AbstractIterableDiskMap(Store store, Header header) {
        super(store, header);
    }

    /**
     * Constructor
     *
     * @param fileStore
     * @param header
     * @param headless
     */
    public AbstractIterableDiskMap(Store fileStore, Header header, boolean headless)
    {
        super(fileStore, header, headless);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Iterate-able properties on structure
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////


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
        return new ReferenceCollection<>();
    }

    /**
     * Getter for set of keys
     *
     * @return
     * @see AbstractIterableAbstractIterableDiskMap.this.KeyCollection
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     */
    @Override
    public Set<K> keySet() {
        return new KeyCollection<>();
    }


    /**
     * Getter for set of keys
     *
     * @return
     * @see AbstractIterableAbstractIterableDiskMap.this.DictionaryCollection
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     */
    public Set<Map> dictionarySet() {
        return new DictionaryCollection<>();
    }


    /**
     * Getter for values
     *
     * @return
     * @see AbstractIterableAbstractIterableDiskMap.this.KeyCollection
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     */
    @Override
    public Collection<V> values() {
        return new ValueCollection<>();
    }

    /**
     * Getter for values.  This contains lazy loaded disk structure entries
     *
     * @return
     * @see AbstractIterableAbstractIterableDiskMap.this.KeyCollection
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     * @see AbstractIterableAbstractIterableDiskMap.this.DiskMapEntry
     */

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntryCollection();
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

    /**
     * Class for sifting through values
     *
     * @param <V>
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     */
    protected class ValueCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param fileStore
         * @param diskMap
         */
        public ValueCollection() {
            super();
        }

        /**
         * Get a new iterator
         *
         * @return
         */
        @Override
        public Iterator<V> iterator() {
            return new ValueIterator(header);

        }

        /**
         * Get a new iterator
         *
         * @return
         */
        public Iterator<V> dictIterator() {
            return new DictionaryIterator(header);

        }

        /**
         * Convert to an array
         *
         * @param a
         * @return
         */
        @Override
        public Object[] toArray(Object[] a) {
            Iterator it = iterator();
            int i = 0;
            while (it.hasNext()) {
                V value = (V) it.next();
                a[i] = value;
                i++;
            }
            return a;
        }
    }

    /**
     * Class for sifting through values
     *
     * @param <V>
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     */
    protected class DictionaryCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param fileStore
         * @param diskMap
         */
        public DictionaryCollection() {
            super();
        }

        /**
         * Get a new iterator
         *
         * @return
         */
        @Override
        public Iterator<V> iterator() {
            return new DictionaryIterator(header);

        }

        /**
         * Convert to an array
         *
         * @param a
         * @return
         */
        @Override
        public Object[] toArray(Object[] a) {
            Iterator it = iterator();
            int i = 0;
            while (it.hasNext()) {
                V value = (V) it.next();
                a[i] = value;
                i++;
            }
            return a;
        }
    }

    /**
     * Key Collection
     *
     * @param <V>
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     */
    protected class KeyCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param fileStore
         * @param diskMap
         */
        public KeyCollection() {
            super();
        }

        /**
         * Iterator that iterates through nodes
         *
         * @return
         */
        @Override
        public Iterator<V> iterator() {
            return new KeyIterator(header);
        }
    }

    /**
     * Reference Collection
     *
     * @param <V>
     * @see AbstractIterableAbstractIterableDiskMap.this.AbstractNodeCollection
     */
    protected class ReferenceCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param fileStore Storage mechanism for data structure
         * @param diskMap outer data structure
         */
        public ReferenceCollection() {
            super();
        }

        /**
         * Iterator that iterates through nodes
         *
         * @return New instance of an Reference Indicator
         */
        @Override
        public Iterator<V> iterator() {
            return new ReferenceIterator(header);
        }
    }

    /**
     * Entry Collection.  Much like KeyCollectionMulti except iterates through entries
     *
     * @param <V>
     */
    protected class EntryCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        public EntryCollection() {
            super();
        }

        @Override
        public Iterator<V> iterator() {
            return new EntryIterator(header);
        }
    }


    /**
     * Abstract SkipListNode Collection.  Holds onto references to all the nodes and fills the node values
     * based on the Bitmap nodes
     *
     * @param <E>
     */
    abstract class AbstractNodeCollection<E> implements Set<E> {

        public AbstractNodeCollection() {
        }

        /**
         * Size, mirrors disk structure
         *
         * @return
         */
        @Override
        public int size() {
            return AbstractIterableDiskMap.this.size();
        }

        /**
         * isEmpty mirrors disk structure
         *
         * @return
         */
        @Override
        public boolean isEmpty() {
            return AbstractIterableDiskMap.this.isEmpty();
        }

        /**
         * To Array
         *
         * @param a
         * @return
         */
        @Override
        public Object[] toArray(Object[] a) {
            Iterator it = iterator();
            int i = 0;
            while (it.hasNext()) {
                V value = (V) it.next();
                a[i] = value;
                i++;
            }
            return a;
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //
        // UNSUPPORTED METHODS
        //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////

        @Override
        @Deprecated
        public Object[] toArray() {
            return new Object[0];
        }

        @Override
        @Deprecated
        public boolean contains(Object o) {
            return AbstractIterableDiskMap.this.containsValue(o);
        }

        @Deprecated
        @Override
        public boolean add(E o) {
            return false;
        }

        @Deprecated
        @Override
        public boolean remove(Object o) {
            return false;
        }

        @Deprecated
        @Override
        public boolean addAll(Collection<? extends E> c) {
            return false;
        }

        @Override
        @Deprecated
        public void clear() {

        }

        @Override
        @Deprecated
        public boolean retainAll(Collection c) {
            return false;
        }

        @Override
        @Deprecated
        public boolean removeAll(Collection c) {
            return false;
        }

        @Override
        @Deprecated
        public boolean containsAll(Collection c) {
            return false;
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //
        // END UNSUPPORTED METHODS
        //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    }

    /**
     * Value Iterator.
     * <p/>
     * Iterates through and hydrates the values in an DiskMap
     */
    class ValueIterator extends AbstractNodeIterator implements Iterator {
        /**
         * Constructor
         *
         * @param header
         */
        public ValueIterator(Header header) {
            super(header);
        }

        /**
         * Next
         *
         * @return
         */

        @Override
        public Object next() {
            final RecordReference reference = (RecordReference) super.next();
            if (reference != null) {
                return getRecordValue(reference);
            }
            return null;
        }
    }

    /**
     * Key Iterator.  Same as Value iterator except returns just the keys
     */
    class KeyIterator extends AbstractNodeIterator implements Iterator {
        public KeyIterator(Header header) {
            super(header);
        }

        @Override
        public Object next() {
            final RecordReference reference = (RecordReference) super.next();
            if (reference != null) {
                return getRecordKey(reference);
            }
            return null;
        }
    }

    /**
     * Reference Iterator.  Same as AbstractNodeIterator iterator except returns just the keys
     */
    class ReferenceIterator extends AbstractNodeIterator implements Iterator {
        public ReferenceIterator(Header header) {
            super(header);
        }

        @Override
        public Object next() {
            return super.next();
        }
    }

    /**
     * Entry.  Similar to the Key and Value iterator except it returns a custom entry that will lazy load the keys and values
     */
    class EntryIterator extends AbstractNodeIterator implements Iterator {
        /**
         * Constructor
         *
         * @param header
         */
        public EntryIterator(Header header) {
            super(header);
        }

        /**
         * Next
         *
         * @return
         */
        @Override
        public Object next() {



            final RecordReference reference = (RecordReference) super.next();
            if (reference != null) {
                return new DiskMapEntry(reference);
            }
            return null;
        }
    }

    /**
     * Dictionary Iterator.
     * <p/>
     * Iterates through and hydrates the values in an DiskMap
     */
    class DictionaryIterator extends AbstractNodeIterator implements Iterator {
        /**
         * Constructor
         *
         * @param header
         */
        public DictionaryIterator(Header header) {
            super(header);
        }

        /**
         * Next
         *
         * @return
         */

        @Override
        public Object next() {
            final RecordReference reference = (RecordReference) super.next();
            if (reference != null) {
                return getRecordValueAsDictionary(reference);
            }
            return null;
        }
    }

    /**
     * Disk Map Entry
     *
     * @param <K>
     * @param <V>
     */
    public class DiskMapEntry<K, V> implements Entry<K, V> {
        protected RecordReference node = null;
        protected K key;
        protected V value;

        /**
         * Constructor
         *
         * @param node
         */
        public DiskMapEntry(RecordReference node) {
            this.node = node;
        }

        /**
         * Get Key
         *
         * @return
         */
        @Override
        public K getKey() {
            if (key == null) // Lazy load the key
                key = (K) getRecordKey(node);

            return key;
        }

        /**
         * Get Value
         *
         * @return
         */
        @Override
        public V getValue() {
            if (value == null) // Lazy load the key
                value = (V) getRecordValue(node);
            return value;
        }

        @Override
        public V setValue(V value) {
            this.value = value;
            return value;
        }
    }

    /**
     * Abstract SkipListNode iterator
     * <p/>
     * Iterates through nodes and gets the left, right, next values
     */
    abstract class AbstractNodeIterator implements Iterator {

        protected Stack<NodeEntry> nodeStack = new Stack<NodeEntry>(); // Simple stack that hold onto the nodes
        protected Stack<Long> referenceStack = new Stack<Long>(); // Simple stack that hold onto the nodes

        /**
         * Constructor
         *
         * @param header
         */
        public AbstractNodeIterator(Header header) {
            if (header.firstNode > 0) {
                nodeStack.push(new NodeEntry(header.firstNode, (short) -1));
            }
            queueUpNext();

        }

        /**
         * Hash next,  only if the stack is not empty
         *
         * @return
         */
        @Override
        public boolean hasNext() {
            return (referenceStack.size() > 0);
        }

        /**
         * This gets the next
         */
        public void queueUpNext() {
            NodeEntry nodeEntry = null;
            BitMapNode node = null;

            NodeEntry newEntry = null;
            long reference = 0;

            while (nodeStack.size() > 0) {

                nodeEntry = nodeStack.pop();
                node = getBitmapNode(nodeEntry.reference);

                // Add all the other related nodes in the bitmap
                for (int i = 0; i < getLoadFactor(); i++) {

                    reference = node.next[i];
                    if (reference > 0) {
                        if (nodeEntry.level < (getRecordReferenceIndex())) {
                            newEntry = new NodeEntry(reference, (short) (nodeEntry.level + 1));
                            nodeStack.add(newEntry);
                        } else {
                            referenceStack.push(reference);
                        }
                    }
                }
            }
        }

        /**
         * Next, pop it off the stack
         *
         * @return
         */
        @Override
        public Object next()
        {
                if (referenceStack.size() < 1) {
                    queueUpNext();
                }

                if (referenceStack.size() > 0) {
                    RecordReference reference = getRecordReference(referenceStack.pop());
                    while (reference.next > 0) {
                        referenceStack.push(reference.next);
                        reference = getRecordReference(reference.next);
                    }
                    return reference;
                }

            return null;
        }

        /**
         * This class holds references of nodes that were scanned while looking for the next reference
         */
        protected class NodeEntry {
            public NodeEntry(long reference, short level) {
                this.reference = reference;
                this.level = level;
            }

            public long reference;
            public short level;

            @Override
            public int hashCode() {
                return new Long(reference).hashCode();
            }

            @Override
            public boolean equals(Object val) {
                if (val == null)
                    return false;

                if (val.getClass() == NodeEntry.class) {
                    return ((NodeEntry) val).reference == reference;
                }
                return false;
            }
        }
    }
}
