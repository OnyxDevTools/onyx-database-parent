package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.node.SkipListNode;
import com.onyx.structure.store.Store;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Created by tosborn1 on 1/7/17.
 *
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.  This contains the iteration part of a map.  This is abstracted out just so we
 * can isolate only the stuff that makes pertains to looping.
 *
 * @since 1.2.0
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 */
@SuppressWarnings("unchecked")
abstract class AbstractIterableSkipList<K,V> extends AbstractCachedSkipList<K,V> implements Map<K,V> {

    /**
     * Constructor with store.  Initialize the collection types
     * @param store Underlying storage mechanism
     * @param header Header location of the skip list
     * @since 1.2.0
     */
    AbstractIterableSkipList(Store store, Header header) {
        super(store, header);
        entries = new EntryCollection(this);
        values = new ValueCollection(this);
        keys = new KeyCollection(this);
        dict = new DictionaryCollection(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Iterate-able properties on structure
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    protected KeyCollection keys;

    /**
     * Getter for set of keys
     *
     * @return the keys collection
     * @see KeyCollection
     * @see AbstractNodeCollection
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<K> keySet() {
        return keys;
    }

    private DictionaryCollection dict;

    /**
     * Getter for set of keys.  This is meant to iterate through the values as maps rather than
     * hard structured objects
     *
     * @return The dictionary collection
     * @see DictionaryCollection
     * @see AbstractNodeCollection
     */
    @SuppressWarnings({"unused", "unchecked"})
    public Set<Map> dictionarySet() {
        return dict;
    }

    private ValueCollection values;

    /**
     * Getter for values
     *
     * @return Values collection
     * @see KeyCollection
     * @see AbstractNodeCollection
     */
    @Override
    @SuppressWarnings("unchecked")
    public Collection<V> values() {
        return values;
    }

    private EntryCollection entries;

    /**
     * Getter for values.  This contains lazy loaded disk structure entries
     *
     * @return Entry set for Skip List
     * @see KeyCollection
     * @see AbstractNodeCollection
     * @see SkipListEntry
     */
    @Override
    @SuppressWarnings("unchecked")
    public Set<Map.Entry<K, V>> entrySet() {
        return entries;
    }

    /**
     * For Each Iterator
     *
     * @param action consumer
     */
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Iterator it = this.entrySet().iterator();
        Map.Entry<K, V> val;
        while (it.hasNext()) {
            val = (Map.Entry<K, V>) it.next();
            action.accept(val.getKey(), val.getValue());
        }
    }

    /**
     * Not sure if this is supported correctly but, Its filled out
     *
     * @param function BiFunction
     */
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Iterator it = this.values().iterator();
        Map.Entry<K, V> val;
        while (it.hasNext()) {
            val = (Map.Entry<K, V>) it.next();
            function.apply(val.getKey(), val.getValue());
        }
    }

    /**
     * Class for sifting through values
     *
     * @param <V> Value type
     * @see AbstractNodeCollection
     */
    private class ValueCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param skipList Reference to outer skip list
         */
        ValueCollection(AbstractIterableSkipList skipList) {
            super(skipList);
            this.skipList = skipList;
        }

        /**
         * Get a new iterator
         *
         * @return New Value Iterator
         */
        @Override
        public Iterator<V> iterator() {
            return new ValueIterator();

        }

        /**
         * Get a new iterator
         *
         * @return New Dictianry Iterator
         */
        @SuppressWarnings("unused")
        public Iterator<V> dictIterator() {
            return new DictionaryIterator();

        }

        /**
         * Convert to an array
         *
         * @param a Array to put objects into
         * @return The same array you sent us.  Seems kinda redundant Oracle....
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
     * @param <V> Values as Dictionary
     * @see AbstractNodeCollection
     */
    private class DictionaryCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param skipList Reference to outer skip list
         */
        DictionaryCollection(AbstractIterableSkipList skipList) {
            super(skipList);
            this.skipList = skipList;
        }

        /**
         * Get a new iterator
         *
         * @return New Dictionary iterator
         */
        @Override
        public Iterator<V> iterator() {
            return new DictionaryIterator();

        }

        /**
         * Convert to an array
         *
         * @param a Array to put objects into
         * @return The same array you sent us.  Seems kinda redundant Oracle....
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
     * @param <V> Key Types
     * @see AbstractNodeCollection
     */
    private class KeyCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        /**
         * Constructor
         *
         * @param skipList Reference to outer skip list
         */
        KeyCollection(AbstractIterableSkipList skipList) {
            super(skipList);
            this.skipList = skipList;
        }

