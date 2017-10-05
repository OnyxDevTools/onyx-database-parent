package com.onyx.diskmap.impl.base.skiplist

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipListHeadNode
import com.onyx.diskmap.data.SkipListNode
import com.onyx.diskmap.impl.base.AbstractDiskMap
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.forceCompare
import com.onyx.extension.perform
import com.onyx.extension.withBuffer
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.lang.map.OptimisticLockingMap
import java.util.*

/**
 * Created by tosborn1 on 1/7/17.
 * <p>
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.  This contains the base level skip list logic and the store i/o.
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 * @since 1.2.0
 */
@Suppress("UNCHECKED_CAST", "LeakingThis")
abstract class AbstractSkipList<K, V> @JvmOverloads constructor(override val fileStore: Store, header: Header,  detached:Boolean = false) : AbstractDiskMap<K, V>(fileStore, header, detached) {

    companion object {
        private val random = Random(60) //To choose the threadLocalHead level data randomly; // Random number generator from 0.0 to 1.0
    }

    // Head.  If the map is detached i.e. does not point to a specific head, a thread local list of heads are provided
    private lateinit var threadLocalHead: ThreadLocal<SkipListHeadNode> // Default threadLocalHead of the SkipList
    protected var nodeCache: MutableMap<Long, SkipListHeadNode> = OptimisticLockingMap(WeakHashMap())

    /**
     * If the map is detached it means there could be any number of threads using it as a different map.  For that
     * reason there was a thread-local pool of heads.
     *
     * @since 1.2.0
     *
     * @return The head data.
     */
    protected var head:SkipListHeadNode? = null
        get() {
            return if(detached)
                threadLocalHead.get()
            else
                field
        }
        set(value) {
            if(detached)
                threadLocalHead.set(value)
            else
                field = value
        }

    init {
        if (detached) {
            threadLocalHead = ThreadLocal()
        } else {
            if (header.firstNode > 0L) {
                head = findNodeAtPosition(reference.firstNode)
            } else {
                val newHead = createHeadNode(java.lang.Byte.MIN_VALUE, 0L, 0L)
                head = newHead
                this.reference.firstNode = newHead.position
                updateHeaderFirstNode(this.reference, this.reference.firstNode)
            }
        }
    }

    /**
     * Put a key value into the Map.  The underlying algorithm for searching is a Skip List
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return What we just put in
     */
    override fun put(key: K, value: V): V {

        // First see if the key already exists.  If it does update it, otherwise, lets continue on trying to insert it.
        // The reason for this is because the rest of the put logic will not start from the root level
        if (updateValue(key, value))
            return value
        var head = head

        val level = selectHeadLevel()
        if (level > head!!.level) {
            head = createHeadNode(level, 0L, head.position)
            this.head = head
            updateHeaderFirstNode(reference, head.position)
        }

        var current: SkipListHeadNode? = head
        var last: SkipListHeadNode? = null
        var next: SkipListHeadNode?
        var recordIndicatorNode: SkipListNode<*>? = null

        var cache = true
        while (current != null) {

            next = findNodeAtPosition(current.next)

            if (current.next == 0L || shouldMoveDown(key, (next as SkipListNode<K>).key)) {
                if (level >= current.level) {
                    val newNode = createNewNode(key, value, current.level, if (next == null) 0L else next.position, 0L, cache, if (recordIndicatorNode == null) -1 else recordIndicatorNode.recordId)
                    if (recordIndicatorNode == null) {
                        recordIndicatorNode = newNode
                    }
                    // There can be multiple nodes for a single record
                    // We do not want to cache because it will stomp all over our
                    // initial reference
                    cache = false
                    if (last != null) {
                        updateNodeDown(last, newNode.position)
                    }

                    updateNodeNext(current, newNode.position)
                    last = newNode
                }
                current = findNodeAtPosition(current.down)
                continue
            }

            current = next
        }

        // Increment the size.  Since there were no failures we assume it was successfully added.
        incrementSize()

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
        var value: V? = null

        // Whether we found the corresponding reference or not.
        var victory = false

        var current: SkipListHeadNode? = head
        while (current != null) {

            val next = findNodeAtPosition(current.next)

            if (current.next == 0L || shouldMoveDown(key, (next as SkipListNode<K>).key)) {

                // We found the record we want
                if (next != null && key.forceCompare((next as SkipListNode<K>).key)) {
                    // Get the return value
                    value = findValueAtPosition(next.recordPosition, next.recordSize)
                    updateNodeNext(current, next.next)

                    removeNode(next)
                    removeNode(current)

                    victory = true
                }

                // We must continue on.  There could be multiple references within the SkipList
                current = findNodeAtPosition(current.down)
                continue
            }

            current = next
        }

        // Victory is ours.  We found you and destroyed the record.  Lets decrement the size
        if (victory) {
            decrementSize()
        } else {
            return null
        }

        return value
    }

