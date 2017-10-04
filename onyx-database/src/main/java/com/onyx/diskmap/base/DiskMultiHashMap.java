package com.onyx.diskmap.base;

import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.SortedDiskMap;
import com.onyx.concurrent.*;
import com.onyx.concurrent.impl.DefaultDispatchLock;
import com.onyx.concurrent.impl.EmptyDispatchLock;
import com.onyx.concurrent.impl.EmptyMap;
import com.onyx.diskmap.impl.base.hashmap.AbstractIterableMultiMapHashMap;
import com.onyx.diskmap.data.CombinedIndexHashNode;
import com.onyx.diskmap.data.Header;
import com.onyx.diskmap.data.SkipListHeadNode;
import com.onyx.diskmap.store.Store;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by tosborn1 on 2/20/17.
 * <p>
 * This class is used to combine both a Hash Matrix index and a SkipList.  The tail end of the hash matrix points to a skip list.
 * The load factor indicates how bit the bitmap index should be.  The larger the bitmap load factor, the larger the disk
 * space.  It also implies the index will run faster.
 *
 * The performance indicates a big o notation of O(log n) / O(1)  where the O(1) indicates the Big O of the
 * Hash table.  The hash table is guaranteed to have 1 iteration.  Each hash matrix points to a reference to a skip list that has a big o notation of O(log n)
 *
 * The log factor indicates how many skip list references there can be.  So, if the logFactor is 10, there can be a
 * maximum of 9999999999 Skip list heads.
 *
 * So, say you had 1,000,000,000 records and a load factor of 5, the first iteration would provide the skip list containing a 10,000.  The remaining Big O would
 * be log(n) on 10k records.
 *
 * The difference between this and the DiskMultiHashMatrixMap is that this does this structure pre define the allocated space for the hash table
 * If you were to define a large load factor > 5 this could be allocating a great deal of space.  If your data set may be sparse, you may want
 * to use the DiskMultiMatrixHashMap since it does not bare the brunt of allocating space upon instantiation.
 *
 * @since 1.2.0 This was added to offer a more efficient version of the DiskMultiMatrixHashMap for smaller data sets.
 */
@SuppressWarnings("unchecked")
public class DiskMultiHashMap<K, V> extends AbstractIterableMultiMapHashMap<K, V> implements DiskMap<K, V>, SortedDiskMap<K, V> {

    private DispatchLock dispatchLock = new DefaultDispatchLock();

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap
     * @since 1.2.0
     */
    public DiskMultiHashMap(Store fileStore, Header header, int loadFactor) {
        super(fileStore, header, true, loadFactor);
    }

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    public DiskMultiHashMap(Store fileStore, Header header, int loadFactor, DispatchLock dispatchLock) {
        super(fileStore, header, true, loadFactor);
        this.dispatchLock = dispatchLock;
        this.setNodeCache(new EmptyMap());
        this.setMapCache(new EmptyMap());
        this.setCache(new EmptyMap());
        this.setValueByPositionCache(new EmptyMap());
        this.setKeyCache(new EmptyMap());
    }

    /**
     * Constructor
     *
     * @param fileStore  File storage mechanism
     * @param header     Pointer to the DiskMap
     * @param loadFactor The max size of the hash that is generated
     * @param stateless  If designated as true, this map does not retain state.  The state is handled elsewhere.  Without a state
     *                   there can not be a meaningful cache nor a meaningful lock.  In that case, in this constructor,
     *                   we set the cache elements and lock to empty implmementations.
     * @since 1.2.0
     */
    public DiskMultiHashMap(Store fileStore, Header header, int loadFactor, @SuppressWarnings("SameParameterValue") boolean stateless) {
        super(fileStore, header, true, loadFactor);

        if (!stateless) {
            setCache(new EmptyMap());
            setMapCache(new EmptyMap());
            setKeyCache(new EmptyMap());
            setValueByPositionCache(new EmptyMap());
            setNodeCache(new EmptyMap());
            dispatchLock = new EmptyDispatchLock();
        }
    }

    /**
     * Get the value by its corresponding key.
     *
     * @param key Primary key
     * @return The value if it exists
     * @since 1.2.0
     */
    @Override
    public V get(Object key) {
        final CombinedIndexHashNode combinedNode = getHeadReferenceForKey(key, true);

        // Set the selected skip list
        setHead(combinedNode.getHead());

        if (combinedNode.getHead() != null) {
            return super.get(key);
        }
        return null;
    }

    /**
     * Put the value based on the item's key
     *
     * @param key   Object used to uniquely identify a value
     * @param value Its corresponding value
     * @return The value we just inserted
     * @since 1.2.0
     */
    @Override
    public V put(K key, V value) {
        final CombinedIndexHashNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.getHead());

