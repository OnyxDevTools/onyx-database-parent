package com.onyx.diskmap.base.skiplist;

import com.onyx.buffer.BufferPool;
import com.onyx.buffer.BufferStream;
import com.onyx.diskmap.impl.base.AbstractDiskMap;
import com.onyx.diskmap.data.Header;
import com.onyx.diskmap.data.SkipListHeadNode;
import com.onyx.diskmap.data.SkipListNode;
import com.onyx.diskmap.store.Store;
import com.onyx.depricated.CompareUtil;
import com.onyx.exception.BufferingException;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.WriteSynchronizedMap;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;

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
@SuppressWarnings("unchecked")
public abstract class AbstractSkipList<K, V> extends AbstractDiskMap<K,V> implements Map<K,V> {

    private static final Random random = new Random(60); //To choose the threadLocalHead level data randomly; // Random number generator from 0.0 to 1.0

    // Head.  If the map is detached i.e. does not point to a specific head, a thread local list of heads are provided
    private ThreadLocal<SkipListHeadNode> threadLocalHead; // Default threadLocalHead of the SkipList
    private SkipListHeadNode headNode;
    protected Map<Long, SkipListHeadNode> nodeCache = new WriteSynchronizedMap<>(new CompatWeakHashMap<>());

    /**
     * Constructor with file store
     *
     * @param fileStore File storage mechanism
     * @param header    Header location of the skip list
     *
     * @since 1.2.0
     */
    AbstractSkipList(Store fileStore, Header header) {
        this(fileStore, header, false);
    }

    /**
     * Constructor for a detached state
     *
     * @param fileStore File storage mechanism
     * @param header    Header location of the skip list
     * @param headless Whether the header should be ignored or not
     *
     * @since 1.2.0
     */
    AbstractSkipList(Store fileStore, Header header, boolean headless) {

        super(fileStore, header, headless);

        if(getDetached())
        {
            threadLocalHead = new ThreadLocal();
        }
        else
        {
            if (header.getFirstNode() > 0L) {
                setHead(findNodeAtPosition(header.getFirstNode()));
            } else {
                SkipListHeadNode newHead = createHeadNode(Byte.MIN_VALUE, 0L, 0L);
                setHead(newHead);
                this.getReference().setFirstNode(newHead.getPosition());
                updateHeaderFirstNode(this.getReference(), this.getReference().getFirstNode());
            }
        }
    }

    /**
     * If the map is detached it means there could be any number of threads using it as a different map.  For that
     * reason there was a thread-local pool of heads.
     *
     * @since 1.2.0
     *
     * @return The head data.
     */
    protected SkipListHeadNode getHead()
    {
        if(getDetached())
        {
            return threadLocalHead.get();
        }
        return headNode;
    }

    /**
     * Set the head data.  If the map is detached, it will set the data as a thread local parameter since it is throw away.
     *
     * @param head Head of the skip list
     */
    protected void setHead(SkipListHeadNode head)
    {
        if(getDetached())
        {
            if(head == null)
                threadLocalHead.remove();
            threadLocalHead.set(head);
        }
        else
        {
            headNode = head;
        }
    }

    /**
     * Put a key value into the Map.  The underlying algorithm for searching is a Skip List
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return What we just put in
     */
    public V put(K key, V value) {

        // First see if the key already exists.  If it does update it, otherwise, lets continue on trying to insert it.
        // The reason for this is because the rest of the put logic will not start from the root level
        if (updateValue(key, value))
            return value;

        final int hash = hash(key);

        SkipListHeadNode head = getHead();

        final byte level = selectHeadLevel();
        if (level > head.getLevel()) {
            head = createHeadNode(level, 0L, head.getPosition());
            setHead(head);
            updateHeaderFirstNode(getReference(), head.getPosition());
        }

        SkipListHeadNode current = head;
        SkipListHeadNode last = null;
        SkipListHeadNode next;
        SkipListNode recordIndicatorNode = null;

        boolean cache = true;
        while (current != null) {

            next = findNodeAtPosition(current.getNext());

            if (current.getNext() == 0L ||
                    shouldMoveDown(hash, hash(((SkipListNode<K>) next).getKey()), key, ((SkipListNode<K>) next).getKey())) {
                if (level >= current.getLevel()) {
                    SkipListNode<K> newNode = createNewNode(key, value, current.getLevel(), next == null ? 0L : next.getPosition(), 0L, cache, (recordIndicatorNode == null) ? -1 : recordIndicatorNode.getRecordId());
                    if (recordIndicatorNode == null) {
                        recordIndicatorNode = newNode;
                    }
                    // There can be multiple nodes for a single record
                    // We do not want to cache because it will stomp all over our
                    // intial reference
                    cache = false;
                    if (last != null) {
                        updateNodeDown(last, newNode.getPosition());
                    }

                    updateNodeNext(current, newNode.getPosition());
                    last = newNode;
                }
                current = findNodeAtPosition(current.getDown());
                continue;
            }

            current = next;
        }

        // Increment the size.  Since there were no failures we assume it was successfully added.
        incrementSize();

        return value;

    }

