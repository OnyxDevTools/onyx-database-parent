package com.onyx.diskmap.base;

import com.onyx.diskmap.DiskMap;
import com.onyx.concurrent.DispatchLock;
import com.onyx.concurrent.impl.EmptyReadWriteLock;
import com.onyx.diskmap.impl.base.skiplist.AbstractIterableSkipList;
import com.onyx.diskmap.data.Header;
import com.onyx.diskmap.data.SkipListHeadNode;
import com.onyx.diskmap.data.SkipListNode;
import com.onyx.diskmap.store.Store;
import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.depricated.CompareUtil;
import com.onyx.util.ReflectionField;
import com.onyx.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

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
    @SuppressWarnings("WeakerAccess")
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

            if (!this.getDetached()) {
                setHead(createHeadNode(Byte.MIN_VALUE, 0L, 0L));
                this.getReference().setFirstNode(getHead().getPosition());
                updateHeaderFirstNode(getReference(), this.getReference().getFirstNode());
                getReference().getRecordCount().set(0L);
                updateHeaderRecordCount();
            }

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Get the record id of a corresponding data.  Note, this points to the SkipListNode position.  Not the actual
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
            return node.getRecordId();
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

        if(recordId <= 0)
            return null;
        try {
            SkipListNode<K> node = (SkipListNode<K>) findNodeAtPosition(recordId);
            return findValueAtPosition(node.getRecordPosition(), node.getRecordSize());
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
            if (node != null && node.getRecordId() == recordId) {
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
     * @since 1.3.0 Optimized to require the reflection field so it does not have to re-instantiate one.
     */
    public Object getAttributeWithRecID(final ReflectionField attribute, final long recordId) throws AttributeTypeMismatchException {

        final SkipListNode node = (SkipListNode<K>) findNodeAtPosition(recordId);

        V value = findValueAtPosition(node.getRecordPosition(), node.getRecordSize());
        if (value != null) {
            return ReflectionUtil.INSTANCE.getAny(value, attribute);
        }

        return null;

    }

    @Override
    public Object getAttributeWithRecID(ReflectionField attributeField, SkipListNode node) throws
            AttributeTypeMismatchException {
        if(node == null)
            return null;

        V value = findValueAtPosition(node.getRecordPosition(), node.getRecordSize());

        if (value != null) {
            return ReflectionUtil.INSTANCE.getAny(value, attributeField);
        }

        return null;
    }

    @NotNull
    @Override
    public DispatchLock getReadWriteLock() {
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
    @NotNull
    @SuppressWarnings("WeakerAccess")
    public Set<Long> above(K index, boolean includeFirst) {
        readWriteLock.readLock().lock();

        try {

            Set results = new HashSet();
            SkipListHeadNode node = nearest(index);
            if (node != null) {

                do {

                    while (node.getDown() != 0L)
                        node = findNodeAtPosition(node.getDown());

                    if (node instanceof SkipListNode) {
                        if (((SkipListNode) node).getKey().equals(index) && !includeFirst) {
                            if (node.getNext() > 0L)
                                node = findNodeAtPosition(node.getNext());
                            else
                                break;

                            continue;
                        }

                        if (CompareUtil.forceCompare(index, ((SkipListNode) node).getKey()) && includeFirst) {
                            results.add(((SkipListNode) node).getRecordId());
                        }
                        else if (CompareUtil.forceCompare(index, ((SkipListNode) node).getKey(), QueryCriteriaOperator.GREATER_THAN)) {
                            results.add(((SkipListNode) node).getRecordId());
                        }
                    }

                    if (node.getNext() > 0L)
                        node = findNodeAtPosition(node.getNext());
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
    @NotNull
    @SuppressWarnings("WeakerAccess")
    public Set<Long> below(K index, boolean includeFirst) {
        readWriteLock.readLock().lock();

        try {
            Set results = new HashSet();

            SkipListHeadNode node = getHead();
            while (node.getDown() != 0L)
                node = findNodeAtPosition(node.getDown());

            while (node.getNext() != 0L) {
                node = findNodeAtPosition(node.getNext());
                if (node instanceof SkipListNode) {
                    if (CompareUtil.forceCompare(index, ((SkipListNode) node).getKey()) && !includeFirst)
                        break;
                    else if (CompareUtil.forceCompare(index, ((SkipListNode) node).getKey()) && includeFirst) {
                        results.add(((SkipListNode) node).getRecordId());
                        continue;
                    }
                    else if (CompareUtil.forceCompare(index, ((SkipListNode) node).getKey(), QueryCriteriaOperator.LESS_THAN)) {
                        results.add(((SkipListNode) node).getRecordId());
                        continue;
                    }
                    else if (CompareUtil.forceCompare(index, ((SkipListNode) node).getKey(), QueryCriteriaOperator.GREATER_THAN))
                        break;

                    if (this.shouldMoveDown(index, (K) ((SkipListNode) node).getKey()))
                        break;
                }
            }
            return results;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public int getSize() {
        return (int)longSize();
    }
}
