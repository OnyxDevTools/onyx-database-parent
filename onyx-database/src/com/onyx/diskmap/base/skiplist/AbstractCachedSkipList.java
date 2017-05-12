package com.onyx.diskmap.base.skiplist;

import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.node.SkipListHeadNode;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.diskmap.store.Store;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.WriteSynchronizedMap;

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

    // Caching maps
    protected Map<K, SkipListNode> keyCache = new WriteSynchronizedMap<>(new CompatWeakHashMap<>());
    protected Map<Long, V> valueByPositionCache = new WriteSynchronizedMap<>(new CompatWeakHashMap<>());

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
    AbstractCachedSkipList(Store fileStore, Header header, boolean headless) {
        super(fileStore, header, headless);
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
    protected SkipListNode<K> createNewNode(K key, V value, byte level, long next, long down, boolean cache, long recordId) {
        final SkipListNode<K> newNode = super.createNewNode(key, value, level, next, down, cache, recordId);
        nodeCache.put(newNode.position, newNode);
        if (cache) {
            keyCache.put(key, newNode);
            valueByPositionCache.put(newNode.recordPosition, value);
        }
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
     * Remove the node from the cache before updating.  Apparantly the reference of the node is not sufficient and
     * it needs to be removed so that it can be refreshed from disk.
     *
     * @param node Node to update
     * @param position position to set the node.down to
     *
     */
    @Override
    protected void updateNodeNext(SkipListHeadNode node, long position) {
        nodeCache.remove(node.position);
        super.updateNodeNext(node, position);
    }

    /**
     * Update the Node value.  This is only done upon update to the map.
     * This is intended to do a cleanup of the cache
     *
     * @param node Node that's reference was cleaned up
     * @param value The value of the reference
     * @since 1.2.0
     */
    protected void updateNodeValue(SkipListNode<K> node, V value, boolean cache)
    {
        // Remove the old value before updating
        if (cache) {
            valueByPositionCache.remove(node.recordPosition);
        }
        // Update and cache the new value
        nodeCache.put(node.position, node);
        super.updateNodeValue(node, value, cache);

        if (cache) {
            keyCache.put(node.key, node);
            valueByPositionCache.put(node.recordPosition, value);
        }
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
            valueByPositionCache.remove(((SkipListNode) node).recordPosition);
        }
    }

    /**
     * Clear the cache
     * @since 1.2.0
     */
    @Override
    public void clear() {
        nodeCache.clear();
        keyCache.clear();
        valueByPositionCache.clear();
    }
}