    /**
     * Perform cleanup once the data has been removed
     * @param node Node that is to be removed
     */
    abstract protected fun removeNode(node: SkipListHeadNode)

    override fun get(key: K): V? {
        val node = find(key)
        return if(node == null) null else findValueAtPosition(node.recordPosition, node.recordSize)
    }

    /**
     * Return whether the Key is already within the Skip List
     *
     * @param key Identifier
     * @return Yeah, I already said it in the description.  True if the key was found.
     * @since 1.2.0
     */
    override fun containsKey(key: K): Boolean = key != null && find(key) != null

    /**
     * Update the value if it already exists.  The purpose of this method is because the Skip List must start
     * is search from the root head.  That is why the put is insufficient.
     *
     * @param key   Key identifier
     * @param value Value to update to
     * @return Whether the value was updated.  In this case, it must already exist.
     * @since 1.2.0
     */
    private fun updateValue(key: K, value: V): Boolean {

        // Whether we found the corresponding reference or not.
        var victory = false

        var current: SkipListHeadNode? = head
        while (current != null) {

            val next = findNodeAtPosition(current.next)

            if (current.next == 0L || next is SkipListNode<*> && shouldMoveDown(key, next.key as K)) {

                // We found the record we want
                if (next != null && key.forceCompare((next as SkipListNode<K>).key)) {
                    // Get the return value

                    // There can be multiple nodes for a single record
                    // We do not want to cache because it will stomp all over our
                    // initial reference.  We use the victory flag to identify
                    // if we have already updated a data

                    updateNodeValue(next, value, !victory)
                    victory = true
                }

                // We must continue on.  There could be multiple references within the SkipList
                current = findNodeAtPosition(current.down)
                continue
            }

            current = next
        }

        return victory
    }

    /**
     * Find the data associated to the key.  This must have an exact match.
     *
     * @param key The Key identifier
     * @return Its corresponding data
     * @since 1.2.0
     */
    protected open fun find(key: K): SkipListNode<K>? {
        var current: SkipListHeadNode? = head

        while (current != null) {
            val next = findNodeAtPosition(current.next)
            if (next != null && key.forceCompare((next as SkipListNode<K>).key)) {
                return next
            } else if (current.next == 0L || next != null && shouldMoveDown(key, (next as SkipListNode<K>).key)) {
                current = findNodeAtPosition(current.down)
                continue
            }// Next data does not have values so we must move on down and continue the loop.

            current = next
        }

        // Boo it wasn't found.
        return null
    }

    /**
     * Find the nearest data associated to the key.  This does not work with hash values.  Only comparable values
     *
     * @param key The Key identifier
     * @return Its closes data
     * @since 1.2.0
     */
    protected fun nearest(key: K?): SkipListHeadNode? {
        if (key == null)
            return null

        var current: SkipListHeadNode? = head
        var previous = current

        while (current != null) {
            val next = findNodeAtPosition(current.next)
            if (next != null && key.forceCompare((next as SkipListNode<K>).key)) {
                return next
            } else if (current.next == 0L || next != null && shouldMoveDown(key, (next as SkipListNode<K>).key)) {
                current = findNodeAtPosition(current.down)
                continue
            }// Next data does not have values so we must move on down and continue the loop.

            current = next

            if (current != null)
                previous = current
        }


        // Boo it wasn't found.  Well return the closest than
        return previous
    }


    /**
     * The purpose of this method is to either utilize comparable so that the data set can be ordered.  If not,
     * it is based on the hash code of the keys
     *
     * @param key   The actual key of value 1
     * @param key2  The actual key of value 2
     * @return If the keys are comparable return the result of that.  Otherwise return the comparison of hash codes
     * @since 1.2.0
     */
    protected fun shouldMoveDown(key: K, key2: K): Boolean = key.forceCompare(key2, QueryCriteriaOperator.GREATER_THAN_EQUAL)

    /**
     * Select an arbitrary head of the data structure to start inserting the data.  This is based on a continuous coin
     * toss.  The maximum value is the max of a byte.  It is set as the minimum to offset it so that we can have a
     * maximum level of 256.  This should provide sufficient height.  I think the chances of that is ridiculously rare.
     * Something like .000...75 0s...76.  So, we set the max to a Byte.MAX_VALUE
     *
     * @return Get the height level to insert the record.
     */
    private fun selectHeadLevel(): Byte {
        var level = java.lang.Byte.MIN_VALUE
        val coinToss = 0.50
        while (random.nextDouble() < coinToss) {
            level++
            // This has such a small chance of happening but if it ever does, we should return the max so we don't bust our max skip list height
            if (level == java.lang.Byte.MAX_VALUE)
                return level
        }

        return level
    }


