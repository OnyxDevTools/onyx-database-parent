package com.onyx.diskmap.impl

import com.onyx.lang.map.EmptyMap
import com.onyx.diskmap.SortedDiskMap
import com.onyx.diskmap.impl.base.hashmatrix.AbstractIterableHashMatrix
import com.onyx.diskmap.data.CombinedIndexHashMatrixNode
import com.onyx.diskmap.data.HashMatrixNode
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.Store
import com.onyx.lang.concurrent.ClosureReadWriteLock
import com.onyx.lang.concurrent.impl.DefaultClosureReadWriteLock
import com.onyx.lang.map.OptimisticLockingMap

import java.util.*

/**
 * Created by Tim Osborn on 1/8/17.
 *
 *
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
 * The difference between this and the DiskHashMap is that this does not pre define the allocated space for the hash matrix
 * It does not because, if the loadFactor were to be 10, that would be a massive amount of storage space.  For smaller loadFactors
 * where you can afford the allocation, the DiskHashMap will perform better.
 *
 * @since 1.2.0 This was re-factored not to have a dependent sub map.
 */
class DiskMatrixHashMap<K, V> : AbstractIterableHashMatrix<K, V>, SortedDiskMap<K,V> {

    private var mapReadWriteLock: ClosureReadWriteLock = DefaultClosureReadWriteLock()

    // Cache of skip lists
    private val skipListMapCache:MutableMap<Int, CombinedIndexHashMatrixNode> = OptimisticLockingMap(WeakHashMap())

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap˚
     * @since 1.2.0
     */
    constructor(fileStore: Store, header: Header, loadFactor: Int) : super(fileStore, header, true) {
        this.loadFactor = loadFactor.toByte()
    }

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap
     * @since 1.2.1 Added constructor in the event you wanted a different locking implementation
     */
    @Suppress("UNUSED")
    constructor(fileStore: Store, header: Header, loadFactor: Int, closureLock: ClosureReadWriteLock) : super(fileStore, header, true) {
        this.mapReadWriteLock = closureLock
        this.loadFactor = loadFactor.toByte()
        this.hashMatrixNodeCache = EmptyMap()
        this.keyCache = EmptyMap()
    }

    /**
     * Get the value by its corresponding key.
     *
     * @param key Primary key
     * @return The value if it exists
     * @since 1.2.0
     */
    override operator fun get(key: K): V? = mapReadWriteLock.readLock {
        val combinedNode = getHeadReferenceForKey(key, false)

        // Set the selected skip list
        head = combinedNode?.head

        return@readLock if (combinedNode?.head != null) {
            super.get(key)
        } else null
    }

    /**
     * Put the value based on the item's key
     *
     * @param key   Object used to uniquely identify a value
     * @param value Its corresponding value
     * @return The value we just inserted
     * @since 1.2.0
     */
    override fun put(key: K, value: V): V = this.mapReadWriteLock.writeLock {
        val combinedNode = getHeadReferenceForKey(key, true)
        head = combinedNode?.head
        val oldHead = combinedNode?.head
        val returnValue = super@DiskMatrixHashMap.put(key, value)
        val newHead = head

        // Only update the data if the head of the skip list has changed
        if (oldHead != newHead) {
            combinedNode!!.head = newHead!!
            this@DiskMatrixHashMap.updateHashMatrixReference(combinedNode.bitMapNode, combinedNode.hashDigit, newHead.position)
            this@DiskMatrixHashMap.hashMatrixNodeCache.remove(combinedNode.bitMapNode.position)
        }
        return@writeLock returnValue
    }

    /**
     * Remove an object from the map
     *
     * @param key Used to uniquely identify a record
     * @return Object that was removed.  Null otherwise
     * @since 1.2.0
     */
    override fun remove(key: K): V? = this.mapReadWriteLock.writeLock {
        val combinedNode = getHeadReferenceForKey(key, true)
        head = combinedNode?.head
        val oldHead = combinedNode?.head

        return@writeLock if (oldHead != null) {

            val returnValue = super@DiskMatrixHashMap.remove(key)

            val newHead = head

            // Only update the data if the head of the skip list has changed
            if (oldHead != newHead) {
                combinedNode.head = newHead!!
                this@DiskMatrixHashMap.updateHashMatrixReference(combinedNode.bitMapNode, combinedNode.hashDigit, newHead.position)
                this@DiskMatrixHashMap.hashMatrixNodeCache.remove(combinedNode.bitMapNode.position)
            }
            returnValue
        } else null
    }

    /**
     * Simple map method to see if an object exists within the map by its key.
     *
     * @param key Identifier
     * @return Whether the object exists
     * @since 1.2.0
     */
    override fun containsKey(key: K): Boolean = mapReadWriteLock.readLock {
        val combinedNode = getHeadReferenceForKey(key, true)
        head = combinedNode?.head
        return@readLock combinedNode?.head != null && super.containsKey(key)
    }

