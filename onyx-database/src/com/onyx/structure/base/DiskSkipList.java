package com.onyx.structure.base;

import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.structure.DiskMap;
import com.onyx.structure.node.Header;
import com.onyx.structure.node.RecordReference;
import com.onyx.structure.node.SkipListNode;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.store.Store;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

/**
 * Created by tosborn1 on 1/7/17.
 * <p>
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 * @since 1.2.0
 */
@SuppressWarnings("unchecked")
public class DiskSkipList<K, V> extends AbstractIterableSkipList<K, V> implements DiskMap<K, V> {

    protected ReadWriteLock readWriteLock;

    /**
     * Constructor with store.  Initialize the collection types
     *
     * @param store  Underlying storage mechanism
     * @param header Header location of the skip list
     * @since 1.2.0
     */
    public DiskSkipList(Store store, Header header) {
        super(store, header);
        readWriteLock = new ReentrantReadWriteLock(true);
    }

    /**
     * Constructor with store.  Initialize the collection types
     *
     * @param store  Underlying storage mechanism
     * @param header Header location of the skip list
     * @param detached Whether the map is headless and should ignore updating the header
     * @since 1.2.0
     */
    public DiskSkipList(Store store, Header header, boolean detached) {
        super(store, header, detached);
        if(detached)
            readWriteLock = new EmptyReadWriteLock();
    }

    /**
     * Iterates through the entire skip list to see if it contains the value you are looking for.
     * <p>
     * Note, this is not efficient.  It will basically do a bubble search.
     *
     * @param value Value you are looking for
     * @return Whether the value was found
     */
    @Override
    public boolean containsValue(Object value) {
        readWriteLock.readLock().lock();

        try {
            final Iterator<V> skipListIterator = values().iterator();
            while (skipListIterator.hasNext()) {
                V next = skipListIterator.next();
                if (next.equals(value))
                    return true;
            }
            return false;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Put all the elements from the map into the skip list map
     *
     * @param m Map to convert from
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.forEach((BiConsumer<K, V>) this::put);
    }

    /**
     * Clear all the elements of the array.
     */
    @Override
    public void clear() {
        readWriteLock.writeLock().lock();

        try {

            super.clear();
            setHead(createHeadNode(Byte.MIN_VALUE, 0L, 0L));

            if(this.header != null) {
                this.header.firstNode = getHead().position;
                updateHeaderFirstNode(header, this.header.firstNode);
                header.recordCount.set(0L);
                updateHeaderRecordCount();
            }

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public long getRecID(Object key) {
        readWriteLock.readLock().lock();

        try {

            SkipListNode<K> node = find((K) key);
            if (node == null)
                return -1;
            return node.position;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public V getWithRecID(long recordId) {
        readWriteLock.readLock().lock();

        try {
            SkipListNode<K> node = (SkipListNode<K>)findNodeAtPosition(recordId);
            return findValueAtPosition(node.recordPosition, node.recordSize);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Get Map representation of key object
     *
     * @param recordId Record reference within storage structure
     * @return Map of key values
     */
    public Map getMapWithRecID(long recordId) {
        readWriteLock.readLock().lock();

        try {
            SkipListNode<K> node = (SkipListNode<K>)findNodeAtPosition(recordId);
            if (node != null && node.position == recordId) {
                return getRecordValueAsDictionary(node);
            }
            return null;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Get Map representation of key object.
     *
     * @param attribute Attribute name to fetch
     * @param recordId  Record reference within storage structure
     * @return Map of key values
     */
    public Object getAttributeWithRecID(final String attribute, final long recordId) throws AttributeTypeMismatchException {
        readWriteLock.readLock().lock();

        try {
            SkipListNode<K> node = (SkipListNode<K>)findNodeAtPosition(recordId);
            Object value = findValueAtPosition(node.recordPosition, node.recordSize);

            if (value != null) {
                final Class clazz = value.getClass();
                OffsetField attributeField = null;

                try {
                    attributeField = ReflectionUtil.getOffsetField(clazz, attribute);
                } catch (AttributeMissingException e) {
                    RecordReference reference = new RecordReference();
                    reference.position = recordId;
                    return getAttributeWithRecID(attribute, reference);
                }

                return ReflectionUtil.getAny(value, attributeField);
            }

            if ((node != null) && (node.position == recordId)) {
                RecordReference reference = new RecordReference();
                reference.position = recordId;

                return getAttributeWithRecID(attribute, reference);
            }

            return null;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Get Attribute with record id
     *
     * @param attribute attribute name to gather
     * @param reference record reference where the record is stored
     * @return Attribute key of record
     */
    public Object getAttributeWithRecID(String attribute, RecordReference reference) {
        readWriteLock.readLock().lock();

        try {
            SkipListNode node = (SkipListNode<K>)findNodeAtPosition(reference.position);
            ObjectBuffer buffer = fileStore.read(node.recordPosition, node.recordSize);
            return buffer.getAttribute(attribute, node.serializerId);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        readWriteLock.writeLock().lock();

        try {
            return super.remove(key);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public V put(K key, V value) {
        readWriteLock.writeLock().lock();

        try {
            return super.put(key, value);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }


    @Override
    public V get(Object key) {
        readWriteLock.readLock().lock();

        try {
            return super.get(key);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public Store getFileStore() {
        return fileStore;
    }

    @Override
    public LevelReadWriteLock getReadWriteLock() {
        return new LevelReadWriteLock() {
            @Override
            public void lockReadLevel(int level) {

            }

            @Override
            public void unlockReadLevel(int level) {

            }

            @Override
            public void lockWriteLevel(int level) {

            }

            @Override
            public void unlockWriteLevel(int level) {

            }

            @Override
            public Lock readLock() {
                return new Lock() {
                    @Override
                    public void lock() {

                    }

                    @Override
                    public void lockInterruptibly() throws InterruptedException {

                    }

                    @Override
                    public boolean tryLock() {
                        return false;
                    }

                    @Override
                    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                        return false;
                    }

                    @Override
                    public void unlock() {

                    }

                    @Override
                    public Condition newCondition() {
                        return null;
                    }
                };
            }

            @Override
            public Lock writeLock() {
                return new Lock() {
                    @Override
                    public void lock() {

                    }

                    @Override
                    public void lockInterruptibly() throws InterruptedException {

                    }

                    @Override
                    public boolean tryLock() {
                        return false;
                    }

                    @Override
                    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                        return false;
                    }

                    @Override
                    public void unlock() {

                    }

                    @Override
                    public Condition newCondition() {
                        return null;
                    }
                };
            }
        };
    }

    @Override
    public Header getReference() {
        return header;
    }

}