        final SkipListHeadNode head = combinedNode.getHead();
        if (head != null) {
            final long headPosition = head.getPosition();

            return (V) dispatchLock.performWithLock(head, o -> {

                V returnValue = DiskMultiHashMap.super.put(key, value);
                SkipListHeadNode newHead = getHead();
                combinedNode.setHead(newHead);
                if (newHead.getPosition() != headPosition) {
                    updateSkipListReference(combinedNode.getMapId(), newHead.getPosition());
                }
                return returnValue;

            });
        }
        return null;
    }

    /**
     * Remove an object from the map
     *
     * @param key Used to uniquely identify a record
     * @return Object that was removed.  Null otherwise
     * @since 1.2.0
     */
    @Override
    public V remove(Object key) {
        final CombinedIndexHashNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.getHead());

        final SkipListHeadNode head = combinedNode.getHead();

        if (head != null) {
            final long headPosition = head.getPosition();
            return (V) dispatchLock.performWithLock(head, o -> {
                V returnValue = DiskMultiHashMap.super.remove(key);
                SkipListHeadNode newHead = getHead();
                combinedNode.setHead(newHead);
                if (newHead.getPosition() != headPosition) {
                    updateSkipListReference(combinedNode.getMapId(), newHead.getPosition());
                }
                return returnValue;
            });
        }
        return null;
    }

    /**
     * Simple map method to see if an object exists within the map by its key.
     *
     * @param key Identifier
     * @return Whether the object exists
     * @since 1.2.0
     */
    @Override
    public boolean containsKey(Object key) {
        final CombinedIndexHashNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.getHead());

        return combinedNode.getHead() != null && super.containsKey(key);
    }

    /**
     * Detect if a value is contained in the map.  This will have to do a full scan.  It is not efficient!!!
     * The data is stored un ordered.
     *
     * @param value Value you are looking for
     * @return Whether the value was found
     * @since 1.2.0
     */
    @Override
    public boolean containsValue(Object value) {
        for (Object o : values()) {
            if (o.equals(value))
                return true;
        }

        return false;
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
        final CombinedIndexHashNode combinedNode = getHeadReferenceForKey(key, false);
        if (combinedNode == null)
            return -1;

        setHead(combinedNode.getHead());

        if (combinedNode.getHead() != null) {
            return super.getRecID(key);
        }

        return 0;
    }

    /**
     * Put all the objects from one map into this map.
     *
     * @param m Map to convert from
     * @since 1.2.0
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        final Iterator<? extends Map.Entry<? extends K, ? extends V>> iterator = m.entrySet().iterator();
        Map.Entry<? extends K, ? extends V> entry;

        while (iterator.hasNext()) {
            entry = iterator.next();
            this.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Clear this map.  In order to do that.  All we have to do is remove the first data reference and it will
     * orphan the entire data structure
     *
     * @since 1.2.0
     */
    @Override
    public void clear() {
        dispatchLock.performWithLock(getReference(), o -> {
            DiskMultiHashMap.super.clear();
            return null;
        });
    }

    /**
     * The nuts and bolts of the map lie here.  This finds the head of the skip list based on the key
     * It uses the bitmap index on the disk map.
     *
     * @param key       Unique identifier that is changed to a non unique key in order to generify the skip list
     * @param forInsert Whether we should insert the bitmap index.
     * @return The Combined Index data of the skip list and it contains the bitmap data information.
     * @since 1.2.0
     *
     * @since 1.2.2 There is no use for the caching map so it was removed.  This should be
     * inexpensive since it only requires a single i/o read.  Also, refactored the
     * locking to lock on the head and pass thru for the rest of the data structure since it
     * does not impact any sub maps
     */
    private CombinedIndexHashNode getHeadReferenceForKey(Object key, @SuppressWarnings("SameParameterValue") boolean forInsert) {
        int skipListMapId = getSkipListKey(key);

        return (CombinedIndexHashNode) this.dispatchLock.performWithLock(this.getReference(), o -> {
            if (forInsert) {
                SkipListHeadNode headNode1;
                long reference = DiskMultiHashMap.super.getSkipListReference(skipListMapId);
                if (reference == 0) {
                    headNode1 = createHeadNode(Byte.MIN_VALUE, 0L, 0L);
                    insertSkipListReference(skipListMapId, headNode1.getPosition());
                    return new CombinedIndexHashNode(headNode1, skipListMapId);
                } else {
                    return new CombinedIndexHashNode(findNodeAtPosition(reference), skipListMapId);
                }
            } else {
                long reference = DiskMultiHashMap.super.getSkipListReference(skipListMapId);
                if (reference > 0)
                    return new CombinedIndexHashNode(findNodeAtPosition(reference), skipListMapId);
                else
                    return null;
            }
        });
    }

    /**
     * Get the key of the skip list.  This is based on the hash.  It pairs down the hash based on the load factor.
     * That scaled hash will then become the key for the skip list header.
     *
     * @param key Key object
     * @return integer key based on its hash
     * @since 1.2.0
     */
    private int getSkipListKey(Object key) {
        int hash = hash(key);
        int hashDigits[] = getHashDigits(hash);

        int i, k = 0;
        for (i = 0; i < hashDigits.length; i++)
            k = 10 * k + hashDigits[i];

        return k;
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
    public Set<Long> above(K index, boolean includeFirst) {
        Set returnValue = new HashSet();

        for (Object o : getMaps()) {
            setHead((SkipListHeadNode) o);
            returnValue.addAll(super.above(index, includeFirst));
        }
        return returnValue;

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
    public Set<Long> below(K index, boolean includeFirst) {
        Set returnValue = new HashSet();

        for (Object o : getMaps()) {
            setHead((SkipListHeadNode) o);
            returnValue.addAll(super.below(index, includeFirst));
        }
        return returnValue;
    }

    /**
     * Return the Level read write lock implementation
     */
    @NotNull
    @Override
    public DispatchLock getReadWriteLock() {
        return this.dispatchLock;
    }

}