    /**
     * Remove The Key and value from the Map.
     *
     * @param key Key Identifier
     * @return The value that was removed.  Null if it does not exist
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    public V remove(Object key) {


        V value = null;

        // Whether we found the corresponding reference or not.
        boolean victory = false;

        int hash = hash(key);
        SkipListHeadNode current = getHead();
        while (current != null) {

            SkipListHeadNode next = findNodeAtPosition(current.getNext());

            if (current.getNext() == 0L ||
                    (shouldMoveDown(hash, hash(((SkipListNode<K>) next).getKey()), (K)key, ((SkipListNode<K>) next).getKey()))) {

                // We found the record we want
                if (next != null && CompareUtil.forceCompare(key, ((SkipListNode<K>) next).getKey())) {
                    // Get the return value
                    value = findValueAtPosition(((SkipListNode<K>) next).getRecordPosition(), ((SkipListNode<K>) next).getRecordSize());
                    updateNodeNext(current, next.getNext());

                    removeNode(next);
                    removeNode(current);

                    victory = true;

                    if(value == null)
                        value = (V)Boolean.TRUE;
                }

                // We must continue on.  There could be multiple references within the SkipList
                current = findNodeAtPosition(current.getDown());
                continue;
            }

            current = next;
        }

        // Victory is ours.  We found you and destroyed the record.  Lets decrement the size
        if (victory) {
            decrementSize();
        }
        else
        {
            return null;
        }

        return value;
    }

    /**
     * Perform cleanup once the data has been removed
     * @param node Node that is to be removed
     */
    abstract void removeNode(SkipListHeadNode node);

    /**
     * Get the value within the map for a given key.
     *
     * @param key Identifier
     * @return Its corresponding value.  Null if the key does not exist
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {

        final SkipListNode<K> node = find((K) key);
        return (node != null) ? findValueAtPosition(node.getRecordPosition(), node.getRecordSize()) : null;

    }

    /**
     * Return whether the Key is already within the Skip List
     *
     * @param key Identifier
     * @return Yeah, I already said it in the description.  True if the key was found.
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean containsKey(Object key) {
        return key != null && find((K) key) != null;
    }

    /**
     * Update the value if it already exists.  The purpose of this method is because the Skip List must start
     * is search from the root head.  That is why the put is insufficient.
     *
     * @param key   Key identifier
     * @param value Value to update to
     * @return Whether the value was updated.  In this case, it must already exist.
     * @since 1.2.0
     */
    private boolean updateValue(K key, V value) {

        // Whether we found the corresponding reference or not.
        boolean victory = false;

        int hash = hash(key);
        SkipListHeadNode current = getHead();
        while (current != null) {

            SkipListHeadNode next = findNodeAtPosition(current.getNext());

            if ((current.getNext() == 0L) || ((next instanceof SkipListNode) &&
                    (shouldMoveDown(hash, hash(((SkipListNode<K>) next).getKey()), key, ((SkipListNode<K>) next).getKey())))) {

                // We found the record we want
                if (next != null && CompareUtil.forceCompare(key, ((SkipListNode<K>) next).getKey())) {
                    // Get the return value

                    // There can be multiple nodes for a single record
                    // We do not want to cache because it will stomp all over our
                    // intial reference.  We use the victory flag to identify
                    // if we have already updated a data

                    updateNodeValue((SkipListNode) next, value, !victory);
                    victory = true;
                }

                // We must continue on.  There could be multiple references within the SkipList
                current = findNodeAtPosition(current.getDown());
                continue;
            }

            current = next;
        }

        return victory;
    }

