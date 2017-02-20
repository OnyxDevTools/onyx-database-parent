package com.onyx.diskmap.base.skiplist;

import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.node.SkipListHeadNode;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.diskmap.store.Store;

import java.util.*;

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
public abstract class AbstractIterableSkipList<K,V> extends AbstractCachedSkipList<K,V> implements Map<K,V> {

    /**
     * Constructor with store.  Initialize the collection types
     * @param store Underlying storage mechanism
     * @param header Header location of the skip list
     * @since 1.2.0
     */
    protected AbstractIterableSkipList(Store store, Header header) {
        super(store, header);
    }

    /**
     * Constructor with store.  Initialize the collection types
     * @param store Underlying storage mechanism
     * @param header Header location of the skip list
     * @param headless Whether the header should be ignored
     * @since 1.2.0
     */
    protected AbstractIterableSkipList(Store store, Header header, boolean headless) {
        super(store, header, headless);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Iterate-able properties on structure
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Getter for set of keys
     *
     * @return the keys collection
     * @see KeyCollection
     * @see AbstractNodeCollection
     */
    @Override
    public Set referenceSet() {
        return new ReferenceCollection();
    }

    /**
     * Getter for set of keys
     *
     * @return the keys collection
     * @see KeyCollection
     * @see AbstractNodeCollection
     */
    @Override
    public Set<K> keySet() {
        return new KeyCollection();
    }

    /**
     * Getter for set of keys.  This is meant to iterate through the values as maps rather than
     * hard structured objects
     *
     * @return The dictionary collection
     * @see DictionaryCollection
     * @see AbstractNodeCollection
     */
    public Set<Map> dictionarySet() {
        return new DictionaryCollection();
    }

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
        return new ValueCollection();
    }


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
        return new EntryCollection();
    }

    /**
     * Class for sifting through values
     *
     * @see AbstractNodeCollection
     */
    private class ValueCollection extends AbstractNodeCollection {
        @Override
        public Iterator iterator() {
            return new ValueIterator();
        }
    }

    /**
     * Class for sifting through values
     *
     * @see AbstractNodeCollection
     */
    private class DictionaryCollection extends AbstractNodeCollection  {
        @Override
        public Iterator iterator() {
            return new DictionaryIterator();
        }
    }

    /**
     * Key Collection
     *
     * @see AbstractNodeCollection
     */
    private class KeyCollection extends AbstractNodeCollection {
        @Override
        public Iterator<V> iterator() {
            return new KeyIterator();
        }
    }

    /**
     * Key Collection
     *
     * @see AbstractNodeCollection
     */
    private class ReferenceCollection extends AbstractNodeCollection {
        @Override
        public Iterator<V> iterator() {
            return new ReferenceIterator();
        }
    }

    /**
     * Entry Collection.  Much like KeyCollection except iterates through entries
     *
     */
    private class EntryCollection extends AbstractNodeCollection {
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
     * Key Iterator.  Same as Value iterator except returns just the keys
     */
    private class ReferenceIterator extends AbstractNodeIterator implements Iterator {}

    /**
     * Entry.  Similar to the Key and Value iterator except it returns a custom entry that will lazy load the keys and values
     */
    private class EntryIterator extends AbstractNodeIterator implements Iterator {
        /**
         * Next Entry
         *
         * @return Next Entry based on the next node
         */
        @Override
        public Object next() {

            final SkipListNode<K> next = (SkipListNode<K>)super.next();
            return new SkipListEntry(next);
        }
    }

    /**
     * Dictionary Iterator.
     * <p/>
     * Iterates through and hydrates the values in an DiskMap
     */
    private class DictionaryIterator extends AbstractNodeIterator implements Iterator {

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

        private SkipListHeadNode current;

        /**
         * Constructor
         *
         */
        AbstractNodeIterator() {
            current = getHead();
            while (current.down != 0L)
                current = findNodeAtPosition(current.down);

            // Lets find a non header node
            while(current != null && !(current instanceof SkipListNode))
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
            return current instanceof SkipListNode;
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

            SkipListHeadNode previous = current;
            while(current != null)
            {
                if (current.next != 0L)
                    current = findNodeAtPosition(current.next);
                else
                    current = null;

                if(current != null && current instanceof SkipListNode)
                    break;
            }

            return previous;
        }
    }

    /**
     * Abstract SkipListNode Collection.  Holds onto references to all the nodes and fills the node values
     * based on the Bitmap nodes
     *
     */
    abstract class AbstractNodeCollection extends AbstractSet {
        /**
         * Size, mirrors disk structure
         *
         * @return Int value of the size.  Only works if its small enough to be an int.  Also note, this is not concurrent
         * It sends the size at the time of method invocation not creating the sub collection.
         */
        public int size() {
            return (int)AbstractIterableSkipList.this.longSize();
        }
    }

    /**
     * Disk Map Entry
     *
     */
    private class SkipListEntry implements Entry {
        protected SkipListNode node = null;
        protected Object key;
        protected Object value;

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
        public Object getKey() {
            return node.key;
        }

        /**
         * Get Value
         *
         * @return Value from the node position
         */
        @Override
        public Object getValue() {
            if (value == null) // Lazy load the key
                value = findValueAtPosition(node.recordPosition, node.recordSize);
            return value;
        }

        /**
         * Override the value in the collection.  Not the map.
         *
         * @param value Value to override to
         * @return The same damn value we set.  Damnit Oracle.  WTF is the purpose of that?
         */
        @Override
        public Object setValue(Object value) {
            this.value = value;
            return value;
        }
    }
}
