package com.onyx.diskmap.base;

import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.SortedDiskMap;
import com.onyx.concurrent.*;
import com.onyx.concurrent.impl.DefaultDispatchLock;
import com.onyx.concurrent.impl.EmptyMap;
import com.onyx.diskmap.impl.DiskSkipListMap;
import com.onyx.diskmap.impl.base.hashmatrix.AbstractIterableHashMatrix;
import com.onyx.diskmap.data.CombinedIndexHashMatrixNode;
import com.onyx.diskmap.data.HashMatrixNode;
import com.onyx.diskmap.data.Header;
import com.onyx.diskmap.data.SkipListHeadNode;
import com.onyx.diskmap.store.Store;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.SynchronizedMap;

import java.util.*;

/**
 * Created by tosborn1 on 1/8/17.
 * <p>
 * This class is used to combine both a Hash Matrix index and a SkipList.  The tail end of the hash matrix points to a skip list.
 * The load factor indicates how bit the bitmap index should be.  The larger the bitmap load factor, the larger the disk
 * space.  It also implies the index will run faster.
 *
 * The performance indicates a big o notation of O(log n) / O(logFactor)  where the O(logFactor) indicates the Big O of the
 * Hash matrix.  Each hash matrix points to a reference to a skip list that has a big o notation of O(log n)
 *
 * The log factor indicates how many skip list references there can be.  So, if the logFactor is 10, there can be a
 * maximum of 9999999999 Skip list heads.
 *
 * The difference between this and the DiskMultiHashMap is that this does not pre define the allocated space for the hash matrix
 * It does not because, if the loadFactor were to be 10, that would be a massive amount of storage space.  For smaller loadFactors
 * where you can afford the allocation, the DiskMultiHashMap will perform better.
 *
 * @since 1.2.0 This was re-factored not to have a dependent sub map.
 */
@SuppressWarnings("unchecked")
public class DiskMultiMatrixHashMap<K, V> extends AbstractIterableHashMatrix<K, V> implements Map<K, V>, DiskMap<K, V>, SortedDiskMap<K,V> {

    private DispatchLock dispatchLock = new DefaultDispatchLock();

    // Cache of skip lists
    private final CompatMap<Integer, CombinedIndexHashMatrixNode> skipListMapCache = new SynchronizedMap<>(new CompatWeakHashMap<>());

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap˚
     * @since 1.2.0
     */
    public DiskMultiMatrixHashMap(Store fileStore, Header header, int loadFactor) {
        super(fileStore, header, true);
        this.setLoadFactor((byte) loadFactor);
    }

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap
     * @since 1.2.1 Added constructor in the event you wanted a different locking implementation
     */
    @SuppressWarnings("unused")
    public DiskMultiMatrixHashMap(Store fileStore, Header header, int loadFactor, DispatchLock dispatchLock) {
        super(fileStore, header, true);
        this.dispatchLock = dispatchLock;
        this.setLoadFactor((byte) loadFactor);
        this.setHashMatrixNodeCache(new EmptyMap());
        this.setValueByPositionCache(new EmptyMap());
        this.setKeyCache(new EmptyMap());
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
        final CombinedIndexHashMatrixNode combinedNode = getHeadReferenceForKey(key, true);

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

        final CombinedIndexHashMatrixNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.getHead());

        final SkipListHeadNode head = combinedNode.getHead();