    /**
     * Find the data associated to the key.  This must have an exact match.
     *
     * @param key The Key identifier
     * @return Its corresponding data
     * @since 1.2.0
     */
    @SuppressWarnings("WeakerAccess")
    protected SkipListNode<K> find(K key) {
        if (key == null)
            return null;

        int hash = hash(key);

        SkipListHeadNode current = getHead();

        while (current != null) {
            SkipListHeadNode next = findNodeAtPosition(current.getNext());
            if (next != null && CompareUtil.forceCompare(key, ((SkipListNode<K>) next).getKey())) {
                return (SkipListNode<K>)next;
            }

            // Next data does not have values so we must move on down and continue the loop.
            else if (current.getNext() == 0L
                    || (next != null && shouldMoveDown(hash, hash(((SkipListNode<K>) next).getKey()), key, ((SkipListNode<K>) next).getKey()))) {
                current = findNodeAtPosition(current.getDown());
                continue;
            }

            current = next;
        }


        // Boo it wasn't found.
        return null;
    }

    /**
     * Find the nearest data associated to the key.  This does not work with hash values.  Only comparable values
     *
     * @param key The Key identifier
     * @return Its closes data
     * @since 1.2.0
     */
    protected SkipListHeadNode nearest(K key) {
        if (key == null)
            return null;

        SkipListHeadNode current = getHead();
        SkipListHeadNode previous = current;

        while (current != null) {
            SkipListHeadNode next = findNodeAtPosition(current.getNext());
            if (next != null && CompareUtil.forceCompare(key, ((SkipListNode<K>) next).getKey())) {
                return next;
            }

            // Next data does not have values so we must move on down and continue the loop.
            else if (current.getNext() == 0L
                    || (next != null && shouldMoveDown(0, 0, key, ((SkipListNode<K>) next).getKey()))) {
                current = findNodeAtPosition(current.getDown());
                continue;
            }

            current = next;

            if(current != null)
                previous = current;
        }


        // Boo it wasn't found.  Well return the closest than
        return previous;
    }

    /**

    /**
     * The purpose of this method is to either utilize comparable so that the data set can be ordered.  If not,
     * it is based on the hash code of the keys
     *
     * @param hash  Hash Code of key 1
     * @param hash2 Hash code of key 2
     * @param key   The actual key of value 1
     * @param key2  The actual key of value 2
     * @return If the keys are comparable return the result of that.  Othwerwise return the comparison of hash codes
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    protected boolean shouldMoveDown(int hash, int hash2, K key, K key2) {
        if (key.getClass() == key2.getClass()
                && Comparable.class.isAssignableFrom(key.getClass())
                && Comparable.class.isAssignableFrom(key2.getClass())) {
            return ((Comparable) key2).compareTo(key) >= 0;
        }
        return hash >= hash2;
    }

    /**
     * Select an arbitrary head of the data structure to start inserting the data.  This is based on a continuous coin
     * toss.  The maximum value is the max of a byte.  It is set as the minimum to offset it so that we can have a
     * maximum level of 256.  This should provide sufficient height.  I think the chances of that is ridiculously rare.
     * Something like .000...75 0s...76.  So, we set the max to a Byte.MAX_VALUE
     *
     * @return Get the height level to insert the record.
     */
    private byte selectHeadLevel() {
        byte level = Byte.MIN_VALUE;
        double COIN_TOSS_MEDIUM = 0.50;
        while (random.nextDouble() < COIN_TOSS_MEDIUM) {
            level++;
            // This has such a small chance of happening but if it ever does, we should return the max so we don't bust our max skip list height
            if (level == Byte.MAX_VALUE)
                return level;
        }

        return level;
    }


