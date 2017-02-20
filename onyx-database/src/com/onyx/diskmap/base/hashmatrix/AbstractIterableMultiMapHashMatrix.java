package com.onyx.diskmap.base.hashmatrix;

import com.onyx.diskmap.node.HashMatrixNode;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.node.SkipListHeadNode;
import com.onyx.diskmap.store.Store;

import java.util.*;

/**
 * Created by tosborn1 on 1/9/17.
 *
 *
 */
@SuppressWarnings("unchecked")
public abstract class AbstractIterableMultiMapHashMatrix<K,V> extends AbstractCachedHashMatrix<K,V> implements Map<K, V> {


    protected AbstractIterableMultiMapHashMatrix(Store store, Header header, boolean detached) {
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
    public Set<Entry<K, V>> entrySet() {
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
            final Entry<K,V> entry = (Entry<K,V> )super.next();
            if (entry != null) {
                return entry.getValue();
            }
            return null;
        }
    }

    private class KeyIterator extends AbstractMultiMapIterator implements Iterator {
        @Override
        public Object next() {
            final Entry<K,V> entry = (Entry<K,V> )super.next();
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

    abstract class AbstractMultiMapIterator implements Iterator {

        Stack<AbstractIterableMultiMapHashMatrix.NodeEntry> nodeStack = new Stack<>(); // Simple stack that hold onto the nodes
        Stack<Long> referenceStack = new Stack<>(); // Simple stack that hold onto the nodes
        Iterator currentIterator = null;
        boolean isDictionary = false;
        boolean isReference = false;

        AbstractMultiMapIterator() {
            if (header.firstNode > 0) {
                nodeStack.push(new AbstractIterableMultiMapHashMatrix.NodeEntry(header.firstNode, (short) -1));
            }
            queueUpNext();
        }

        AbstractMultiMapIterator(boolean isDictionary) {
            this.isDictionary = isDictionary;
            if (header.firstNode > 0) {
                nodeStack.push(new AbstractIterableMultiMapHashMatrix.NodeEntry(header.firstNode, (short) -1));
            }
            queueUpNext();
        }

        @Override
        public boolean hasNext() {
            prepareNext();
            return (currentIterator != null && currentIterator.hasNext());
        }

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
                    reference = node.next[i];
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

        @Override
        public Object next()
        {
            prepareNext();
            return currentIterator.next();
        }
    }

    private class NodeEntry {
        public long reference;
        public short level;

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