    /**
     * This method is intended to get a record key as a dictionary.  Note: This is only intended for ManagedEntities
     *
     * @param node Record reference to pull
     * @return Map of key key pairs
     *
     * @since 1.2.0
     */
    protected fun getRecordValueAsDictionary(node: SkipListNode<K>): Map<String, Any?> {
        val buffer = fileStore.read(node.recordPosition, node.recordSize)
        return buffer.perform {
            it!!.toMap(fileStore.context!!)
        }
    }

    /**
     * Find the value at a position.
     *
     * @param position   The position within the file structure to pull it from
     * @param recordSize How many bytes we must read to get the value
     * @return The value as long as it serialized ok.
     * @since 1.2.0
     */
    protected open fun findValueAtPosition(position: Long, recordSize: Int): V? {
        if (position == 0L)
            return null

        val buffer = fileStore.read(position, recordSize)
        return buffer.perform {
            it!!.getObject(fileStore.context) as V
        }
    }

    /**
     * Update the data's value.  The data acts as a record reference.  It must be changed
     * if the value is re-defined.  This is in the event of an update.  This is optimized only to write part of the
     * data.  Only re-write the record size and position after it writes the record.
     *
     * @param node  Record reference
     * @param value The value of the reference
     * @since 1.2.0
     */
    protected open fun updateNodeValue(node: SkipListNode<K>, value: V, cache: Boolean) {
        val stream = BufferStream()

        stream.perform {
            // Write the record value to the buffer and allocate the space in the store
            val sizeOfRecord = stream.putObject(value, fileStore.context)
            val recordPosition = fileStore.allocate(sizeOfRecord)

            stream.flip()
            fileStore.write(stream.byteBuffer, recordPosition)

            // Set the data values and lets write only the updated values to the store.  No need to write the key and
            // all the other junk
            node.recordSize = sizeOfRecord
            node.recordPosition = recordPosition

            stream.clear()
            stream.putLong(node.recordPosition)
            stream.putInt(node.recordSize)

            stream.flip()

            // Write the data values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // data so we want to skip over that
            fileStore.write(stream.byteBuffer, node.position + Integer.BYTES)
        }
    }

    /**
     * Update the down reference of a data.  This is done during insertion.  This will also take into account if the SkipListNode
     * is a head data or a record data.
     *
     * @param node Node to update
     * @param position position to set the data.down to
     *
     * @since 1.2.0
     */
    private fun updateNodeDown(node: SkipListHeadNode, position: Long) {
        val buffer = BufferPool.allocateAndLimit(java.lang.Long.BYTES)
        withBuffer(buffer) {

            node.down = position
            buffer.putLong(node.down)

            var offset = Integer.BYTES + java.lang.Long.BYTES
            if (node is SkipListNode<*>)
                offset = Integer.BYTES + java.lang.Long.BYTES + Integer.BYTES + java.lang.Long.BYTES

            buffer.flip()

            // Write the data values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // data so we want to skip over that
            fileStore.write(buffer, node.position + offset)
        }
    }

    /**
     * Update the next reference of a data.  This is done during insertion.  This will also take into account if the SkipListNode
     * is a head data or a record data.
     *
     * @param node Node to update
     * @param position position to set the data.down to
     *
     * @since 1.2.0
     */
    protected open fun updateNodeNext(node: SkipListHeadNode, position: Long) {
        val buffer = BufferPool.allocateAndLimit(java.lang.Long.BYTES)
        withBuffer(buffer) {
            node.next = position
            buffer.putLong(node.next)

            var offset = Integer.BYTES
            if (node is SkipListNode<*>)
                offset = Integer.BYTES + java.lang.Long.BYTES + Integer.BYTES

            buffer.flip()

            // Write the data values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // data so we want to skip over that
            fileStore.write(buffer, node.position + offset)
        }
    }

