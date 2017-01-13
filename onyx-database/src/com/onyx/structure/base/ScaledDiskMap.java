package com.onyx.structure.base;

import com.onyx.structure.DefaultDiskMap;
import com.onyx.structure.DiskMap;
import com.onyx.structure.node.*;
import com.onyx.structure.store.Store;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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
public class ScaledDiskMap<K, V> extends AbstractIterableLoadFactorMap<K, V> implements Map<K, V>, DiskMap<K, V> {

    // Cache of skip lists
    private Map<Integer, CombinedIndexNode> skipListMapCache = new ConcurrentWeakHashMap();

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap
     * @since 1.2.0
     */
    public ScaledDiskMap(Store fileStore, Header header, int loadFactor) {
        super(fileStore, header, true);

        this.defaultDiskMap = new DefaultDiskMap(fileStore, header, true);

        this.loadFactor = (byte)loadFactor;

        // If there are a small number of skip lists lets keep it in memory.  The max is going to be 999
        if (loadFactor <= 3)
            skipListMapCache = new ConcurrentWeakHashMap();
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
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(key, true);

        // Set the selected skip list
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
     * Put the value based on the item's key
     *
     * @param key   Object used to uniquely identify a value
     * @param value Its corresponding value
     * @return The value we just inserted
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

                value = super.put(key, value);

                head = getHead();

                // Only update the node if the head of the skip list has changed
                if (combinedNode.bitMapNode.next[combinedNode.hashDigit] != head.position) {
                    combinedNode.head = head;
                    defaultDiskMap.updateBitmapNodeReference(combinedNode.bitMapNode, combinedNode.hashDigit, head.position);
                    defaultDiskMap.nodeCache.remove(combinedNode.bitMapNode.position);
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
     *
     * @param key Used to uniquely identify a record
     * @return Object that was removed.  Null otherwise
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

                // Only update the bitmap node if the head of the skiplist has changed
                if (combinedNode.bitMapNode.next[combinedNode.hashDigit] != head.position) {
                    defaultDiskMap.updateBitmapNodeReference(combinedNode.bitMapNode, combinedNode.hashDigit, head.position);
                    combinedNode.head = head;
                    defaultDiskMap.nodeCache.remove(combinedNode.bitMapNode.position);
                }

                return value;
            } finally {
                defaultDiskMap.readWriteLock.unlockWriteLevel(combinedNode.hashDigit);
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
        final CombinedIndexNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.head);

        defaultDiskMap.readWriteLock.lockReadLevel(combinedNode.hashDigit);

        try {
            if (combinedNode.head != null) {
                return super.containsKey(key);
            }
        } finally {
            defaultDiskMap.readWriteLock.unlockReadLevel(combinedNode.hashDigit);
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
        Iterator<DiskSkipList<K, V>> iterator = defaultDiskMap.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().containsValue(value))
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
        try {
            super.clear();
            defaultDiskMap.readWriteLock.lockWriteLevel(0);
            defaultDiskMap.clear();
        } finally {
            defaultDiskMap.readWriteLock.unlockWriteLevel(0);
        }
    }

    /**
     * The nuts and bolts of the map lie here.  This finds the head of the skip list based on the key
     * It uses the bitmap index on the disk map.
     *
     * @param key       Unique identifier that is changed to a non unique key in order to generify the skip list
     * @param forInsert Whether we should insert the bitmap index.
     * @return The Combined Index node of the skip list and it contains the bitmap node information.
     * @since 1.2.0
     */
    private CombinedIndexNode getHeadReferenceForKey(Object key, boolean forInsert) {
        int hash = Math.abs(hash(key));
        int hashDigits[] = getHashDigits(hash);
        int hashDigit = hashDigits[loadFactor - 1];

        int skipListMapId = getSkipListKey(key);

        if (forInsert)
            defaultDiskMap.readWriteLock.lockWriteLevel(hashDigit);
        else
            defaultDiskMap.readWriteLock.lockReadLevel(hashDigit);

        try {

            return skipListMapCache.computeIfAbsent(skipListMapId, integer -> seek(forInsert, hashDigits));


        } finally {
            if (forInsert)
                defaultDiskMap.readWriteLock.unlockWriteLevel(hashDigit);
            else
                defaultDiskMap.readWriteLock.unlockReadLevel(hashDigit);
        }
    }


    /**
     * Get the key of the skip list.  This is based on the hash.  It pairs down the hash based on the load factor.
     * That scaled hash will then become the key for the skip list header.
     *
     * @param key Key object
     * @return integer key based on its hash
     *
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
     * Finds a head of the next data structure.  The head is to a skip list and we will go from bitmap to skip list.
     * This is different than in the bitmap since it takes into effect the load factor and will only do a pre defined
     * amount of iterations to find your data.
     *
     * @param forInsert Whether you want to insert nodes as you go along.  Used for efficiency
     * @param hashDigits An array of hash digits.  This has been tuned based on the loadFactor
     * @return The Contrived index of the sub data structure
     *
     * @since 1.2.0
     */
    private CombinedIndexNode seek(boolean forInsert, final int[] hashDigits) {

        BitMapNode node;

        if (this.header.firstNode > 0) {
            node = defaultDiskMap.getBitmapNode(this.header.firstNode); // Get Root node
        } else {
            // No default node, lets create one // It must mean we are inserting
            node = new BitMapNode();
            node.position = fileStore.allocate(defaultDiskMap.getBitmapNodeSize());
            header.firstNode = node.position;

            defaultDiskMap.writeBitmapNode(node.position, node);
            defaultDiskMap.updateHeaderFirstNode(header, node.position);
        }

        // There is no default node return -1 because it was not found
        if (!forInsert && node == null)
            return null;

        BitMapNode previousNode = node;
        long nodePosition;
        int hashDigit;

        // Not we are using this load factor rather than what is on the Bitmap disk map
        // Break down the nodes and iterate through them.  We should be left with the remaining node which should point us to the record
        for (int level = 0; level < loadFactor; level++) {

            hashDigit = hashDigits[level];
            nodePosition = previousNode.next[hashDigit];

            if (nodePosition == 0 && forInsert) {
                if (level == loadFactor - 1) {
                    SkipListHeadNode headNode = createHeadNode(Byte.MIN_VALUE, 0L, 0L);
                    defaultDiskMap.updateBitmapNodeReference(previousNode, hashDigit, headNode.position);
                    return new CombinedIndexNode(headNode, previousNode, hashDigit);
                } else {
                    node = new BitMapNode();
                    node.position = fileStore.allocate(defaultDiskMap.getBitmapNodeSize());
                    defaultDiskMap.writeBitmapNode(node.position, node);
                    defaultDiskMap.updateBitmapNodeReference(previousNode, hashDigit, node.position);
                }

                previousNode = node;
            }

            // Not found because it is not in the
            else if (nodePosition == 0)
                return null;
            else if (level < loadFactor - 1)
                previousNode = defaultDiskMap.getBitmapNode(nodePosition);
            else {
                return new CombinedIndexNode(findNodeAtPosition(nodePosition), previousNode, hashDigit);
            }
        }

        return null; // This should contain the drones you are looking for (Star Wars reference) // This contains the key to the linked list
    }
}