    /**
     * This method is intended to get a record key as a dictionary.  Note: This is only intended for ManagedEntities
     *
     * @param node Record reference to pull
     * @return Map of key key pairs
     *
     * @since 1.2.0
     */
    protected Map getRecordValueAsDictionary(SkipListNode<K> node) {
        BufferStream buffer = getFileStore().read(node.getRecordPosition(), node.getRecordSize());
        try {
            return buffer.toMap(getFileStore().getContext());
        } finally {
            buffer.recycle();
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
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    protected V findValueAtPosition(long position, int recordSize) {
        if(position == 0L)
            return null;

        final BufferStream buffer = getFileStore().read(position, recordSize);

        try {
            return (V) buffer.getObject(getFileStore().getContext());
        } catch (BufferingException e) {
            e.printStackTrace();
        } finally {
            buffer.recycle();
        }

        return null;
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
    @SuppressWarnings("WeakerAccess")
    protected void updateNodeValue(SkipListNode<K> node, V value, boolean cache) {
        final BufferStream stream = new BufferStream();

        try {
            // Write the record value to the buffer and allocate the space in the store
            int sizeOfRecord = stream.putObject(value, getFileStore().getContext());
            long recordPosition = getFileStore().allocate(sizeOfRecord);

            stream.flip();
            getFileStore().write(stream.getByteBuffer(), recordPosition);

            // Set the data values and lets write only the updated values to the store.  No need to write the key and
            // all the other junk
            node.setRecordSize(sizeOfRecord);
            node.setRecordPosition(recordPosition);

            stream.clear();
            stream.putLong(node.getRecordPosition());
            stream.putInt(node.getRecordSize());

            stream.flip();

            // Write the data values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // data so we want to skip over that
            getFileStore().write(stream.getByteBuffer(), node.getPosition() + Integer.BYTES);
        } catch (BufferingException e) {
            e.printStackTrace();
        } finally {
            stream.recycle();
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
    private void updateNodeDown(SkipListHeadNode node, long position) {
        final ByteBuffer buffer = BufferPool.INSTANCE.allocateAndLimit(Long.BYTES);
        try {

            node.setDown(position);
            buffer.putLong(node.getDown());

            int offset = Integer.BYTES + Long.BYTES;
            if(node instanceof SkipListNode)
                offset = Integer.BYTES + Long.BYTES + Integer.BYTES + Long.BYTES;

            buffer.flip();

            // Write the data values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // data so we want to skip over that
            getFileStore().write(buffer, node.getPosition() + offset);

        } finally {
            BufferPool.INSTANCE.recycle(buffer);
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
    @SuppressWarnings("WeakerAccess")
    protected void updateNodeNext(SkipListHeadNode node, long position) {
        final ByteBuffer buffer = BufferPool.INSTANCE.allocateAndLimit(Long.BYTES);
        try {

            node.setNext(position);
            buffer.putLong(node.getNext());

            int offset = Integer.BYTES;
            if(node instanceof SkipListNode)
                offset = Integer.BYTES + Long.BYTES + Integer.BYTES;

            buffer.flip();

            // Write the data values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // data so we want to skip over that
            getFileStore().write(buffer, node.getPosition() + offset);

        } finally {
            BufferPool.INSTANCE.recycle(buffer);
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
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    protected SkipListNode<K> createNewNode(K key, V value, byte level, long next, long down, boolean cache, long recordId) {
        BufferStream stream = new BufferStream();

        SkipListNode<K> newNode = null;

        try {
            // Write the key to the buffer just to see how big it is.  Afterwards just reset it
            int keySize = stream.putObject(key);
            stream.clear();

            // Write the record to the buffer first and keep track of how big it is
            int recordSize = 0;
            if (value != null) {
                recordSize = stream.putObject(value, getFileStore().getContext());
            }

            stream.clearReferences();

            int sizeOfNode = keySize + SkipListNode.Companion.getBASE_SKIP_LIST_NODE_SIZE();

            // Allocate the space on the file.  Size of the data, record size, and size indicator as Integer.BYTES
            long recordPosition = getFileStore().allocate(sizeOfNode + recordSize + Integer.BYTES);

            // Take note of the position of the data so we can indicate that within the data
            long position = recordPosition + recordSize;

            if (recordId < 0) {
                recordId = position;
            }
            // Instantiate the new data and write it to the buffer
            newNode = new SkipListNode(key, position, (value == null) ? 0L : recordPosition, level, next, down, recordSize, recordId);

            // Jot down the size of the data so that we know how much data to pull
            stream.putInt(sizeOfNode);
            newNode.write(stream);

            // Write the data and record if it exists to the store
            stream.flip();
            getFileStore().write(stream.getByteBuffer(), recordPosition);

        } catch (BufferingException e) {
            // TODO: Handle exceptions
            e.printStackTrace();
        } finally {
            stream.recycle();
        }

        return newNode;
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
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    protected SkipListHeadNode createHeadNode(byte level, long next, long down) {
        SkipListHeadNode newNode = null;

        int sizeOfNode = SkipListNode.Companion.getBASE_SKIP_LIST_NODE_SIZE();
        BufferStream stream = new BufferStream(sizeOfNode + Integer.BYTES);

        try {

            // Allocate the space on the file.  Size of the data, record size, and size indicator as Integer.BYTES
            long position = getFileStore().allocate(sizeOfNode + Integer.BYTES);

            // Instantiate the new data and write it to the buffer
            newNode = new SkipListHeadNode(level, next, down);
            newNode.setPosition(position);

            // Jot down the size of the data so that we know how much data to pull
            stream.putInt(sizeOfNode);
            newNode.write(stream);

            stream.flip();

            // Write the data and record if it exists to the store
            getFileStore().write(stream.getByteBuffer(), position);

        } catch (BufferingException e) {
            // TODO: Handle exceptions
            e.printStackTrace();
        } finally {
            stream.recycle();
        }

        return newNode;
    }

    /**
     * Pull a data from the store.  Since we do not know the size of the data, we must first look that up.
     * This will also return null if the data position is 0L.
     *
     * @param position Last known location of the data
     * @return The Hydrated Skip List Node from the file store
     * @since 1.2.0
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    protected SkipListHeadNode findNodeAtPosition(long position) {
        if (position == 0L)
            return null;

        // First get the size of the data since it may be variable due to the size of the key
        final BufferStream buffer = getFileStore().read(position, Integer.SIZE);
        int sizeOfNode = 0;
        try {
            sizeOfNode = buffer.getInt();
        } catch (BufferingException e) {}
        finally {
            buffer.recycle();
        }

        SkipListHeadNode node;

        // Read the data
        if(sizeOfNode == SkipListNode.Companion.getBASE_SKIP_LIST_NODE_SIZE())
            node = (SkipListHeadNode) getFileStore().read(position + Integer.BYTES, sizeOfNode, new SkipListHeadNode());
        else
            node = (SkipListHeadNode) getFileStore().read(position + Integer.BYTES, sizeOfNode, new SkipListNode());
        node.setPosition(position);
        return node;
    }

    /**
     * Return the quantity of the elements
     * <p>
     * Note: Please use longSize() instead
     *
     * @return The size in an int
     */
    /*@Deprecated
    @Override
    public int size() {
        return (int) longSize();
    }*/

    /**
     * This method will only update the record count rather than the entire header
     */
    protected void updateHeaderRecordCount() {
        final ByteBuffer buffer = BufferPool.INSTANCE.allocateAndLimit(Long.BYTES);
        try {
            buffer.putLong(getReference().getRecordCount().get());
            buffer.flip();
            getFileStore().write(buffer, getReference().getPosition() + Long.BYTES);
        } finally {
            BufferPool.INSTANCE.recycle(buffer);
        }
    }

    /**
     * Only update the first position for a header
     *
     * @param header    Data structure header
     * @param firstNode First Node location
     */
    protected void updateHeaderFirstNode(Header header, long firstNode) {
        if(!getDetached()) {
            forceUpdateHeaderFirstNode(header, firstNode);
        }
    }

    /**
     * This method is designed to bypass the detached check.  It is for use in disk maps that are detached and override
     * the logic of calculating the data position.
     *
     * @param header Disk Map Header
     * @param firstNode First data location within store
     */
    protected void forceUpdateHeaderFirstNode(Header header, long firstNode) {
        this.getReference().setFirstNode(firstNode);
        final ByteBuffer buffer = BufferPool.INSTANCE.allocateAndLimit(Long.BYTES);
        try {
            buffer.putLong(firstNode);
            buffer.flip();
            getFileStore().write(buffer, header.getPosition());
        } finally {
            BufferPool.INSTANCE.recycle(buffer);
        }
    }
}