        /**
         * Iterator that iterates through nodes
         *
         * @return New Key iterator
         */
        @Override
        public Iterator<V> iterator() {
            return new KeyIterator();
        }
    }

    /**
     * Entry Collection.  Much like KeyCollection except iterates through entries
     *
     * @param <V> Entry Types
     */
    private class EntryCollection<V> extends AbstractNodeCollection<V> implements Collection<V> {
        EntryCollection(AbstractIterableSkipList skipList) {
            super(skipList);
            this.skipList = skipList;
        }

        @Override
        public Iterator<V> iterator() {
            return new EntryIterator();
        }
    }


    /**
     * Value Iterator.
     * <p/>
     * Iterates through and hydrates the values in an DiskMap
     */
    private class ValueIterator extends AbstractNodeIterator implements Iterator {
        /**
         * Constructor
         */
        ValueIterator() {
            super();
        }

        /**
         * Next Get the next value
         *
         * @return The value from the node
         */
        @Override
        public Object next() {
            final SkipListNode<K> next = (SkipListNode<K>)super.next();
            return findValueAtPosition(next.recordPosition, next.recordSize);
        }
    }

    /**
     * Key Iterator.  Same as Value iterator except returns just the keys
     */
    private class KeyIterator extends AbstractNodeIterator implements Iterator {

        /**
         * Constructor
         */
        KeyIterator() {
            super();
        }

        /**
         * Get the next key object
         * @return next key object
         */
        @Override
        public Object next() {
            final SkipListNode<K> next = (SkipListNode<K>)super.next();
            return next.key;
        }
    }

    /**
     * Entry.  Similar to the Key and Value iterator except it returns a custom entry that will lazy load the keys and values
     */
    private class EntryIterator extends AbstractNodeIterator implements Iterator {
        /**
         * Constructor
         */
        EntryIterator() {
            super();
        }

        /**
         * Next Entry
         *
         * @return Next Entry based on the next node
         */
        @Override
        public Object next() {

            final SkipListNode<K> next = (SkipListNode<K>)super.next();
            return new SkipListEntry<K,V>(next);
        }
    }

    /**
     * Disk Map Entry
     *
     * @param <K> Key Type
     * @param <V> Vaue Type
     */
    public class SkipListEntry<K, V> implements Entry<K, V> {
        protected SkipListNode<K> node = null;
        protected K key;
        protected V value;

        /**
         * Constructor
         *
         * @param node Underlying node for the entry
         */
        SkipListEntry(SkipListNode<K> node) {
            this.node = node;
        }

        /**
         * Get Key
         *
         * @return Key from the node
         */
        @Override
        public K getKey() {
            return node.key;
        }

        /**
         * Get Value
         *
         * @return Value from the node position
         */
        @Override
        public V getValue() {
            if (value == null) // Lazy load the key
                value = (V) findValueAtPosition(node.recordPosition, node.recordSize);
            return value;
        }

        /**
         * Override the value in the collection.  Not the map.
         *
         * @param value Value to override to
         * @return The same damn value we set.  Damnit Oracle.  WTF is the purpose of that?
         */
        @Override
        public V setValue(V value) {
            this.value = value;
            return value;
        }
    }

    /**
     * Dictionary Iterator.
     * <p/>
     * Iterates through and hydrates the values in an DiskMap
     */
    private class DictionaryIterator extends AbstractNodeIterator implements Iterator {
        /**
         * Constructor
         *
         */
        DictionaryIterator() {
            super();
        }

        /**
         * Next Dictionary Object
         *
         * @return The next dictionary object
         */

        @Override
        public Object next() {
            final SkipListNode<K> next = (SkipListNode<K>)super.next();
            if (next != null) {
                return getRecordValueAsDictionary(next);
            }
            return null;
        }
    }


    /**
     * Abstract SkipListNode iterator
     * <p/>
     * Iterates through nodes and gets the left, right, next values
     */
    abstract class AbstractNodeIterator implements Iterator {

        private SkipListNode current;

        /**
         * Constructor
         *
         */
        public AbstractNodeIterator() {
            current = head;
            while (current.down != 0L)
                current = findNodeAtPosition(current.down);

            // Lets find a non header node
            while(current != null && current.recordPosition == 0L)
            {
                if (current.next != 0L)
                    current = findNodeAtPosition(current.next);
                else
                    current = null;
            }
        }

        /**
         * Has next.  Only if there are remaining objects
         *
         * @return Whether the node has a record or not
         */
        @Override
        public boolean hasNext() {
            return !(current == null || current.recordPosition == 0L);
        }

        /**
         * Next, find the next node with a record associated to it.
         *
         * @return Nex node with a record value.
         */
        @Override
        public Object next()
        {
            if(current == null)
                return null;

            SkipListNode previous = current;
            while(current != null)
            {
                if (current.next != 0L)
                    current = findNodeAtPosition(current.next);
                else
                    current = null;

                if(current != null && current.recordPosition != 0L)
                    break;
            }

            return previous;
        }
    }

    /**
     * Abstract SkipListNode Collection.  Holds onto references to all the nodes and fills the node values
     * based on the Bitmap nodes
     *
     * @param <E> Collection type.
     */
    @SuppressWarnings("SuspiciousMethodCalls")
    abstract class AbstractNodeCollection<E> implements Set<E> {

        protected Store fileStore; // Reference to outer document fileStore

        AbstractIterableSkipList<K,V> skipList; // Just a handle on the outer class

        /**
         * Constructor
         * @param skipList Outer skip list reference
         */
        AbstractNodeCollection(AbstractIterableSkipList skipList) {
            this.skipList = skipList;
        }

        /**
         * Size, mirrors disk structure
         *
         * @return Int value of the size.  Only works if its small enough to be an int.  Also note, this is not concurrent
         * It sends the size at the time of method invocation not creating the sub collection.
         */
        public int size() {
            return (int)skipList.longSize();
        }

        /**
         * isEmpty mirrors disk structure
         *
         * @return Whether the outer skipList reference is empty
         */
        @Override
        public boolean isEmpty() {
            return skipList.isEmpty();
        }

        /**
         * To Array
         *
         * @param a Array to populate
         * @return Same array you sent in.
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
            return skipList.containsValue(o);
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

}