        if (head != null) {

            return (V)this.getReadWriteLock().performWithLock(head, o -> {
                Object returnValue = DiskMultiMatrixHashMap.super.put(key, value);
                final SkipListHeadNode newHead = getHead();

                // Only update the data if the head of the skip list has changed
                if (combinedNode.getBitMapNode().getNext()[combinedNode.getHashDigit()] != newHead.getPosition()) {
                    combinedNode.setHead(newHead);
                    DiskMultiMatrixHashMap.this.updateHashMatrixReference(combinedNode.getBitMapNode(), combinedNode.getHashDigit(), newHead.getPosition());
                    DiskMultiMatrixHashMap.this.getHashMatrixNodeCache().remove(combinedNode.getBitMapNode().getPosition());
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
        final CombinedIndexHashMatrixNode combinedNode = getHeadReferenceForKey(key, true);
        setHead(combinedNode.getHead());
        SkipListHeadNode head = combinedNode.getHead();

        if (head != null) {

            return (V) this.getReadWriteLock().performWithLock(head, o -> {
                Object returnValue = DiskMultiMatrixHashMap.super.remove(key);
                final SkipListHeadNode newHead = getHead();

                // Only update the data if the head of the skip list has changed
                if (combinedNode.getBitMapNode().getNext()[combinedNode.getHashDigit()] != newHead.getPosition()) {
                    combinedNode.setHead(newHead);
                    DiskMultiMatrixHashMap.this.updateHashMatrixReference(combinedNode.getBitMapNode(), combinedNode.getHashDigit(), newHead.getPosition());
                    DiskMultiMatrixHashMap.this.getHashMatrixNodeCache().remove(combinedNode.getBitMapNode().getPosition());
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
        final CombinedIndexHashMatrixNode combinedNode = getHeadReferenceForKey(key, true);
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
        for (DiskSkipListMap<K, V> kvDiskSkipListMap : this.getMaps()) {
            if (kvDiskSkipListMap.containsValue(value))
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
            DiskMultiMatrixHashMap.super.clear();
            return null;
        });
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
        final CombinedIndexHashMatrixNode combinedNode = getHeadReferenceForKey(key, false);
        if(combinedNode == null)
            return -1;
        setHead(combinedNode.getHead());

        if (combinedNode.getHead() != null) {
            return super.getRecID((K)key);
        }
        return 0;
    }

    /**
     * The nuts and bolts of the map lie here.  This finds the head of the skip list based on the key
     * It uses the bitmap index on the disk map.
     *
     * @param key       Unique identifier that is changed to a non unique key in order to generify the skip list
     * @param forInsert Whether we should insert the bitmap index.
     * @return The Combined Index data of the skip list and it contains the bitmap data information.
     * @since 1.2.0
     */
    private CombinedIndexHashMatrixNode getHeadReferenceForKey(Object key, @SuppressWarnings("SameParameterValue") boolean forInsert) {
        int hash = Math.abs(hash(key));
        int hashDigits[] = getHashDigits(hash);

        int skipListMapId = getSkipListKey(key);

        if (forInsert) {
            CombinedIndexHashMatrixNode retVal = skipListMapCache.get(skipListMapId);
            if (retVal != null)
                return retVal;
            return (CombinedIndexHashMatrixNode) this.getReadWriteLock().performWithLock(getReference(), o -> skipListMapCache.computeIfAbsent(skipListMapId, integer -> seek(forInsert, hashDigits)));
        } else {
            CombinedIndexHashMatrixNode node = skipListMapCache.get(skipListMapId);
            if (node != null)
                return node;
            return seek(false, hashDigits);
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
    private CombinedIndexHashMatrixNode seek(boolean forInsert, final int[] hashDigits) {

        HashMatrixNode node;

        if (this.getReference().getFirstNode() > 0) {
            node = this.getHashMatrixNode(this.getReference().getFirstNode()); // Get Root data
        } else {
            // No default data, lets create one // It must mean we are inserting
            node = new HashMatrixNode();
            node.setPosition(getFileStore().allocate(this.getHashMatrixNodeSize()));

            this.writeHashMatrixNode(node.getPosition(), node);
            this.forceUpdateHeaderFirstNode(getReference(), node.getPosition());
        }

        // There is no default data return -1 because it was not found
        if (!forInsert && node == null)
            return null;

        HashMatrixNode previousNode = node;
        long nodePosition;
        int hashDigit;

        // Not we are using this load factor rather than what is on the Bitmap disk map
        // Break down the nodes and iterate through them.  We should be left with the remaining data which should point us to the record
        for (int level = 0; level < getLoadFactor(); level++) {

            hashDigit = hashDigits[level];
            nodePosition = previousNode.getNext()[hashDigit];

            if (nodePosition == 0 && forInsert) {
                if (level == getLoadFactor() - 1) {
                    SkipListHeadNode headNode = createHeadNode(Byte.MIN_VALUE, 0L, 0L);
                    this.updateHashMatrixReference(previousNode, hashDigit, headNode.getPosition());
                    return new CombinedIndexHashMatrixNode(headNode, previousNode, hashDigit);
                } else {
                    node = new HashMatrixNode();
                    node.setPosition(getFileStore().allocate(this.getHashMatrixNodeSize()));
                    this.writeHashMatrixNode(node.getPosition(), node);
                    this.updateHashMatrixReference(previousNode, hashDigit, node.getPosition());
                }

                previousNode = node;
            }

            // Not found because it is not in the
            else if (nodePosition == 0)
                return null;
            else if (level < getLoadFactor() - 1)
                previousNode = this.getHashMatrixNode(nodePosition);
            else {
                return new CombinedIndexHashMatrixNode(findNodeAtPosition(nodePosition), previousNode, hashDigit);
            }
        }

        return null; // This should contain the drones you are looking for (Star Wars reference) // This contains the key to the linked list
    }

    /**
     * Find all references above and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @since 1.2.0
     * @return A Set of references
     */
    public Set<Long> above(K index, boolean includeFirst)
    {
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
     * @param index The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    public Set<Long> below(K index, boolean includeFirst)
    {
        Set returnValue = new HashSet();

        for (Object o : getMaps()) {
            setHead((SkipListHeadNode) o);
            returnValue.addAll(super.below(index, includeFirst));
        }
        return returnValue;
    }

    /**
     * Get the read write lock
     *
     * @return Implementation of a level read write lock
     */
    public DispatchLock getReadWriteLock()
    {
        return dispatchLock;
    }

}
