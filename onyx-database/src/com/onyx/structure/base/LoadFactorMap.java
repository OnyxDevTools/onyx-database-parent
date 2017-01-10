package com.onyx.structure.base;

import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.structure.DefaultDiskMap;
import com.onyx.structure.DiskMap;
import com.onyx.structure.node.*;
import com.onyx.structure.store.Store;

import java.util.*;

/**
 * Created by tosborn1 on 1/8/17.
 *
 * This class is used to combine both a Bitmap index and a SkipList.  The tail end of the bitmap points to a skip list.
 * The load factor indicates how bit the bitmap index should be.  The larger the bitmap load factor, the larger the disk
 * space.  It also implies the index will run faster.
 *
 * @since 1.2.0
 */
public class LoadFactorMap<K, V> extends AbstractIterableLoadFactorMap<K, V> implements Map<K, V>, DiskMap<K, V> {

    // Cache of skip lists
    private Map<Integer, CombinedIndexNode> skipListMapCache = Collections.synchronizedMap(new WeakHashMap<>());

    // Load factor as mentioned in the header.  The max value is 10.  Minimum value is 1
    private int loadFactor = 4;

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header Pointer to the DiskMap
     *
     * @since 1.2.0
     */
    public LoadFactorMap(Store fileStore, Header header) {
        super(fileStore, header, true);

        this.defaultDiskMap = new DefaultDiskMap(fileStore, header, true);
        this.defaultDiskMap.setLoadFactor(loadFactor);

        // If there are a small number of skip lists lets keep it in memory.  The max is going to be 4k
        if(loadFactor <= 4)
            skipListMapCache = Collections.synchronizedMap(new HashMap<>());
    }

    /**
     * Get the value by its corresponding key.
     * @param key Primary key
     * @return The value if it exists
     * @since 1.2.0
     */
    @Override
    public V get(Object key) {
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(key, true);

        // Set the selected skiplist
        setHead(combinedNode.head);

        defaultDiskMap.readWriteLock.lockReadLevel(combinedNode.hashDigit);
        try {
            if (combinedNode.head != null) {
                return super.get(key);
            }
        } finally {
            defaultDiskMap.readWriteLock.unlockReadLevel(combinedNode.hashDigit);
        }
        return null;
    }

    /**
     * The nuts and bolts of the map lie here.  This finds the head of the skip list based on the key
     * It uses the bitmap index on the disk map.
     *
     * @param key Unique identifier that is changed to a non unique key in order to generify the skip list
     * @param forInsert Whether we should insert the bitmap index.
     * @return The Combined Index node of the skip list and it contains the bitmap node information.
     */
    private CombinedIndexNode getHeadReferenceForKey(Object key, boolean forInsert) {
        int hash = hash(key);
        int hashDigits[] = defaultDiskMap.getHashDigits(hash);
        int hashDigit = hashDigits[defaultDiskMap.getRecordReferenceIndex()];
        reverse(hashDigits);

        // Get the unique identifier for the skip list
        int actualKey = getSkipListKey(key);

        if (forInsert)
            defaultDiskMap.readWriteLock.lockWriteLevel(hashDigit);
        else
            defaultDiskMap.readWriteLock.lockReadLevel(hashDigit);

        try {

            CombinedIndexNode returnValue = skipListMapCache.get(actualKey);
            if (returnValue != null)
                return returnValue;

            // Find the last node of the bitmap index
            final BitMapNode node = defaultDiskMap.seek(actualKey, forInsert, hashDigits);
            long nodeReference = node.next[hashDigit];

            if (node != null && nodeReference > 0)
                returnValue = new CombinedIndexNode(findNodeAtPosition(nodeReference), node, hashDigit);
            else if (forInsert)
                returnValue = new CombinedIndexNode(createHeadNode(Byte.MIN_VALUE, 0L, 0L), node, hashDigit);

            if (returnValue != null)
                skipListMapCache.put(actualKey, returnValue);

            return returnValue;
        } finally {
            if (forInsert)
                defaultDiskMap.readWriteLock.unlockWriteLevel(hashDigit);
            else
                defaultDiskMap.readWriteLock.unlockReadLevel(hashDigit);
        }
    }

    /**
     * Put the value based on the item's key
     *
     * @param key Object used to uniquely identify a value
     * @param value Its corresponding value
     * @return The value we just inserted
     *
     * @since 1.2.0
     */
    @Override
    public V put(K key, V value) {

        final CombinedIndexNode combinedNode = getHeadReferenceForKey(key, true);

        setHead(combinedNode.head);


        SkipListHeadNode head = combinedNode.head;


        if (head != null) {
            defaultDiskMap.readWriteLock.lockWriteLevel(combinedNode.hashDigit);

            try {
                long time = System.nanoTime();

                value = super.put(key, value);

                head = getHead();
                if (combinedNode.bitMapNode.next[combinedNode.hashDigit] != head.position) {
                    defaultDiskMap.updateBitmapNodeReference(combinedNode.bitMapNode, combinedNode.hashDigit, head.position);
                    combinedNode.head = head;
                }

            } finally {
                defaultDiskMap.readWriteLock.unlockWriteLevel(combinedNode.hashDigit);
            }

            return value;
        }

        return null;
    }

