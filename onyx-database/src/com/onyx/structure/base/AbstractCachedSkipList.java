package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.node.SkipListNode;
import com.onyx.structure.store.Store;

import java.util.Collections;
import java.util.WeakHashMap;

/**
 * Created by tosborn1 on 1/7/17.
 *
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.  This contains the caching implementation of the SkipList.  It will try to
 * maintain a weak reference of all the values.  If memory consumption goes to far, it will start flushing those
 * from the elastic cache.
 *
 * @since 1.2.0
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 */
abstract class AbstractCachedSkipList<K,V> extends AbstractSkipList<K,V>
{

    /**
     * Constructor defines the caching medium for the nodes and values.
     * @param fileStore Underlying storage mechanism
     * @param header Header location of the skip list
     *
     * @since 1.2.0
     */
    AbstractCachedSkipList(Store fileStore, Header header) {
        super(fileStore, header);
    }

    /**
     * Finds a value.  First, checks the cache.  If it does not exist in the cache, it calls the super.
     *
     * @param position The position within the file structure to pull it from
     * @param recordSize How many bytes we must read to get the object
     * @return The corresponding value at the position.
     *
     * @since 1.2.0
     */
    @Override
    protected V findValueAtPosition(long position, int recordSize) {
        return valueCache.compute(position, (aLong, v) -> {
            if(v != null)
                return v;
            return AbstractCachedSkipList.super.findValueAtPosition(position, recordSize);
        });
    }

    /**
     * Update the node value.  This must also maintain the cache by removing the old value and updating it to the new one.
     *
     * @param node Record reference
     * @param value The value of the reference
     *
     * @since 1.2.0
     */
    @Override
    protected void updateNodeValue(SkipListNode<K> node, V value) {
        valueCache.remove(node.recordPosition);
        super.updateNodeValue(node, value);
        valueCache.put(node.recordPosition, value);
    }

    /**
     * Creates and caches the new node as well as its value.
     *
     * @param key Key Identifier
     * @param value Record value
     * @param level What level it exists within the skip list
     * @param next The next value in the skip list
     * @param down Reference to the next level
     *
     * @return The instantiated and configured node.
     *
     * @since 1.2.0
     */
    @Override
    protected SkipListNode<K> createNewNode(K key, V value, byte level, long next, long down) {
        final SkipListNode<K> newNode = super.createNewNode(key, value, level, next, down);
        nodeCache.put(newNode.position, newNode);
        valueCache.put(newNode.recordPosition, value);
        return newNode;
    }

    /**
     * Find a node at a position.  First check the cache.  If it is in there great return it otherwise go the the
     * store to find it.
     *
     * @param position Position the node can be found at.
     * @return The SkipListNode at that position.
     *
     * @since 1.2.0
     */
    protected SkipListNode<K> findNodeAtPosition(final long position) {
        return nodeCache.compute(position, (aLong, kSkipListNode) -> {
            if(kSkipListNode != null)
                return kSkipListNode;
            return AbstractCachedSkipList.super.findNodeAtPosition(position);
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        keyCache.remove(key);
//        keyValueCache.remove(key);
        return super.remove(key);
    }
    /*
    @Override
    public V put(K key, V value) {
        keyValueCache.put(key, value);
        return super.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        keyCache.remove(key);
        keyValueCache.remove(key);
        return super.remove(key);
    }

    @Override
    public V get(Object key)
    {
        return keyValueCache.compute((K)key, (k, v) -> {
            if(v != null)
                return v;
            return AbstractCachedSkipList.super.get(key);
        });
    }
*/
    /**
     * Clear the cache
     */
    @Override
    public void clear()
    {
        nodeCache = Collections.synchronizedMap(new WeakHashMap<Long, SkipListNode<K>>());
        valueCache = Collections.synchronizedMap(new WeakHashMap<Long, V>());
        keyCache = Collections.synchronizedMap(new WeakHashMap<K, SkipListNode<K>>());
        keyValueCache = Collections.synchronizedMap(new WeakHashMap<K, V>());
    }

    /**
     * Find the node associated to the key.  This must have an exact match.
     * @param key The Key identifier
     * @return Its corresponding node
     * @since 1.2.0
     */
    protected SkipListNode<K> find(K key) {
        return keyCache.compute(key, (k, node) -> {
            if(node != null)
                return node;

            return AbstractCachedSkipList.super.find(key);
        });
    }
}
