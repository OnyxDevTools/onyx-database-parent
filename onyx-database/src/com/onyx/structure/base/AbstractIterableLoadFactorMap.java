package com.onyx.structure.base;

import com.onyx.structure.DefaultDiskMap;
import com.onyx.structure.node.*;
import com.onyx.structure.store.Store;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Created by tosborn1 on 1/9/17.
 */
public class AbstractIterableLoadFactorMap<K,V> extends DiskSkipList<K,V> {

    protected DefaultDiskMap<K, DiskSkipList<K, V>> defaultDiskMap = null;

    /**
     * Constructor
     *
     * @param store
     */
    public AbstractIterableLoadFactorMap(Store store, Header header, boolean isHeadless) {
        super(store, header, isHeadless);
        entries = new EntryCollectionMulti(fileStore, this);
        values = new ValueCollectionMulti(fileStore, this);
        keys = new KeyCollectionMulti(fileStore, this);
        dict = new DictionaryCollectionMulti(fileStore, this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Iterate-able properties on structure
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Getter for set of keys
     *
     * @return
     * @see KeyCollectionMulti
     * @see AbstractMultiNodeCollection
     */
    protected KeyCollectionMulti keys;

    @Override
    public Set<K> keySet() {
        return keys;
    }


    /**
     * Getter for set of keys
     *
     * @return
     * @see DictionaryCollectionMulti
     * @see AbstractMultiNodeCollection
     */
    protected DictionaryCollectionMulti dict;

    public Set<Map> dictionarySet() {
        return dict;
    }

    // Getter for super entry set for the load factor map
    public Set<Entry<K, V>> superEntrySet() {
        return super.entrySet();
    }

    // Getter for super dictionary set for the load factor map
    public Set<Map> superDictionarySet() {
        return super.dictionarySet();
    }

    /**
     * Getter for values
     *
     * @return
     * @see KeyCollectionMulti
     * @see AbstractMultiNodeCollection
     */

    protected ValueCollectionMulti values;

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
    protected EntryCollectionMulti entries;

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

    /**
     * Class for sifting through values
     *
     * @param <V>
     * @see AbstractMultiNodeCollection
     */
    protected class ValueCollectionMulti<V> extends AbstractMultiNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param fileStore
         * @param diskMap
         */
        public ValueCollectionMulti(Store fileStore, AbstractIterableLoadFactorMap diskMap) {
            super(fileStore, diskMap);
            this.fileStore = fileStore;
            this.diskMap = diskMap;
        }

        /**
         * Get a new iterator
         *
         * @return
         */
        @Override
        public Iterator<V> iterator() {
            return new AbstractIterableLoadFactorMap.ValueIterator(header);

        }

        /**
         * Get a new iterator
         *
         * @return
         */
        public Iterator<V> dictIterator() {
            return new AbstractIterableLoadFactorMap.DictionaryIterator(header);

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
     * @see AbstractMultiNodeCollection
     */
    protected class DictionaryCollectionMulti<V> extends AbstractMultiNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param fileStore
         * @param diskMap
         */
        public DictionaryCollectionMulti(Store fileStore, AbstractIterableLoadFactorMap diskMap) {
            super(fileStore, diskMap);
            this.fileStore = fileStore;
            this.diskMap = diskMap;
        }

        /**
         * Get a new iterator
         *
         * @return
         */
        @Override
        public Iterator<V> iterator() {
            return new AbstractIterableLoadFactorMap.DictionaryIterator(header);

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
     * @see AbstractMultiNodeCollection
     */
    protected class KeyCollectionMulti<V> extends AbstractMultiNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param fileStore
         * @param diskMap
         */
        public KeyCollectionMulti(Store fileStore, AbstractIterableLoadFactorMap diskMap) {
            super(fileStore, diskMap);
            this.fileStore = fileStore;
            this.diskMap = diskMap;
        }

        /**
         * Iterator that iterates through nodes
         *
         * @return
         */
        @Override
        public Iterator<V> iterator() {
            return new AbstractIterableLoadFactorMap.KeyIterator(header);
        }
    }

    /**
     * Entry Collection.  Much like KeyCollectionMulti except iterates through entries
     *
     * @param <V>
     */
    protected class EntryCollectionMulti<V> extends AbstractMultiNodeCollection<V> implements Collection<V> {
        public EntryCollectionMulti(Store fileStore, AbstractIterableLoadFactorMap diskMap) {
            super(fileStore, diskMap);
            this.fileStore = fileStore;
            this.diskMap = diskMap;
        }

        @Override
        public Iterator<V> iterator() {
            return new AbstractIterableLoadFactorMap.EntryIterator(header);
        }
    }


    /**
     * Abstract SkipListNode Collection.  Holds onto references to all the nodes and fills the node values
     * based on the Bitmap nodes
     *
     * @param <E>
     */
    abstract class AbstractMultiNodeCollection<E> implements Set<E> {
        protected Store fileStore; // Reference to outer document fileStore

        protected AbstractIterableLoadFactorMap diskMap; // Just a handle on the outer class

        public AbstractMultiNodeCollection(Store fileStore, AbstractIterableLoadFactorMap diskMap) {
            this.fileStore = fileStore;
            this.diskMap = diskMap;
        }

        /**
         * Size, mirrors disk structure
         *
         * @return
         */
        @Override
        public int size() {
            return diskMap.size();
        }

        /**
         * isEmpty mirrors disk structure
         *
         * @return
         */
        @Override
        public boolean isEmpty() {
            return diskMap.isEmpty();
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
            return diskMap.containsValue(o);
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
    class ValueIterator extends AbstractIterableLoadFactorMap.AbstractNodeIterator implements Iterator {
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
            final Entry<K,V> entry = (Entry<K,V> )super.next();
            if (entry != null) {
                return entry.getValue();
            }
            return null;
        }
    }

    /**
     * Key Iterator.  Same as Value iterator except returns just the keys
     */
    class KeyIterator extends AbstractIterableLoadFactorMap.AbstractNodeIterator implements Iterator {
        public KeyIterator(Header header) {
            super(header);
        }

        @Override
        public Object next() {
            final Entry<K,V> entry = (Entry<K,V> )super.next();
            if (entry != null) {
                return entry.getKey();
            }
            return null;
        }
    }

    /**
     * Entry.  Similar to the Key and Value iterator except it returns a custom entry that will lazy load the keys and values
     */
    class EntryIterator extends AbstractIterableLoadFactorMap.AbstractNodeIterator implements Iterator {
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
            return super.next();
        }
    }

    /**
     * Dictionary Iterator.
     * <p/>
     * Iterates through and hydrates the values in an DiskMap
     */
    class DictionaryIterator extends AbstractIterableLoadFactorMap.AbstractNodeIterator implements Iterator {
        /**
         * Constructor
         *
         * @param header
         */
        public DictionaryIterator(Header header) {
            super(header, true);
        }

        /**
         * Next
         *
         * @return
         */

        @Override
        public Object next() {
            return super.next();
        }
    }

    /**
     * Abstract SkipListNode iterator
     * <p/>
     * Iterates through nodes and gets the left, right, next values
     */
    abstract class AbstractNodeIterator implements Iterator {

        protected Stack<AbstractIterableLoadFactorMap.AbstractNodeIterator.NodeEntry> nodeStack = new Stack<AbstractIterableLoadFactorMap.AbstractNodeIterator.NodeEntry>(); // Simple stack that hold onto the nodes
        protected Stack<Long> referenceStack = new Stack<Long>(); // Simple stack that hold onto the nodes
        protected Iterator currentIterator = null;
        protected boolean isDictionary = false;

        /**
         * Constructor
         *
         * @param header
         */
        public AbstractNodeIterator(Header header) {
            if (header.firstNode > 0) {
                nodeStack.push(new AbstractIterableLoadFactorMap.AbstractNodeIterator.NodeEntry(header.firstNode, (short) -1));
            }
            queueUpNext();
        }

        /**
         * Constructor
         *
         * @param header
         */
        public AbstractNodeIterator(Header header, boolean isDictionary) {
            this.isDictionary = isDictionary;
            if (header.firstNode > 0) {
                nodeStack.push(new AbstractIterableLoadFactorMap.AbstractNodeIterator.NodeEntry(header.firstNode, (short) -1));
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
            prepareNext();
            return (currentIterator != null && currentIterator.hasNext());
        }


        public void queueUpNext() {
            AbstractNodeIterator.NodeEntry nodeEntry = null;
            BitMapNode node = null;

            AbstractNodeIterator.NodeEntry newEntry = null;
            long reference = 0;

            while (nodeStack.size() > 0) {

                nodeEntry = nodeStack.pop();
                node = defaultDiskMap.getBitmapNode(nodeEntry.reference);

                // Add all the other related nodes in the bitmap
                for (int i = 0; i < defaultDiskMap.getLoadFactor(); i++) {

                    reference = node.next[i];
                    if (reference > 0) {
                        if (nodeEntry.level < (loadFactor - 2)) {
                            newEntry = new AbstractNodeIterator.NodeEntry(reference, (short) (nodeEntry.level + 1));
                            nodeStack.add(newEntry);
                        } else {
                            referenceStack.push(reference);
                        }
                    }
                }
            }
        }

        private void prepareNext()
        {
            if(currentIterator != null && currentIterator.hasNext())
                return;

            boolean continueLooking = true;
            while (continueLooking && referenceStack.size() > 0) {
                queueUpNext();

                while (referenceStack.size() > 0) {
                    SkipListHeadNode node = findNodeAtPosition(referenceStack.pop());
                    setHead(node);
                    if (isDictionary)
                        currentIterator = superDictionarySet().iterator();
                    else
                        currentIterator = superEntrySet().iterator();
                    if (currentIterator.hasNext())
                    {
                        continueLooking = false;
                        break;
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
            prepareNext();
            return currentIterator.next();
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

                if (val.getClass() == AbstractIterableLoadFactorMap.AbstractNodeIterator.NodeEntry.class) {
                    return ((AbstractIterableLoadFactorMap.AbstractNodeIterator.NodeEntry) val).reference == reference;
                }
                return false;
            }
        }
    }

}
