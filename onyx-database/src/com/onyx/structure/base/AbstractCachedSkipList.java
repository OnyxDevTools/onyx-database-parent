package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.node.SkipListHeadNode;
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
     * Constructor defines the caching medium for the nodes and values.
     * @param fileStore Underlying storage mechanism
     * @param header Header location of the skip list
     * @param headless Whether the header should be ignored or not
     *
     * @since 1.2.0
     */
    AbstractCachedSkipList(Store fileStore, Header header, boolean headless) {
        super(fileStore, header, headless);
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
        return newNode;
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
    protected SkipListHeadNode createHeadNode(byte level, long next, long down) {
        final SkipListHeadNode newNode = super.createHeadNode(level, next, down);
        nodeCache.put(newNode.position, newNode);
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
    protected SkipListHeadNode findNodeAtPosition(final long position) {
        if(position == 0L)
            return null;

        SkipListHeadNode node = nodeCache.get(position);
        if(node != null)
            return node;

        node = super.findNodeAtPosition(position);
        nodeCache.put(position, node);
        return node;
    }

    @Override
    public V put(K key, V value) {
        valueCache.put(key, value);
        return super.put(key, value);
    }


    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        valueCache.remove(key);
        return super.remove(key);
    }
    @Override
    public V get(Object key) {
        V value = valueCache.get(key);
        if(value == null)
            value = super.get(key);
        return value;
    }


    /**
     * Clear the cache
     */
    @Override
    public void clear()
    {
        nodeCache = Collections.synchronizedMap(new WeakHashMap<Long, SkipListHeadNode>());
    }
}
