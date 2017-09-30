package com.onyx.diskmap.base.hashmatrix;

import com.onyx.diskmap.data.HashMatrixNode;
import com.onyx.diskmap.data.Header;
import com.onyx.diskmap.data.SkipListHeadNode;
import com.onyx.diskmap.store.Store;

import java.util.*;

/**
 * Created by tosborn1 on 1/9/17.
 *
 * This class manages the iteration behavior of a multi indexed map.  The first index being a hash matrix.  The second,
 * is a skip list.
 *
 * So, first it will iterate through the hash matrix, and grab a reference to each second index being the rerence to the
 * skip lists.
 *
 * @since 1.2.0
 */
@SuppressWarnings("unchecked")
public abstract class AbstractIterableMultiMapHashMatrix<K,V> extends AbstractCachedHashMatrix<K,V> implements Map<K, V> {

    /**
     * Constructor, initializes cache
     *
     * @param store Storage volume for hash matrix
     * @param header Head of the data structure
     * @param detached Whether the object is detached.  If it is detached, ignore writing
     *                 values to the header
     *
     * @since 1.2.0
     */
    protected AbstractIterableMultiMapHashMatrix(Store store, Header header, @SuppressWarnings("SameParameterValue") boolean detached) {
        super(store, header, detached);
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Getters for Sets
    //
    ///////////////////////////////////////////////////////////////////////

    @Override
    public Set referenceSet() {
        return new MultiMapReferenceSet();
    }

    @Override
    public Set<K> keySet() {
        return new MultiMapKeySet();
    }

    @Override
    public Collection<V> values() {
        return new MultiMapValueSet();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new MultiMapEntrySet();
    }

    public Set<Map> dictionarySet() {
        return new MultiMapDictionarySet();
    }

    protected Set mapSet() {
        return new SkipListMapSet();
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Set Declarations
    //
    ///////////////////////////////////////////////////////////////////////

    private class MultiMapValueSet extends AbstractMultiMapSet {
        @Override
        public Iterator iterator() {
            return new AbstractIterableMultiMapHashMatrix.ValueIterator();
        }
    }

    private class MultiMapDictionarySet extends AbstractMultiMapSet {
        @Override
        public Iterator<V> iterator() {
            return new AbstractIterableMultiMapHashMatrix.DictionaryIterator();
        }
    }

    private class SkipListMapSet extends AbstractMultiMapSet {
        @Override
        public Iterator iterator() {
            return new AbstractIterableMultiMapHashMatrix.MapIterator();
        }
    }

    private class MultiMapKeySet extends AbstractMultiMapSet {
        @Override
        public Iterator iterator() {
            return new AbstractIterableMultiMapHashMatrix.KeyIterator();
        }
    }

    private class MultiMapReferenceSet extends AbstractMultiMapSet {
        @Override
        public Iterator iterator() {
            return new AbstractIterableMultiMapHashMatrix.ReferenceIterator();
        }
    }

    private class MultiMapEntrySet extends AbstractMultiMapSet {
        @Override
        public Iterator iterator() {
            return new AbstractIterableMultiMapHashMatrix.EntryIterator();
        }
    }

    abstract class AbstractMultiMapSet extends AbstractSet {
        @Override
        public int size() {
            return (int)AbstractIterableMultiMapHashMatrix.this.longSize();
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Iterator Declarations
    //
    ///////////////////////////////////////////////////////////////////////

    private class ValueIterator extends AbstractMultiMapIterator implements Iterator {
        @Override
        public Object next() {
            final Map.Entry<K,V> entry = (Map.Entry<K,V>)super.next();
            if (entry != null) {
                return entry.getValue();
            }
            return null;
        }
    }

    private class KeyIterator extends AbstractMultiMapIterator implements Iterator {
        @Override
        public Object next() {
            final Map.Entry<K,V> entry = (Map.Entry<K,V>)super.next();
            if (entry != null) {
                return entry.getKey();
            }
            return null;
        }
    }

    private class ReferenceIterator extends AbstractMultiMapIterator implements Iterator {
        ReferenceIterator() {
            super();
            isReference = true;
        }
    }

    private class DictionaryIterator extends AbstractMultiMapIterator implements Iterator {
        DictionaryIterator() {
            super(true);
        }
    }

    private class MapIterator extends AbstractMultiMapIterator implements Iterator {
        /**
         * Hash next,  only if the stack is not empty
         *
         * @return Whether there is a sub data structure
         */
        @Override
        public boolean hasNext() {
            queueUpNext();
            return referenceStack.size() > 0;
        }

        /**
         * Next, pop it off the stack
         *
         * @return Returns the nex sub map
         */
        @Override
        public Object next()
        {
            return findNodeAtPosition(referenceStack.pop());
        }
    }

    private class EntryIterator extends AbstractMultiMapIterator implements Iterator {}

    /**
     * This is the base implenetation of the iterators
     *
     * @since 1.2.0
     */
    abstract class AbstractMultiMapIterator implements Iterator {

        final Stack<AbstractIterableMultiMapHashMatrix.NodeEntry> nodeStack = new Stack<>(); // Simple stack that hold onto the nodes
        final Stack<Long> referenceStack = new Stack<>(); // Simple stack that hold onto the nodes
        Iterator currentIterator = null;
        boolean isDictionary = false;
        boolean isReference = false;

        /**
         * Constructor.  Queue up the first reference of a skip list
         */
        AbstractMultiMapIterator() {
            this(false);
        }

        /**
         * Constructor.  Queue up the first reference of a skip list.
         *
         * @param isDictionary Identify if the value is a map/dictionary object or the default entry key value pair
         */
        AbstractMultiMapIterator(boolean isDictionary) {
            this.isDictionary = isDictionary;
            if (header.getFirstNode() > 0) {
                nodeStack.push(new AbstractIterableMultiMapHashMatrix.NodeEntry(header.getFirstNode(), (short) -1));
            }
            queueUpNext();
        }

        /**
         * The cursor of the skip list has next.  The currentIterator indicates the iterator of the current skip list
         * set by queueUpNext.
         * @return Whether there are values left in the skip list or there are skip lists still to check that may have
         *         values
         *
         * @since 1.2.0
         */
        @Override
        public boolean hasNext() {
            prepareNext();
            return (currentIterator != null && currentIterator.hasNext());
        }

        /**
         * Queue up the next reference so, we can check ahead rather than do the processing in the next() while
         * the hasNext may not know if there is a next value or not.
         *
         * @since 1.2.0
         */
        void queueUpNext() {
            NodeEntry nodeEntry;
            NodeEntry newEntry;

            HashMatrixNode node;
            long reference;

            while (nodeStack.size() > 0) {

                nodeEntry = nodeStack.pop();
                node = AbstractIterableMultiMapHashMatrix.this.getHashMatrixNode(nodeEntry.reference);

                // Add all the other related nodes in the bitmap
                for (int i = 0; i < 10; i++) {
                    reference = node.getNext()[i];
                    if (reference > 0) {
                        if (nodeEntry.level < (loadFactor - 2)) {
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
         * Prepare next value
         *
         * @since 1.2.0
         */
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
                        currentIterator = AbstractIterableMultiMapHashMatrix.super.dictionarySet().iterator();
                    else if(isReference)
                        currentIterator = AbstractIterableMultiMapHashMatrix.super.referenceSet().iterator();
                    else
                        currentIterator = AbstractIterableMultiMapHashMatrix.super.entrySet().iterator();
                    if (currentIterator.hasNext())
                    {
                        continueLooking = false;
                        break;
                    }
                }
            }
        }

        /**
         * Return the next object which is located in the current iterator.
         * @return Either a map reference, dictionary, or entry key value pair based on the parents' requirement
         */
        @Override
        public Object next()
        {
            prepareNext();
            return currentIterator.next();
        }
    }

    private class NodeEntry {
        public final long reference;
        public final short level;

        NodeEntry(long reference, short level) {
            this.reference = reference;
            this.level = level;
        }

        @Override
        public int hashCode() {
            return new Long(reference).hashCode();
        }
    }
}
