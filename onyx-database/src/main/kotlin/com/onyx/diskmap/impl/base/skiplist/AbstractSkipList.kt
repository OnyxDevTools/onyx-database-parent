package com.onyx.diskmap.impl.base.skiplist

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.PutResult
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.impl.base.AbstractDiskMap
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.castTo
import com.onyx.extension.common.forceCompare
import com.onyx.extension.common.long
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.query.QueryCriteriaOperator
import java.lang.ref.WeakReference
import java.util.*
import kotlin.reflect.KClass

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
abstract class AbstractSkipList<K, V>(
    store: WeakReference<Store>,
    recordStore: WeakReference<Store>,
    header: Header,
    keyType: Class<*>
) : AbstractDiskMap<K, V>(store, recordStore, header, keyType) {

    protected open var nodeCache = OptimisticLockingMap<Long, SkipNode?>(WeakHashMap())

    init {
        @Suppress("LeakingThis")
        determineHead()
    }

    protected var head: SkipNode? = null

    /**
     * Determine the head.  This will vary based on if it is attached or detached.
     * Detached being, no owning class controls the threading and therefore can use
     * a thread local head
     *
     */
    protected open fun determineHead() {
        if (reference.firstNode > 0L) {
            head = findNodeAtPosition(reference.firstNode)
        } else {
            val newHead = SkipNode.create(fileStore)
            head = newHead
            updateHeaderFirstNode(this.reference, newHead.position)
            head?.write(store.get()!!)
        }
    }

    protected open fun findNodeAtPosition(position: Long): SkipNode? =
        if (position == 0L) null else SkipNode.get(fileStore, position)

    override fun containsKey(key: K): Boolean = find(key) != null

    private val update = arrayOfNulls<SkipNode?>(34)

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
    fun internalPutAndGet(key: K, value: V, preUpdate: ((Long) -> Unit)?): PutResult {

        val foundNodeBottomMost = searchAndCollectPredecessors(key)

        val result = PutResult(key as Any, foundNodeBottomMost == null)
        result.recordId = foundNodeBottomMost?.position ?: -1L

        if (preUpdate != null) {
            // The purpose of getting and setting the head is in the event there is a recursive call within the pre-update
            // method.  That would scratch out the head and we have to re-set it
            val tempHead = head
            preUpdate.invoke(result.recordId)
            head = tempHead
        }

        val valueLocation: Long = records.writeObject(value)

        if (!result.isInsert && foundNodeBottomMost != null) {
            foundNodeBottomMost.setRecord(fileStore, valueLocation)
            updateNodeCache(foundNodeBottomMost)
            updateKeyCache(key)
        } else {
            val keyLocation: Long = if (storeKeyWithinNode) key.long() else records.writeObject(key)

            var newNodeLevel = 0
            while (coinToss() && newNodeLevel < (34 - 1)) {
                newNodeLevel++
            }

            var currentOverallHead = this.head!!

            if (newNodeLevel > currentOverallHead.level.toInt()) {
                for (lvl in (currentOverallHead.level.toInt() + 1)..newNodeLevel) {
                    val oldHead = this.head!!
                    val newHeadNode = SkipNode.create(
                        fileStore,
                        key = 0L,
                        value = 0L,
                        left = 0L,
                        right = 0L,
                        bottom = oldHead.position,
                        level = lvl.toUByte()
                    )
                    updateNodeCache(newHeadNode)

                    this.head = newHeadNode
                    updateHeaderFirstNode(reference, newHeadNode.position)
                    update[lvl] = newHeadNode
                }
                currentOverallHead = this.head!!
            }

            var insertedNodeTowerBottom: SkipNode? = null

            for (currentLevel in 0..newNodeLevel) {
                val predecessorAtLevel = update[currentLevel]
                val pred = predecessorAtLevel ?: headAtLevel(currentLevel)

                val rightOfPredecessorPos = pred.right

                val newNodeAtCurrentLevel = SkipNode.create(
                    fileStore,
                    keyLocation,
                    if (currentLevel == 0) valueLocation else 0L,
                    pred.position,
                    rightOfPredecessorPos,
                    insertedNodeTowerBottom?.position ?: 0L,
                    currentLevel.toUByte()
                )
                updateNodeCache(newNodeAtCurrentLevel)

                pred.setRight(fileStore, newNodeAtCurrentLevel.position)
                updateNodeCache(pred)

                if (rightOfPredecessorPos > 0L) {
                    val oldRightNode = findNodeAtPosition(rightOfPredecessorPos)!!
                    oldRightNode.setLeft(fileStore, newNodeAtCurrentLevel.position)
                    updateNodeCache(oldRightNode)
                }

                insertedNodeTowerBottom = newNodeAtCurrentLevel

                if (currentLevel == 0) {
                    result.recordId =
                        newNodeAtCurrentLevel.position
                }
            }
            incrementSize()
        }

        return result
    }

    /**
     * Placeholder for the crucial search method that populates predecessors.
     * This method needs to be implemented in your AbstractSkipList class.
     *
     * @param key The key to search for.
     * @return The bottom-most SkipNode if the key is found, otherwise null.
     */
    protected open fun searchAndCollectPredecessors(key: K): SkipNode? {
        for (i in update.indices) update[i] = null

        var currentNodeAtCurrentLevel: SkipNode = this.head ?: return null

        val headLevel = currentNodeAtCurrentLevel.level.toInt()

        for (level in headLevel downTo 0) {
            while (true) {
                val rightNodePosition = currentNodeAtCurrentLevel.right
                if (rightNodePosition == 0L) break

                val rightNode = findNodeAtPosition(rightNodePosition) ?: break

                val keyOfRightNode: K = rightNode.getKey(records, storeKeyWithinNode, keyType)
                if (isGreater(key, keyOfRightNode)) {
                    currentNodeAtCurrentLevel = rightNode
                } else break
            }
            update[level] = currentNodeAtCurrentLevel

            if (level > 0) {
                val downNodePosition = currentNodeAtCurrentLevel.down
                if (downNodePosition > 0L) {
                    val downNode = findNodeAtPosition(downNodePosition) ?: return null
                    currentNodeAtCurrentLevel = downNode
                } else {
                    return null
                }
            }
        }

        val predecessorAtLevel0 = update[0]

        if (predecessorAtLevel0 != null && predecessorAtLevel0.right > 0L) {
            val candidateNodePosition = predecessorAtLevel0.right
            val candidateNode = findNodeAtPosition(candidateNodePosition)

            if (candidateNode != null) {
                val candidateKey = candidateNode.getKey<K>(records, storeKeyWithinNode, keyType)
                if (isEqual(key, candidateKey)) {
                    var bottomMostNodeOfTower = candidateNode
                    while (bottomMostNodeOfTower!!.down > 0L) {
                        val nextNodeDownInTower = findNodeAtPosition(bottomMostNodeOfTower.down) ?: break
                        bottomMostNodeOfTower = nextNodeDownInTower
                    }
                    return bottomMostNodeOfTower
                }
            }
        }

        return null
    }

    /**
     * Get the head at a level
     */
    private fun headAtLevel(targetLevel: Int): SkipNode {
        var targetHead = requireNotNull(head)
        var level = targetHead.level.toInt()
        while (level > targetLevel) {
            val down = targetHead.down
            targetHead = findNodeAtPosition(down)!!
            level--
        }
        return targetHead
    }

    /**
     * Put a key value into the Map.  The underlying algorithm for searching is a Skip List
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return What we just put in
     */
    override fun put(key: K, value: V): V {
        this@AbstractSkipList.internalPutAndGet(key.castTo(keyType) as K, value, null)
        return value
    }

    /**
     * Remove The Key and value from the Map.
     *
     * @param key Key Identifier
     * @return The value that was removed.  Null if it does not exist
     * @since 1.2.0
     */
    override fun remove(key: K): V? {
        val nodeToDeleteBottomMost = searchAndCollectPredecessors(key)

        if (nodeToDeleteBottomMost != null &&
            nodeToDeleteBottomMost.isRecord &&
            isEqual(key, nodeToDeleteBottomMost.getKey(records, storeKeyWithinNode, keyType))
        ) {
            val returnValue: V? = nodeToDeleteBottomMost.getRecord(records)

            for (i in 0..this.head!!.level.toInt()) {
                val predecessorAtLevelI = update[i] ?: continue

                val nodeAfterPredecessorPos = predecessorAtLevelI.right
                if (nodeAfterPredecessorPos > 0L) {
                    val nodeToDeleteAtLevelI = findNodeAtPosition(nodeAfterPredecessorPos)
                    if (nodeToDeleteAtLevelI != null) {
                        if (isEqual(key, nodeToDeleteAtLevelI.getKey(records, storeKeyWithinNode, keyType))) {
                            predecessorAtLevelI.setRight(fileStore, nodeToDeleteAtLevelI.right)
                            updateNodeCache(predecessorAtLevelI)
                            if (nodeToDeleteAtLevelI.right > 0L) {
                                val successorNode = findNodeAtPosition(nodeToDeleteAtLevelI.right)
                                if (successorNode != null) {
                                    successorNode.setLeft(fileStore, predecessorAtLevelI.position)
                                    updateNodeCache(successorNode)
                                }
                            }
                        }
                    }
                }
            }

            var currentHead = this.head!!
            while (currentHead.level > 0.toUByte() && currentHead.right == 0L) {
                val newHeadCandidate = findNodeAtPosition(currentHead.down) ?: continue
                this.head = newHeadCandidate
                updateHeaderFirstNode(reference, newHeadCandidate.position)
                updateNodeCache(this.head)
                currentHead = this.head!!
            }

            decrementSize()
            updateKeyCache(key)
            return returnValue
        }

        return null
    }

    /**
     * Get value from map
     */
    override fun get(key: K): V? = find(key.castTo(keyType) as K)?.getRecord(records)

    /**
     * Find matching value.  This is different than nearest because it will return null
     * if it is not a match.
     *
     */
    protected open fun find(key: K): SkipNode? {
        val nearest = nearest(key) ?: return null
        return if (nearest.isRecord && isEqual(key, nearest.getKey(records, storeKeyWithinNode, keyType))) {
            var bottomNode = nearest
            while (bottomNode.down > 0) {
                bottomNode = findNodeAtPosition(bottomNode.down)!!
            }
            bottomNode
        } else {
            null
        }
    }

    /**
     * Find the nearest node on the bottom level
     */
    protected open fun nearest(key: K): SkipNode? {
        var current: SkipNode = head!!
        var found = false
        moveDownLoop@ while (true) {
            moveRightLoop@ while (current.right > 0L && !found) {
                val next: SkipNode = findNodeAtPosition(current.right)!!

                // Skip sentinel nodes that have no associated key
                if (next.key == 0L) {
                    if (next.position == current.position) break@moveRightLoop
                    current = next
                    continue@moveRightLoop
                }

                @Suppress("UNCHECKED_CAST")
                val nextKey = next.getKey<Any?>(records, storeKeyWithinNode, keyType) as K

                current = when {
                    isEqual(key, nextKey) -> {
                        found = true
                        next
                    }

                    isGreater(key, nextKey) -> {
                        if (next.position == current.position) break@moveRightLoop
                        next
                    }

                    else -> break@moveRightLoop
                }
            }

            if (current.down > 0L) {
                val down = findNodeAtPosition(current.down) ?: break@moveDownLoop
                // Guard against vertical self-link cycle.
                if (down.position == current.position) break@moveDownLoop
                current = down
            } else {
                break@moveDownLoop
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
    abstract fun updateNodeCache(node: SkipNode?)

    /**
     * Abstract method for updating cache for a value
     */
    abstract fun updateKeyCache(node: K)

    companion object {
        private fun <K> isGreater(key: K, key2: K): Boolean = key2.forceCompare(key, QueryCriteriaOperator.GREATER_THAN)
        private fun <K> isEqual(key: K, key2: K): Boolean = key.forceCompare(key2, QueryCriteriaOperator.EQUAL)
        private fun coinToss() = Math.random() < 0.3

        fun Any?.cast(type: Class<*>): Any? {
            val kotlinClass: KClass<*> = type.kotlin
            return when {
                this is String && kotlinClass != String::class -> return when (kotlinClass) {
                    Int::class -> this.toIntOrNull() ?: 0
                    Long::class -> this.toLongOrNull() ?: 0L
                    Double::class -> this.toDoubleOrNull() ?: 0.0
                    Float::class -> this.toFloatOrNull() ?: 0.0f
                    Boolean::class -> (this.toIntOrNull() ?: 0) != 0
                    Char::class -> this.chars().findFirst()
                    Byte::class -> this.toByteOrNull() ?: 0
                    Short::class -> this.toShortOrNull() ?: 0
                    Date::class -> Date(this.toLongOrNull() ?: 0L)
                    else -> this
                }

                else -> this
            }
        }

    }
}