    /**
     * Remove an object from the map
     * @param key Used to uniquely identify a record
     * @return Object that was removed.  Null otherwise
     *
     * @since 1.2.0
     */
    @Override
    public V remove(Object key) {
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.head);
        SkipListHeadNode head = combinedNode.head;

        if (head != null) {

            defaultDiskMap.readWriteLock.lockWriteLevel(combinedNode.hashDigit);

            try {

                V value = super.remove(key);

                head = getHead();
                if (combinedNode.bitMapNode.next[combinedNode.hashDigit] != head.position) {
                    defaultDiskMap.updateBitmapNodeReference(combinedNode.bitMapNode, combinedNode.hashDigit, head.position);
                    combinedNode.head = head;
                }

                return value;
            } finally {
                defaultDiskMap.readWriteLock.unlockWriteLevel(combinedNode.hashDigit);
            }
        }
        return null;
    }


    @Override
    public long getRecID(Object key) {
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.head);

        try {
            defaultDiskMap.readWriteLock.lockReadLevel(combinedNode.hashDigit);
            if (getHead() != null) {
                return super.getRecID(key);
            }
        } finally {
            defaultDiskMap.readWriteLock.unlockReadLevel(combinedNode.hashDigit);
        }
        return -1;
    }

    @Override
    public V getWithRecID(long recordId) {

        final CombinedIndexNode combinedNode = getHeadReferenceForKey(0, true);
        setHead(combinedNode.head);
        defaultDiskMap.readWriteLock.lockReadLevel(combinedNode.hashDigit);

        try {
            if (combinedNode.head != null) {
                return super.getWithRecID(recordId);
            }
        } finally {
            defaultDiskMap.readWriteLock.unlockReadLevel(combinedNode.hashDigit);
            setHead(null);
        }
        return null;
    }

    @Override
    public Map getMapWithRecID(long recordId) {
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(0, true);
        setHead(combinedNode.head);

        try {
            defaultDiskMap.readWriteLock.lockReadLevel(combinedNode.hashDigit);
            if (combinedNode.head != null) {
                return super.getMapWithRecID(recordId);
            }
        } finally {
            defaultDiskMap.readWriteLock.unlockReadLevel(combinedNode.hashDigit);
            setHead(null);
        }
        return null;
    }

    @Override
    public Object getAttributeWithRecID(String attribute, long reference) throws AttributeTypeMismatchException {
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(0, true);
        setHead(combinedNode.head);

        defaultDiskMap.readWriteLock.lockReadLevel(combinedNode.hashDigit);

        try {
            if (combinedNode.head != null) {
                return super.getAttributeWithRecID(attribute, reference);
            }
        } finally {
            defaultDiskMap.readWriteLock.unlockReadLevel(combinedNode.hashDigit);
            setHead(null);
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.head);

        defaultDiskMap.readWriteLock.lockReadLevel(combinedNode.hashDigit);

        try {
            if (combinedNode.head != null) {
                return super.containsKey(key);
            }
        } finally {
            defaultDiskMap.readWriteLock.unlockReadLevel(combinedNode.hashDigit);
            setHead(null);
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        Iterator<DiskSkipList<K, V>> iterator = defaultDiskMap.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().containsValue(value))
                return true;
        }

        return false;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        final Iterator<? extends Entry<? extends K, ? extends V>> iterator = m.entrySet().iterator();
        Entry<? extends K, ? extends V> entry = null;

        while (iterator.hasNext()) {
            entry = iterator.next();
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        try {
            defaultDiskMap.readWriteLock.lockWriteLevel(0);
            defaultDiskMap.clear();
        } finally {
            defaultDiskMap.readWriteLock.unlockWriteLevel(0);
        }
    }


    /**
     * Helper method used to reverse an array
     *
     * @param data Array to reverse
     * @since 1.2.0
     */
    private static void reverse(int[] data) {
        for (int left = 0, right = data.length - 1; left < right; left++, right--) {
            int temp = data[left];
            data[left] = data[right];
            data[right] = temp;
        }
    }

    /**
     * Get the key of the skip list.  This is based on the hash.  It pairs down the hash based on the load factor.
     * That scaled hash will then become the key for the skip list header.
     *
     * @param key Key object
     * @return integer key based on its hash
     */
    private int getSkipListKey(Object key) {
        int hash = hash(key);
        int hashDigits[] = defaultDiskMap.getHashDigits(hash);

        int i, k = 0;
        for (i = 0; i < hashDigits.length; i++)
            k = 10 * k + hashDigits[i];

        return k;
    }

}
