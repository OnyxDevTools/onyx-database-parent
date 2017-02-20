package com.onyx.diskmap.base;

import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.base.concurrent.EmptyReadWriteLock;
import com.onyx.diskmap.base.skiplist.AbstractIterableSkipList;
import com.onyx.diskmap.node.*;
import com.onyx.diskmap.store.Store;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
public class DiskSkipListMap<K, V> extends AbstractIterableSkipList<K, V> implements DiskMap<K, V> {

    private ReadWriteLock readWriteLock;

    /**
     * Constructor with store.  Initialize the collection types
     *
     * @param store  Underlying storage mechanism
     * @param header Header location of the skip list
     * @since 1.2.0
     */
    public DiskSkipListMap(Store store, Header header) {
        super(store, header);
        readWriteLock = new ReentrantReadWriteLock(true);
    }

    /**
     * Constructor with store.  Initialize the collection types
     *
     * @param store    Underlying storage mechanism
     * @param header   Header location of the skip list
     * @param detached Whether the map is headless and should ignore updating the header
     * @since 1.2.0
     */
    public DiskSkipListMap(Store store, Header header, boolean detached) {
        super(store, header, detached);

        // If it is detached.  The concurrency will be handled by this class' parent
        if (detached)
            readWriteLock = new EmptyReadWriteLock();
    }


    /**
     * Remove an item within the map
     *
     * @param key Key Identifier
     * @return The value that was removed
     */
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

    /**
     * Put a value into a map based on its key.
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return The value of the object that was just put into the map
     */
    @Override
    public V put(K key, V value) {
        readWriteLock.writeLock().lock();

        try {
            return super.put(key, value);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Get an item based on its key
     *
     * @param key Identifier
     * @return The corresponding value
     */
    @Override
    public V get(Object key) {
        readWriteLock.readLock().lock();

        try {
            return super.get(key);
        } finally {
            readWriteLock.readLock().unlock();
        }
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
            for (V next : values()) {
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
     * Clear all the elements of the array.  If it is not detached we must handle
     * the head of teh data structure
     *
     * @since 1.2.0
     */
    @Override
    public void clear() {
        readWriteLock.writeLock().lock();

        try {

            super.clear();

            if (!this.detached) {
                setHead(createHeadNode(Byte.MIN_VALUE, 0L, 0L));
                this.header.firstNode = getHead().position;
                updateHeaderFirstNode(header, this.header.firstNode);
                header.recordCount.set(0L);
                updateHeaderRecordCount();
            }

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Get the record id of a corresponding node.  Note, this points to the SkipListNode position.  Not the actual
     * record position.
     *
     * @param key Identifier
     * @return The position of the record reference if it exists.  Otherwise -1
     * @since 1.2.0
     */
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

    /**
     * Hydrate a record with its record ID.  If the record value exists it will be returned
     *
     * @param recordId Position to find the record reference
     * @return The value within the map
     * @since 1.2.0
     */
    @Override
    public V getWithRecID(long recordId) {
        readWriteLock.readLock().lock();

        try {
            SkipListNode<K> node = (SkipListNode<K>) findNodeAtPosition(recordId);
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
     * @since 1.2.0
     */
    public Map getMapWithRecID(long recordId) {
        readWriteLock.readLock().lock();

        try {
            SkipListNode<K> node = (SkipListNode<K>) findNodeAtPosition(recordId);
            if (node != null && node.position == recordId) {
                return getRecordValueAsDictionary(node);
            }
            return null;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Get Map representation of key object.  If it is in the cache, use reflection to get it from the cache.  Otherwise,
     * just hydrate the value within the store
     *
     * @param attribute Attribute name to fetch
     * @param recordId  Record reference within storage structure
     * @return Map of key values
     * @since 1.2.0
     */
    public Object getAttributeWithRecID(final String attribute, final long recordId) throws AttributeTypeMismatchException {

        final SkipListNode node = (SkipListNode<K>) findNodeAtPosition(recordId);

        V value = findValueAtPosition(node.recordPosition, node.recordSize);
        if (value != null) {
            final Class clazz = value.getClass();
            OffsetField attributeField;
            try {
                attributeField = ReflectionUtil.getOffsetField(clazz, attribute);
            } catch (AttributeMissingException e) {
                return null;
            }
            return ReflectionUtil.getAny(value, attributeField);
        }

        return null;

    }

    @Override
    public Object getAttributeWithRecID(OffsetField attributeField, SkipListNode node) throws
            AttributeTypeMismatchException {
        if(node == null)
            return null;
        V value = findValueAtPosition(node.recordPosition, node.recordSize);

        if (value != null) {
            return ReflectionUtil.getAny(value, attributeField);
        }

        return null;
    }

    /**
     * Find all references above and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    public Set<Long> above(K index, boolean includeFirst) {
        readWriteLock.readLock().lock();

        try {

            Set results = new HashSet();
            SkipListHeadNode node = nearest(index);
            if (node != null) {

                do {

                    while (node.down != 0L)
                        node = findNodeAtPosition(node.down);

                    if (node instanceof SkipListNode) {
                        if (((SkipListNode) node).key.equals(index) && !includeFirst) {
                            if (node.next > 0L)
                                node = findNodeAtPosition(node.next);
                            else
                                break;

                            continue;
                        }

                        if (shouldMoveDown(0, 0, index, (K) ((SkipListNode) node).key))
                            results.add(((SkipListNode) node).position);
                    }
                    if (node.next > 0L)
                        node = findNodeAtPosition(node.next);
                    else
                        node = null;
                } while (node != null);
            }
            return results;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Find all references below and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    public Set<Long> below(K index, boolean includeFirst) {
        readWriteLock.readLock().lock();

        try {
            Set results = new HashSet();

            SkipListHeadNode node = getHead();
            while (node.down != 0L)
                node = findNodeAtPosition(node.down);

            while (node.next != 0L) {
                node = findNodeAtPosition(node.next);
                if (node instanceof SkipListNode) {
                    if (((SkipListNode) node).key.equals(index) && !includeFirst)
                        break;
                    else if (((SkipListNode) node).key.equals(index)) {
                        results.add(node.position);
                        continue;
                    }

                    if (this.shouldMoveDown(0, 0, index, (K) ((SkipListNode) node).key))
                        break;


                    results.add(node.position);
                }
            }
            return results;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }
}
