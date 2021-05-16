package com.onyx.diskmap.impl.base.skiplist

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.PutResult
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.impl.base.AbstractDiskMap
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.forceCompare
import com.onyx.extension.common.long
import com.onyx.lang.map.OptimisticLockingMap
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
abstract class AbstractSkipList<K, V> constructor(override val fileStore: Store, header: Header, keyType:Class<*>, canStoreKeyWithinNode:Boolean) : AbstractDiskMap<K, V>(fileStore, header, keyType, canStoreKeyWithinNode) {

    protected var nodeCache: MutableMap<Long, SkipNode?> = OptimisticLockingMap(WeakHashMap())

    init {
        determineHead()
    }

    protected var head: SkipNode? = null

    /**
     * Determine the head.  This will vary based on if it is attached or detached.
     * Detached being, no owning class controls the threading and therefore can use
     * a thread local head
     *
     */
    private fun determineHead() {
        if (reference.firstNode > 0L) {
            head = findNodeAtPosition(reference.firstNode)
        } else {
            val newHead = SkipNode.create(fileStore)
            head = newHead
            this.reference.firstNode = newHead.position
            updateHeaderFirstNode(this.reference, this.reference.firstNode)
        }
    }

    protected open fun findNodeAtPosition(position: Long):SkipNode? = if(position == 0L) null else SkipNode.get(fileStore, position)

    override fun containsKey(key: K): Boolean = find(key) != null

    /**
     * Put key value.  This is the same as map.put(K,V) except
     * rather than the value you just put into the map, it will
     * return the record id.  The purpose of this is so you
     * do not have to fetch the record id and search the skip list
     * again after inserting the record.
     *
     * @param key Primary Key
     * @param value Value to insert or update
     * @since 2.1.3
     * @return Value for previous record ID and if the value is been updated or inserted
     */
    fun internalPutAndGet(key: K, value: V, preUpdate:((Long) -> Unit)?): PutResult {
        var nearest:SkipNode = nearest(key)!!

        val result = PutResult(key as Any, !(nearest.isRecord && isEqual(key, nearest.getKey(fileStore, storeKeyWithinNode, keyType))))
        result.recordId = if(!result.isInsert) nearest.position else -1L

        if(preUpdate != null) {
            // The purpose of getting and setting the head is in the event there is a recursive call within the pre-update
            // method.  That would scratch out the head and we have to re-set it
            val tempHead = head
            preUpdate.invoke(result.recordId)
            head = tempHead
        }

        val valueLocation:Long = fileStore.writeObject(value)

        if(!result.isInsert) {

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
            val keyLocation:Long = if(storeKeyWithinNode) key.long() else fileStore.writeObject(key)

            //Stuff in between nearest and its right partner
            var insertedNode:SkipNode = insertNode(keyLocation, valueLocation, nearest, null, 0)
            updateNodeCache(insertedNode)
            var level:Short = 0.toShort()

            result.recordId = insertedNode.position

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

        return result
    }

    /**
     * Put a key value into the Map.  The underlying algorithm for searching is a Skip List
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return What we just put in
     */
    override fun put(key: K, value: V): V {
        this@AbstractSkipList.internalPutAndGet(key, value, null)
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
    protected open fun deleteNode(node:SkipNode, head:SkipNode) {
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
    }
}