    /**
     * Detect if a value is contained in the map.  This will have to do a full scan.  It is not efficient!!!
     * The data is stored un ordered.
     *
     * @param value Value you are looking for
     * @return Whether the value was found
     * @since 1.2.0
     */
    override fun containsValue(value: V): Boolean = this.maps.firstOrNull {
        head = it
        super.containsValue(value)
    } != null

    /**
     * Put all the objects from one map into this map.
     *
     * @param from Map to convert from
     * @since 1.2.0
     */
    override fun putAll(from: Map<out K, V>) = from.forEach { this.put(it.key, it.value) }

    /**
     * Clear this map.  In order to do that.  All we have to do is remove the first data reference and it will
     * orphan the entire data structure
     *
     * @since 1.2.0
     */
    override fun clear() = this.mapReadWriteLock.writeLock {
        super@DiskMatrixHashMap.clear()
    }

    /**
     * Get the record id of a corresponding data.  Note, this points to the SkipListNode position.  Not the actual
     * record position.
     *
     * @param key Identifier
     * @return The position of the record reference if it exists.  Otherwise -1
     * @since 1.2.0
     */
    override fun getRecID(key: K): Long = mapReadWriteLock.readLock {
        val combinedNode = getHeadReferenceForKey(key, false) ?: return@readLock -1
        head = combinedNode.head
        return@readLock super.getRecID(key)
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
    private fun getHeadReferenceForKey(key: K, forInsert: Boolean): CombinedIndexHashMatrixNode? {
        val hash = Math.abs(hash(key))
        val hashDigits = getHashDigits(hash)
        val skipListMapId = getSkipListKey(key)
        return try {
            skipListMapCache.getOrPut(skipListMapId) { return@getOrPut seek(forInsert, hashDigits)!! }
        } catch (e: KotlinNullPointerException) {
            null
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
    private fun getSkipListKey(key: K): Int {
        val hash = hash(key)
        val hashDigits = getHashDigits(hash)

        var k = 0
        for(i in 0 until hashDigits.size)
            k = 10 * k + hashDigits[i]

        return k
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
    private fun seek(forInsert: Boolean, hashDigits: IntArray): CombinedIndexHashMatrixNode? {

        var node: HashMatrixNode?

        if (this.reference.firstNode > 0) {
            node = this.getHashMatrixNode(this.reference.firstNode) // Get Root data
        } else {
            // No default data, lets create one // It must mean we are inserting
            node = HashMatrixNode()
            node.position = fileStore.allocate(this.hashMatrixNodeSize)
            this.writeHashMatrixNode(node.position, node)
            this.forceUpdateHeaderFirstNode(reference, node.position)
        }

        var previousNode: HashMatrixNode = node
        var nodePosition: Long
        var hashDigit: Int

        // Not we are using this load factor rather than what is on the Bitmap disk map
        // Break down the nodes and iterate through them.  We should be left with the remaining data which should point us to the record
        for (level in 0 until loadFactor) {

            hashDigit = hashDigits[level]
            nodePosition = previousNode.next[hashDigit]

            if (nodePosition == 0L && forInsert) {
                if (level == loadFactor - 1) {
                    val headNode = SkipNode.create(fileStore)
                    this.updateHashMatrixReference(previousNode, hashDigit, headNode.position)
                    return CombinedIndexHashMatrixNode(headNode, previousNode, hashDigit)
                } else {
                    node = HashMatrixNode()
                    node.position = fileStore.allocate(this.hashMatrixNodeSize)
                    this.writeHashMatrixNode(node.position, node)
                    this.updateHashMatrixReference(previousNode, hashDigit, node.position)
                }

                previousNode = node
            } else if (nodePosition == 0L)
                return null
            else if (level < loadFactor - 1)
                previousNode = this.getHashMatrixNode(nodePosition)
            else {
                return CombinedIndexHashMatrixNode(findNodeAtPosition(nodePosition)!!, previousNode, hashDigit)
            }// Not found because it is not in the
        }

        return null // This should contain the drones you are looking for (Star Wars reference) // This contains the key to the linked list
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
    override fun above(index: K, includeFirst: Boolean): Set<Long> {
        val returnValue = HashSet<Long>()
        maps.forEach {
            head = it
            returnValue += super.above(index, includeFirst)
        }
        return returnValue
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
    override fun below(index: K, includeFirst: Boolean): Set<Long> {
        val returnValue = HashSet<Long>()
        maps.forEach {
            head = it
            returnValue += super.below(index, includeFirst)
        }
        return returnValue
    }
}
