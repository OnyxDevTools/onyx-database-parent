package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.node.SkipListHeadNode;
import com.onyx.structure.node.SkipListNode;
import com.onyx.structure.store.Store;

import java.util.Map;

/**
 * Created by tosborn1 on 1/7/17.
 * <p>
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.  This contains the caching implementation of the SkipList.  It will try to
 * maintain a weak reference of all the values.  If memory consumption goes to far, it will start flushing those
 * from the elastic cache.
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 * @since 1.2.0
 */
abstract class AbstractCachedSkipList<K, V> extends AbstractSkipList<K, V> {

    public AbstractCachedSkipList()
    {

    }
    /**
     * Constructor defines the caching medium for the nodes and values.
     *
     * @param fileStore Underlying storage mechanism
     * @param header    Header location of the skip list
     * @since 1.2.0
     */
    AbstractCachedSkipList(Store fileStore, Header header) {
        super(fileStore, header);
    }

    /**
     * Constructor defines the caching medium for the nodes and values.
     *
     * @param fileStore Underlying storage mechanism
     * @param header    Header location of the skip list
     * @param headless  Whether the header should be ignored or not
     * @since 1.2.0
     */
    AbstractCachedSkipList(Store fileStore, Header header, boolean headless, boolean enableCaching) {
        super(fileStore, header, headless);
        if(!enableCaching)
        {
            nodeCache = new EmptyMap();
            valueCache = new EmptyMap();
            keyCache = new EmptyMap();
            valueByPositionCache = new EmptyMap();
        }
    }

    /**
     * Creates and caches the new node as well as its value.
     *
     * @param key   Key Identifier
     * @param value Record value
     * @param level What level it exists within the skip list
     * @param next  The next value in the skip list
     * @param down  Reference to the next level
     * @return The instantiated and configured node.
     * @since 1.2.0
     */
    @Override
    protected SkipListNode<K> createNewNode(K key, V value, byte level, long next, long down) {
        final SkipListNode<K> newNode = super.createNewNode(key, value, level, next, down);
        nodeCache.put(newNode.position, newNode);
        keyCache.put(key, newNode);
        valueCache.put(key, value);
        return newNode;
    }

    /**
     * Creates and caches the new node as well as its value.
     *
     * @param level What level it exists within the skip list
     * @param next  The next value in the skip list
     * @param down  Reference to the next level
     * @return The instantiated and configured node.
     * @since 1.2.0
     */
    @Override
    protected SkipListHeadNode createHeadNode(byte level, long next, long down) {
        final SkipListHeadNode newNode = super.createHeadNode(level, next, down);
        nodeCache.put(newNode.position, newNode);
        return newNode;
    }

    /**
     * Find the value at a position.
     *
     * @param position   The position within the file structure to pull it from
     * @param recordSize How many bytes we must read to get the object
     * @return The value as long as it serialized ok.
     * @since 1.2.0
     */
    @Override
    protected V findValueAtPosition(long position, int recordSize)
    {
        V value = valueByPositionCache.get(position);
        if(value == null)
        {
            value = super.findValueAtPosition(position, recordSize);
            valueByPositionCache.put(position, value);
        }
        return value;
    }

    /**
     * Find a node at a position.  First check the cache.  If it is in there great return it otherwise go the the
     * store to find it.
     *
     * @param position Position the node can be found at.
     * @return The SkipListNode at that position.
     * @since 1.2.0
     */
    protected SkipListHeadNode findNodeAtPosition(final long position) {
        if (position == 0L)
            return null;

        SkipListHeadNode node = nodeCache.get(position);
        if(node == null)
        {
            node = super.findNodeAtPosition(position);
            nodeCache.put(position, node);
        }
        return node;
    }

    /**
     * Map get method.  This checked the cache to see if the key value exists.  If it does it will return the
     * cached value otherwise, it will look to the storage
     * @param key Identifier
     * @return Cached value
     *
     * @since 1.2.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        V value = valueCache.get(key);
        if(value == null)
        {
            value = super.get(key);
            valueCache.put((K)key, value);
        }
        return value;
    }

    /**
     * Find the record reference based on the key
     *
     * @param key The Key identifier
     * @return Record reference node for corresponding key
     *
     * @since 1.2.0
     */
    @Override
    @SuppressWarnings("unchecked")
    protected SkipListNode<K> find(K key) {
        SkipListNode node = keyCache.get(key);
        if(node == null)
        {
            node = super.find(key);
            keyCache.put(key, node);
        }
        return node;
    }

    /**
     * Update the Node value.  This is only done upon update to the map.
     * This is intended to do a cleanup of the cache
     *
     * @param node Node that's reference was cleaned up
     * @param value The value of the reference
     * @since 1.2.0
     */
    protected void updateNodeValue(SkipListNode<K> node, V value)
    {
        // Remove the old value before updating
        valueByPositionCache.remove(node.recordPosition);

        // Update and cache the new value
        super.updateNodeValue(node, value);
        nodeCache.put(node.position, node);
        keyCache.put(node.key, node);
        valueCache.put(node.key, value);
    }

    /**
     * Perform cleanup once the node has been removed
     * @param node Node that is to be removed
     *
     * @since 1.2.0
     */
    @Override
    @SuppressWarnings("SuspiciousMethodCalls")
    protected void removeNode(SkipListHeadNode node)
    {
        nodeCache.remove(node.position);
        if(node instanceof SkipListNode)
        {
            keyCache.remove(((SkipListNode) node).key);
            valueCache.remove(((SkipListNode) node).key);
            valueByPositionCache.remove(((SkipListNode) node).recordPosition);
        }
    }

    /**
     * Clear the cache
     * @since 1.2.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public void clear() {
        nodeCache = new ConcurrentWeakHashMap<>();
        keyCache = new ConcurrentWeakHashMap<>();
        valueCache = new ConcurrentWeakHashMap<>();
        valueByPositionCache = new ConcurrentWeakHashMap<>();
    }
}
