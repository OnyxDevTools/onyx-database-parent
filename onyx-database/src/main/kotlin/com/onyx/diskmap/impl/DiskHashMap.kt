package com.onyx.diskmap.impl

import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.SortedDiskMap
import com.onyx.lang.map.EmptyMap
import com.onyx.diskmap.data.CombinedIndexHashNode
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.impl.base.hashmap.AbstractIterableMultiMapHashMap
import com.onyx.diskmap.store.Store
import com.onyx.lang.concurrent.ClosureReadWriteLock
import com.onyx.lang.concurrent.impl.*
import java.util.HashSet

class DiskHashMap<K, V> : AbstractIterableMultiMapHashMap<K, V>, SortedDiskMap<K, V> {

    private var mapReadWriteLock: ClosureReadWriteLock = DefaultClosureReadWriteLock()

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap
     * @since 1.2.0
     */
    constructor (fileStore: Store, header: Header, loadFactor: Int) : super(fileStore, header, true, loadFactor)

    /**
     * Constructor
     *
     * @param fileStore File storage mechanism
     * @param header    Pointer to the DiskMap
     * @since 1.2.0
     */
    @Suppress("UNUSED")
    constructor (fileStore: Store, header: Header, loadFactor: Int, closureLock: ClosureReadWriteLock) : super(fileStore, header, true, loadFactor) {
        this.nodeCache = EmptyMap()
        this.mapCache = EmptyMap()
        this.cache = EmptyMap()
        this.keyCache = EmptyMap()
        this.mapReadWriteLock = closureLock
    }

    /**
     * Constructor
     *
     * @param fileStore  File storage mechanism
     * @param header     Pointer to the DiskMap
     * @param loadFactor The max size of the hash that is generated
     * @param stateless  If designated as true, this map does not retain state.  The state is handled elsewhere.  Without a state
     * there can not be a meaningful cache nor a meaningful lock.  In that case, in this constructor,
     * we set the cache elements and lock to empty implementations.
     * @since 1.2.0
     */
    constructor(fileStore: Store, header: Header, loadFactor: Int, stateless: Boolean) : super(fileStore, header, true, loadFactor) {
        if (!stateless) {
            cache = EmptyMap()
            mapCache = EmptyMap()
            keyCache = EmptyMap()
            nodeCache = EmptyMap()
            mapReadWriteLock = EmptyClosureReadWriteLock()
        }
    }

    /**
     * Remove an object from the map
     *
     * @param key Used to uniquely identify a record
     * @return Object that was removed.  Null otherwise
     * @since 1.2.0
     */
    override fun remove(key: K): V? = mapReadWriteLock.writeLock {
        val combinedNode = getHeadReferenceForKey(key, true)
        head = combinedNode?.head

        val head = combinedNode?.head

        if (head != null) {
            val headPosition = head.position

            val returnValue = super@DiskHashMap.remove(key)
            combinedNode.head = head
            if (head.position != headPosition) {
                updateSkipListReference(combinedNode.mapId, head.position)
            }
            return@writeLock returnValue
        }
        return@writeLock null
    }

