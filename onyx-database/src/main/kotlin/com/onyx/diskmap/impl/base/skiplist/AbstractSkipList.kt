package com.onyx.diskmap.impl.base.skiplist

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.impl.base.AbstractDiskMap
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.forceCompare
import com.onyx.extension.common.long
import com.onyx.lang.map.WriteSynchronizedMap
import com.onyx.persistence.query.QueryCriteriaOperator
import java.util.*

/**
 * Created by Tim Osborn on 1/7/17.
 * <p>
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.  This contains the base level skip list logic and the store i/o.
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 * @since 1.2.0
 * @since 2.0.0 This was refactored to make simpler.  It now conforms better to an actual skip list
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractSkipList<K, V> @JvmOverloads constructor(override val fileStore: Store, header: Header, detached: Boolean = false, keyType:Class<*>, canStoreKeyWithinNode:Boolean) : AbstractDiskMap<K, V>(fileStore, header, detached, keyType, canStoreKeyWithinNode) {

    protected var nodeCache: MutableMap<Long, SkipNode?> = WriteSynchronizedMap(WeakHashMap())

    init {
        determineHead()
    }

    /**
     * If the map is detached it means there could be any number of threads using it as a different map.  For that
     * reason there was a thread-local pool of heads.
     *
     * @since 1.2.0
     *
     * @return The head data.
     */
    protected var head: SkipNode? = null
        get() = if (detached)
            threadLocalHead.get()
        else
            field
        set(value) = if (detached)
            threadLocalHead.set(value)
        else
            field = value

    /**
     * Determine the head.  This will vary based on if it is attached or detached.
     * Detached being, no owning class controls the threading and therefore can use
     * a thread local head
     *
     */
    private fun determineHead() {
        if (!detached) {
            if (reference.firstNode > 0L) {
                head = findNodeAtPosition(reference.firstNode)
            } else {
                val newHead = SkipNode.create(fileStore)
                head = newHead
                this.reference.firstNode = newHead.position
                updateHeaderFirstNode(this.reference, this.reference.firstNode)
            }
        }
    }

    open protected fun findNodeAtPosition(position: Long):SkipNode? = if(position == 0L) null else SkipNode.get(fileStore, position)

    override fun containsKey(key: K): Boolean = find(key) != null

    /**
     * Put a key value into the Map.  The underlying algorithm for searching is a Skip List
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return What we just put in
     */
    override fun put(key: K, value: V): V {
        val valueLocation:Long = fileStore.writeObject(value)
        var nearest:SkipNode = nearest(key)!!

        if(nearest.isRecord && isEqual(key, nearest.getKey(fileStore, storeKeyWithinNode, keyType))) {

            nearest.setRecord(fileStore, valueLocation)
            updateNodeCache(nearest)
            updateKeyCache(key)

            // Update Entire column
            while(nearest.up > 0) {
                nearest = findNodeAtPosition(nearest.up)!!
                nearest.setRecord(fileStore, valueLocation)
                updateNodeCache(nearest)
            }

        } else {

            var head:SkipNode = this.head!!
            val keyLocation:Long = if(storeKeyWithinNode) key!!.long() else fileStore.writeObject(key)

            //Stuff in between nearest and its right partner
            var insertedNode:SkipNode = insertNode(keyLocation, valueLocation, nearest, null, 0)
            updateNodeCache(insertedNode)
            var level:Short = 0.toShort()

            // Create Layers
            while(coinToss()) {

                // Add another level
                if(level >= head.level) {
                    val newHead = SkipNode.create(fileStore, 0L, 0L, 0L, 0L, head.position, level)
                    updateNodeCache(newHead)
                    head.setTop(fileStore, newHead.position)
                    updateNodeCache(head)
                    this.head = newHead
                    head = newHead
                    updateHeaderFirstNode(reference, newHead.position)
                }

                // Find the first with a top
                while(nearest.up == 0L && nearest.left > 0L)
                    nearest = findNodeAtPosition(nearest.left)!!

                if(nearest.up > 0)
                    nearest = findNodeAtPosition(nearest.up)!!

                insertedNode = insertNode(keyLocation, valueLocation, nearest, insertedNode, level)
                updateNodeCache(insertedNode)
                level++
            }

            incrementSize()
        }

        return value
    }

    /**
     * Insert a new node between 2 other nodes
     * @since 2.0.0
     */
    private fun insertNode(key:Long, value:Long, left:SkipNode?, bottom:SkipNode?, level:Short):SkipNode {
        val right:SkipNode? = if(left?.right ?: 0L > 0L) findNodeAtPosition(left!!.right) else null

        val newNode = SkipNode.create(fileStore, key, value, left?.position ?: 0L, left?.right ?: 0L, bottom?.position ?: 0L, level)
        updateNodeCache(newNode)
        right?.setLeft(fileStore, newNode.position)
        updateNodeCache(right)
        left?.setRight(fileStore, newNode.position)
        updateNodeCache(left)
        bottom?.setTop(fileStore, newNode.position)
        updateNodeCache(bottom)
        return newNode
    }

    /**
     * Remove The Key and value from the Map.
     *
     * @param key Key Identifier
     * @return The value that was removed.  Null if it does not exist
     * @since 1.2.0
     */
    override fun remove(key: K): V? {
        val nearest:SkipNode = nearest(key)!!
        var returnValue:V? = null
        val head = head!!

        if(nearest.isRecord && isEqual(key, nearest.getKey(fileStore, storeKeyWithinNode, keyType))) {

            returnValue = nearest.getRecord(fileStore)
            deleteNode(nearest, head)
            var foundNode:SkipNode = nearest

            // Delete All Above
            while(foundNode.up > 0) {
                foundNode = findNodeAtPosition(foundNode.up)!!
                deleteNode(foundNode, head)
            }

            decrementSize()
            updateKeyCache(key)
        }

        return returnValue
    }

    /**
     * Delete a node and set values for neighboring nodes
     *
     */
    open protected fun deleteNode(node:SkipNode, head:SkipNode) {
        val leftNode:SkipNode? = if(node.left > 0) findNodeAtPosition(node.left) else null
        val rightNode:SkipNode? = if(node.right > 0) findNodeAtPosition(node.right) else null
        leftNode?.setRight(fileStore, rightNode?.position ?: 0L)
        updateNodeCache(leftNode)

        if(leftNode?.position == head.position)
            this.head = leftNode
        rightNode?.setLeft(fileStore, leftNode?.position ?: 0L)
        updateNodeCache(rightNode)
    }

    /**
     * Get value from map
     */
    override fun get(key: K): V? = find(key)?.getRecord(fileStore)

    /**
     * Find matching value.  This is different than nearest because it will return null
     * if it is not a match.
     *
     */
    protected open fun find(key: K): SkipNode? {
        val nearest:SkipNode = nearest(key) ?: return null
        return if(nearest.isRecord && isEqual(key, nearest.getKey(fileStore, storeKeyWithinNode, keyType)))
            nearest
        else
            null
    }

    /**
     * Find the nearest node on the bottom level
     */
    protected open fun nearest(key: K): SkipNode? {
        var current: SkipNode = head!!
        var found = false
        moveDownLoop@while(true) {
            moveRightLoop@ while (current.right > 0L && !found) {
                val next: SkipNode? = findNodeAtPosition(current.right)
                val nextKey: K = next?.getKey(fileStore, storeKeyWithinNode, keyType)!!

                current = when {
                    isEqual(key, nextKey) -> {
                        found = true
                        next
                    }
                    isGreater(key, nextKey) -> next
                    else -> break@moveRightLoop
                }
            }

            when {
                current.down > 0L -> current = findNodeAtPosition(current.down)!!
                else -> break@moveDownLoop
            }
        }

        return current
    }

    override fun clearCache() {
        nodeCache.clear()
    }

    /**
     * Abstract method for updating cache for a node
     */
    abstract fun updateNodeCache(node:SkipNode?)

    /**
     * Abstract method for updating cache for a value
     */
    abstract fun updateKeyCache(node:K)

    companion object {

        private fun <K> isGreater(key: K, key2: K): Boolean = key2.forceCompare(key, QueryCriteriaOperator.GREATER_THAN)
        private fun <K> isEqual(key: K, key2: K): Boolean = key.forceCompare(key2, QueryCriteriaOperator.EQUAL)
        private fun coinToss() = Math.random() < 0.5

        private val threadLocalHead: ThreadLocal<SkipNode> = ThreadLocal()

    }
}