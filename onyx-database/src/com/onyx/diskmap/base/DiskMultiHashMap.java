package com.onyx.diskmap.base;

import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.OrderedDiskMap;
import com.onyx.diskmap.base.concurrent.DefaultLevelReadWriteLock;
import com.onyx.diskmap.base.hashmap.AbstractIterableMultiMapHashMap;
import com.onyx.diskmap.node.CombinedIndexHashNode;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.node.SkipListHeadNode;
import com.onyx.diskmap.store.Store;

import java.util.*;

/**
 * Created by tosborn1 on 1/8/17.
 * <p>
 * This class is used to combine both a Bitmap index and a SkipList.  The tail end of the bitmap points to a skip list.
 * The load factor indicates how bit the bitmap index should be.  The larger the bitmap load factor, the larger the disk
 * space.  It also implies the index will run faster.
 *
 * @since 1.2.0
 */
@SuppressWarnings("unchecked")
public class DiskMultiHashMap<K, V> extends AbstractIterableMultiMapHashMap<K, V> implements Map<K, V>, DiskMap<K, V>, OrderedDiskMap<K, V> {

    private DefaultLevelReadWriteLock levelReadWriteLock = new DefaultLevelReadWriteLock();

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
        setHead(combinedNode.head);

        long stamp = levelReadWriteLock.lockReadLevel(combinedNode.hashDigit);
        try {
            if (combinedNode.head != null) {
                return super.get(key);
            }
        } finally {
            levelReadWriteLock.unlockReadLevel(combinedNode.hashDigit, stamp);
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
        setHead(combinedNode.head);

        SkipListHeadNode head = combinedNode.head;

        if (head != null) {

            long stamp = levelReadWriteLock.lockWriteLevel(combinedNode.hashDigit);

            try {
                long headPosition = head.position;

                value = super.put(key, value);
                head = getHead();
                combinedNode.head = head;
                if (head.position != headPosition) {
                    updateReference(combinedNode.mapId, head.position);
                }
            } finally {
                levelReadWriteLock.unlockWriteLevel(combinedNode.hashDigit, stamp);
            }

            return value;
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
        setHead(combinedNode.head);
        SkipListHeadNode head = combinedNode.head;

        if (head != null) {

            long stamp = levelReadWriteLock.lockWriteLevel(combinedNode.hashDigit);

            long headPosition = head.position;
            try {

                V value = super.remove(key);
                head = getHead();
                if (head.position != headPosition) {
                    combinedNode.head = head;
                    updateReference(combinedNode.mapId, head.position);
                }

                return value;
            } finally {
                levelReadWriteLock.unlockWriteLevel(combinedNode.hashDigit, stamp);
            }
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
        setHead(combinedNode.head);

        long stamp = levelReadWriteLock.lockReadLevel(combinedNode.hashDigit);

        try {
            if (combinedNode.head != null) {
                return super.containsKey(key);
            }
        } finally {
            levelReadWriteLock.unlockReadLevel(combinedNode.hashDigit, stamp);
        }
        return false;
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
     * Put all the objects from one map into this map.
     *
     * @param m Map to convert from
     * @since 1.2.0
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        final Iterator<? extends Entry<? extends K, ? extends V>> iterator = m.entrySet().iterator();
        Entry<? extends K, ? extends V> entry;

        while (iterator.hasNext()) {
            entry = iterator.next();
            this.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Clear this map.  In order to do that.  All we have to do is remove the first node reference and it will
     * orphan the entire data structure
     *
     * @since 1.2.0
     */
    @Override
    public void clear() {
        long stamp = levelReadWriteLock.lockWriteLevel(0);
        try {
            super.clear();
        } finally {
            levelReadWriteLock.unlockWriteLevel(0, stamp);
        }
    }

    private Map<Integer, CombinedIndexHashNode> hashIndexNodeCache = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The nuts and bolts of the map lie here.  This finds the head of the skip list based on the key
     * It uses the bitmap index on the disk map.
     *
     * @param key       Unique identifier that is changed to a non unique key in order to generify the skip list
     * @param forInsert Whether we should insert the bitmap index.
     * @return The Combined Index node of the skip list and it contains the bitmap node information.
     * @since 1.2.0
     */
    private CombinedIndexHashNode getHeadReferenceForKey(Object key, boolean forInsert) {
        int hash = Math.abs(hash(key));
        int hashDigits[] = getHashDigits(hash);
        int hashDigit = hashDigits[loadFactor - 1];

        int skipListMapId = getSkipListKey(key);

        return hashIndexNodeCache.compute(skipListMapId, (integer, combinedHashIndexNode) -> {
            if (combinedHashIndexNode != null)
                return combinedHashIndexNode;

            if (forInsert) {
                SkipListHeadNode headNode1;
                long reference = super.getReference(skipListMapId);
                if (reference == 0) {
                    headNode1 = createHeadNode(Byte.MIN_VALUE, 0L, 0L);
                    insertReference(skipListMapId, headNode1.position);
                    return new CombinedIndexHashNode(headNode1, skipListMapId, hashDigit);
                } else {
                    return new CombinedIndexHashNode(findNodeAtPosition(reference), skipListMapId, hashDigit);
                }
            }
            return null;
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
    public Set<Long> above(K index, boolean includeFirst) {
        Set returnValue = new HashSet();

        for (Object o : mapSet()) {
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
    public Set<Long> below(K index, boolean includeFirst) {
        Set returnValue = new HashSet();

        for (Object o : mapSet()) {
            setHead((SkipListHeadNode) o);
            returnValue.addAll(super.below(index, includeFirst));
        }
        return returnValue;
    }

}
