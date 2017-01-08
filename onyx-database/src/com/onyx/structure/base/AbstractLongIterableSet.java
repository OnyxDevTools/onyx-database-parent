package com.onyx.structure.base;

import com.onyx.structure.node.BitMapNode;
import com.onyx.structure.node.Header;
import com.onyx.structure.node.LongRecordReference;
import com.onyx.structure.store.Store;

import java.util.Iterator;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Created by tosborn1 on 9/9/16.
 */
public abstract class AbstractLongIterableSet<E> extends AbstractLongCachedBitMap implements Set<E> {

    /**
     * Constructor.
     *
     * @param fileStore
     * @param header
     */
    public AbstractLongIterableSet(Store fileStore, Header header) {
        super(fileStore, header);
    }

    
    @Override
    public Iterator<E> iterator() {
        return new ValueIterator(header);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Iterator iterator = iterator();
        while(iterator.hasNext())
        {
            action.accept((E)iterator.next());
        }
    }

    /**
     * Value Iterator.
     * <p/>
     * Iterates through and hydrates the values in an DiskMap
     */
    class ValueIterator extends AbstractLongIterableSet.AbstractNodeIterator implements Iterator {
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
            final LongRecordReference reference = (LongRecordReference) super.next();
            if (reference != null) {
                return reference.value;
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

        protected Stack<AbstractLongIterableSet.AbstractNodeIterator.NodeEntry> nodeStack = new Stack<AbstractLongIterableSet.AbstractNodeIterator.NodeEntry>(); // Simple stack that hold onto the nodes
        protected Stack<Long> referenceStack = new Stack<Long>(); // Simple stack that hold onto the nodes

        /**
         * Constructor
         *
         * @param header
         */
        public AbstractNodeIterator(Header header) {
            if (header.firstNode > 0) {
                nodeStack.push(new AbstractLongIterableSet.AbstractNodeIterator.NodeEntry(header.firstNode, (short) -1));
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
            AbstractLongIterableSet.AbstractNodeIterator.NodeEntry nodeEntry = null;
            BitMapNode node = null;

            AbstractLongIterableSet.AbstractNodeIterator.NodeEntry newEntry = null;
            long reference = 0;

            while (nodeStack.size() > 0) {

                nodeEntry = nodeStack.pop();
                node = getBitmapNode(nodeEntry.reference);

                // Add all the other related nodes in the bitmap
                for (int i = 0; i < getLoadFactor(); i++) {

                    reference = node.next[i];
                    if (reference > 0) {
                        if (nodeEntry.level < (getLoadFactor() - 1)) {
                            newEntry = new AbstractLongIterableSet.AbstractNodeIterator.NodeEntry(reference, (short) (nodeEntry.level + 1));
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

                LongRecordReference reference = getLongRecordReference(referenceStack.pop());
                while (reference.next > 0) {
                    referenceStack.push(reference.next);
                    reference = getLongRecordReference(reference.next);
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

                if (val.getClass() == AbstractLongIterableSet.AbstractNodeIterator.NodeEntry.class) {
                    return ((AbstractLongIterableSet.AbstractNodeIterator.NodeEntry) val).reference == reference;
                }
                return false;
            }
        }
    }

}