    /**
     * Get the value by its corresponding key.
     *
     * @param key Primary key
     * @return The value if it exists
     * @since 1.2.0
     */
    override operator fun get(key: K): V? {
        val combinedNode = getHeadReferenceForKey(key, false)

        // Set the selected skip list
        head = combinedNode?.head

        return if (combinedNode?.head != null) {
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
    override fun put(key: K, value: V): V = mapReadWriteLock.writeLock {
        val combinedNode = getHeadReferenceForKey(key, true)
        head = combinedNode?.head

        val mapHead = combinedNode?.head
        if (mapHead != null) {
            val headPosition = mapHead.position
            val returnValue = super@DiskHashMap.put(key, value)
            val newHead = head!!
            combinedNode.head = newHead
            if (newHead.position != headPosition)
                updateSkipListReference(combinedNode.mapId, newHead.position)
            return@writeLock returnValue
        }

        return@writeLock value
    }

    /**
     * Simple map method to see if an object exists within the map by its key.
     *
     * @param key Identifier
     * @return Whether the object exists
     * @since 1.2.0
     */
    override fun containsKey(key: K): Boolean = mapReadWriteLock.readLock {
        val combinedNode = getHeadReferenceForKey(key, false) ?: return@readLock false
        head = combinedNode.head
        return@readLock super.containsKey(key)
    }

    /**
     * Detect if a value is contained in the map.  This will have to do a full scan.  It is not efficient!!!
     * The data is stored un ordered.
     *
     * @param value Value you are looking for
     * @return Whether the value was found
     * @since 1.2.0
     */
    override fun containsValue(value: V): Boolean = values.first {
        it == value
    } != null

    /**
     * Get the record id of a corresponding data.  Note, this points to the SkipListNode position.  Not the actual
     * record position.
     *
     * @param key Identifier
     * @return The position of the record reference if it exists.  Otherwise -1
     * @since 1.2.0
     */
    override fun getRecID(key: K): Long {
        val combinedNode = getHeadReferenceForKey(key, false) ?: return -1
        head = combinedNode.head
        return super.getRecID(key)
    }

    /**
     * Put all the objects from one map into this map.
     *
     * @param from Map to convert from
     * @since 1.2.0
     */
    override fun putAll(from: Map<out K, V>) = from.forEach {
        put(it.key, it.value)
    }

    /**
     * Clear this map.  In order to do that.  All we have to do is remove the first data reference and it will
     * orphan the entire data structure
     *
     * @since 1.2.0
     */
    override fun clear() = mapReadWriteLock.writeLock {
        super.clear()
    }

    /**
     * Find all references above and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    override fun above(index: K, includeFirst: Boolean): Set<Long> {
        val returnValue = HashSet<Long>()
        maps.forEach {
            head = it
            returnValue.addAll(super.above(index, includeFirst))
        }
        return returnValue
    }

    /**
     * Find all references below and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    override fun below(index: K, includeFirst: Boolean): Set<Long> {
        val returnValue = HashSet<Long>()
        maps.forEach {
            head = it
            returnValue.addAll(super.below(index, includeFirst))
        }
        return returnValue
    }

    /**
     * Find all references between from and to value.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param fromValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeFrom Whether to compare above and equal or not.
     * @param toValue Key to end range to
     * @param includeTo Whether to compare equal or not.
     *
     * @since 2.1.3
     */
    override fun between(fromValue: K?, includeFrom: Boolean, toValue: K?, includeTo: Boolean): Set<Long> {
        val returnValue = HashSet<Long>()
        maps.forEach {
            head = it
            returnValue.addAll(super.between(fromValue, includeFrom, toValue, includeTo))
        }
        return returnValue
    }

    /**
     * The nuts and bolts of the map lie here.  This finds the head of the skip list based on the key
     * It uses the bitmap index on the disk map.
     *
     * @param key       Unique identifier that is changed to a non unique key in order to generify the skip list
     * @param forInsert Whether we should insert the bitmap index.
     * @return The Combined Index data of the skip list and it contains the bitmap data information.
     * @since 1.2.0
     *
     * @since 1.2.2 There is no use for the caching map so it was removed.  This should be
     * inexpensive since it only requires a single i/o read.  Also, refactored the
     * locking to lock on the head and pass through for the rest of the data structure since it
     * does not impact any sub maps
     */
    private fun getHeadReferenceForKey(key: K, forInsert: Boolean): CombinedIndexHashNode? {
        val skipListMapId = getSkipListKey(key)

        return if (forInsert) {
            val headNode1: SkipNode
                val reference = super@DiskHashMap.getSkipListReference(skipListMapId)
                if (reference == 0L) {
                    headNode1 = SkipNode.create(fileStore)
                    insertSkipListReference(skipListMapId, headNode1.position)
                    CombinedIndexHashNode(headNode1, skipListMapId)
                } else {
                    CombinedIndexHashNode(findNodeAtPosition(reference)!!, skipListMapId)
                }
        } else {
            val reference = super@DiskHashMap.getSkipListReference(skipListMapId)
            if (reference > 0L) CombinedIndexHashNode(findNodeAtPosition(reference)!!, skipListMapId) else null
        }
    }

    /**
     * Get the key of the skip list.  This is based on the hash.  It pairs down the hash based on the load factor.
     * That scaled hash will then become the key for the skip list header.
     *
     * @param key Key object
     * @return integer key based on its hash
     * @since 1.2.0
     */
    private fun getSkipListKey(key: K): Int {
        val hash = hash(key)
        val hashDigits = getHashDigits(hash)

        var k = 0
        for (i in 0 until hashDigits.size)
            k = 10 * k + hashDigits[i]

        return k
    }
}