    /**
     * Instantiate and create a new data.  This will insert it into the file store.  This will also configure the
     * data and set all of the necessary information it needs to refer other parts of this class
     * to the data elements it needs.
     *
     * @param key   Key Identifier
     * @param value Record value
     * @param level What level it exists within the skip list
     * @param next  The next value in the skip list
     * @param down  Reference to the next level
     * @return The newly created Skip List Node
     * @since 1.2.0
     */
    protected open fun createNewNode(key: K, value: V?, level: Byte, next: Long, down: Long, cache: Boolean, recordId: Long): SkipListNode<K> {
        var valueRecordId = recordId
        var newNode: SkipListNode<K>? = null
        val stream = BufferStream()

        stream.perform {
            // Write the key to the buffer just to see how big it is.  Afterwards just reset it
            val keySize = stream.putObject(key)
            stream.clear()

            // Write the record to the buffer first and keep track of how big it is
            var recordSize = 0
            if (value != null) {
                recordSize = stream.putObject(value, fileStore.context)
            }

            stream.clearReferences() // Make sure we do not track previous references such as the value.  They need to be on different paths so they can be individually hydrated from store

            val sizeOfNode = keySize + SkipListNode.SKIP_LIST_NODE_SIZE

            // Allocate the space on the file.  Size of the data, record size, and size indicator as Integer.BYTES
            val recordPosition = fileStore.allocate(sizeOfNode + recordSize + Integer.BYTES)

            // Take note of the position of the data so we can indicate that within the data
            val position = recordPosition + recordSize

            if (valueRecordId < 0) {
                valueRecordId = position
            }

            // Instantiate the new data and write it to the buffer
            newNode = SkipListNode(key, position, if (value == null) 0L else recordPosition, level, next, down, recordSize, valueRecordId)

            // Jot down the size of the data so that we know how much data to pull
            stream.putInt(sizeOfNode)
            newNode!!.write(stream)

            // Write the data and record if it exists to the store
            stream.flip()
            fileStore.write(stream.byteBuffer, recordPosition)

        }

        return newNode!!
    }

    /**
     * Instantiate and create a new data.  This will insert it into the file store.  This will also configure the
     * data and set all of the necessary information it needs to refer other parts of this class
     * to the data elements it needs.
     *
     * @param level What level it exists within the skip list
     * @param next  The next value in the skip list
     * @param down  Reference to the next level
     * @return The newly created Skip List Node
     * @since 1.2.0
     */
    protected open fun createHeadNode(level: Byte, next: Long, down: Long): SkipListHeadNode {
        var newNode: SkipListHeadNode? = null

        val sizeOfNode = SkipListNode.BASE_SKIP_LIST_NODE_SIZE
        val stream = BufferStream(sizeOfNode + Integer.BYTES)

        stream.perform {

            // Allocate the space on the file.  Size of the data, record size, and size indicator as Integer.BYTES
            val position = fileStore.allocate(sizeOfNode + Integer.BYTES)

            // Instantiate the new data and write it to the buffer
            newNode = SkipListHeadNode(level, next, down)
            newNode!!.position = position

            // Jot down the size of the data so that we know how much data to pull
            it!!.putInt(sizeOfNode)
            newNode!!.write(it) // Write new node to stream

            it.flip()

            // Write the data and record if it exists to the store
            fileStore.write(stream.byteBuffer, position)
        }

        return newNode!!
    }

    /**
     * Pull a data from the store.  Since we do not know the size of the data, we must first look that up.
     * This will also return null if the data position is 0L.
     *
     * @param position Last known location of the data
     * @return The Hydrated Skip List Node from the file store
     * @since 1.2.0
     */
    protected open fun findNodeAtPosition(position: Long): SkipListHeadNode? {
        if (position == 0L)
            return null

        var sizeOfNode = 0

        // First get the size of the data since it may be variable due to the size of the key
        val buffer = fileStore.read(position, Integer.SIZE)!!
        buffer.perform {
            sizeOfNode = buffer.int
        }

        val node = if (sizeOfNode == SkipListNode.BASE_SKIP_LIST_NODE_SIZE)
            fileStore.read(position + Integer.BYTES, sizeOfNode, SkipListHeadNode()) as SkipListHeadNode
        else
            fileStore.read(position + Integer.BYTES, sizeOfNode, SkipListNode<K>()) as SkipListHeadNode
        node.position = position
        return node
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    protected fun updateHeaderRecordCount() {
        val buffer = BufferPool.allocateAndLimit(java.lang.Long.BYTES)
        withBuffer(buffer) {
            buffer.putLong(reference.recordCount.get())
            buffer.flip()
            fileStore.write(buffer, reference.position + java.lang.Long.BYTES)
        }
    }

    /**
     * Only update the first position for a header
     *
     * @param header    Data structure header
     * @param firstNode First Node location
     */
    override fun updateHeaderFirstNode(header: Header, firstNode: Long) {
        if (!detached) {
            forceUpdateHeaderFirstNode(header, firstNode)
        }
    }

    /**
     * This method is designed to bypass the detached check.  It is for use in disk maps that are detached and override
     * the logic of calculating the data position.
     *
     * @param header Disk Map Header
     * @param firstNode First data location within store
     */
    protected fun forceUpdateHeaderFirstNode(header: Header, firstNode: Long) {
        this.reference.firstNode = firstNode
        val buffer = BufferPool.allocateAndLimit(java.lang.Long.BYTES)
        withBuffer(buffer) {
            buffer.putLong(firstNode)
            buffer.flip()
            fileStore.write(buffer, header.position)
        }
    }
}
