package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.node.SkipListHeadNode;
import com.onyx.structure.node.SkipListNode;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.store.Store;

import java.io.IOException;
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
abstract class AbstractSkipList<K, V> extends AbstractDiskMap<K,V> implements Map<K, V> {

    private static Random random = new Random(60); //To choose the threadLocalHead level node randomly; // Random number generator from 0.0 to 1.0

    // Head.  If the map is detached i.e. does not point to a specific head, a thread local list of heads are provided
    private ThreadLocal<SkipListHeadNode> threadLocalHead; // Default threadLocalHead of the SkipList
    private SkipListHeadNode headNode;

    // Caching maps
    Map<Long, SkipListHeadNode> nodeCache = new ConcurrentWeakHashMap();
    Map<K, V> valueCache = new ConcurrentWeakHashMap();
    Map<K, SkipListNode> keyCache = new ConcurrentWeakHashMap();
    Map<Long, V> valueByPositionCache = new ConcurrentWeakHashMap();

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

        if(detached)
        {
            threadLocalHead = new ThreadLocal();
        }
        else
        {
            if (header.firstNode > 0L) {
                setHead(findNodeAtPosition(header.firstNode));
            } else {
                SkipListHeadNode newHead = createHeadNode(Byte.MIN_VALUE, 0L, 0L);
                setHead(newHead);
                this.header.firstNode = newHead.position;
                updateHeaderFirstNode(this.header, this.header.firstNode);
            }
        }
    }

    /**
     * If the map is detached it means there could be any number of threads using it as a different map.  For that
     * reason there was a thread-local pool of heads.
     *
     * @since 1.2.0
     *
     * @return The head node.
     */
    SkipListHeadNode getHead()
    {
        if(detached)
        {
            return threadLocalHead.get();
        }
        return headNode;
    }

    /**
     * Set the head node.  If the map is detached, it will set the node as a thread local parameter since it is throw away.
     *
     * @param head Head of the skip list
     */
    void setHead(SkipListHeadNode head)
    {
        if(detached)
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
        if (level > head.level) {
            head = createHeadNode(level, 0L, head.position);
            setHead(head);
            updateHeaderFirstNode(header, head.position);
        }

        SkipListHeadNode current = head;
        SkipListHeadNode last = null;
        SkipListHeadNode next;

        while (current != null) {

            next = findNodeAtPosition(current.next);

            if (current.next == 0L ||
                    shouldMoveDown(hash, hash(((SkipListNode<K>)next).key), key, ((SkipListNode<K>)next).key)) {
                if (level >= current.level) {
                    SkipListNode<K> newNode = createNewNode(key, value, current.level, next == null ? 0L : next.position, 0L);
                    if (last != null) {
                        updateNodeDown(last, newNode.position);
                    }

                    updateNodeNext(current, newNode.position);
                    last = newNode;
                }
                current = findNodeAtPosition(current.down);
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
    @Override
    public V remove(Object key) {

        V value = null;

        // Whether we found the corresponding reference or not.
        boolean victory = false;

        int hash = hash(key);
        SkipListHeadNode current = getHead();
        while (current != null) {

            SkipListHeadNode next = findNodeAtPosition(current.next);

            if (current.next == 0L ||
                    (shouldMoveDown(hash, hash(((SkipListNode<K>)next).key), (K)key, ((SkipListNode<K>)next).key))) {

                // We found the record we want
                if (next != null && key.equals(((SkipListNode<K>)next).key)) {
                    // Get the return value
                    value = findValueAtPosition(((SkipListNode<K>)next).recordPosition, ((SkipListNode<K>)next).recordSize);
                    updateNodeNext(current, next.next);
                    removeNode(next);

                    victory = true;
                    if(value == null)
                        value = (V)Boolean.TRUE;
                }

                // We must continue on.  There could be multiple references within the SkipList
                current = findNodeAtPosition(current.down);
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
     * Perform cleanup once the node has been removed
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
        return (node != null) ? findValueAtPosition(node.recordPosition, node.recordSize) : null;

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
     * Update the object if it already exists.  The purpose of this method is because the Skip List must start
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

            SkipListHeadNode next = findNodeAtPosition(current.next);

            if ((current.next == 0L) ||
                    (shouldMoveDown(hash, hash(((SkipListNode<K>) next).key), key, ((SkipListNode<K>) next).key))) {

                // We found the record we want
                if (next != null && key.equals(((SkipListNode<K>)next).key)) {
                    // Get the return value
                    updateNodeValue((SkipListNode)next, value);
                    victory = true;
                }

                // We must continue on.  There could be multiple references within the SkipList
                current = findNodeAtPosition(current.down);
                continue;
            }

            current = next;
        }

        return victory;
    }

    /**
     * Find the node associated to the key.  This must have an exact match.
     *
     * @param key The Key identifier
     * @return Its corresponding node
     * @since 1.2.0
     */
    protected SkipListNode<K> find(K key) {
        if (key == null)
            return null;

        int hash = hash(key);

        SkipListHeadNode current = getHead();

        while (current != null) {
            SkipListHeadNode next = findNodeAtPosition(current.next);
            if (next != null && key.equals(((SkipListNode<K>)next).key)) {
                return (SkipListNode<K>)next;
            }

            // Next node does not have values so we must move on down and continue the loop.
            else if (current.next == 0L
                    || (next != null && shouldMoveDown(hash, hash(((SkipListNode<K>)next).key), key, ((SkipListNode<K>)next).key))) {
                current = findNodeAtPosition(current.down);
                continue;
            }

            current = next;
        }


        // Boo it wasn't found.
        return null;
    }

    /**
     * The purpose of this method is to either utilize comparable so that the data set can be ordered.  If not,
     * it is based on the hash code of the keys
     *
     * @param hash  Hash Code of key 1
     * @param hash2 Hash code of key 2
     * @param key   The actual key of object 1
     * @param key2  The actual key of object 2
     * @return If the keys are comparable return the result of that.  Othwerwise return the comparison of hash codes
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    private boolean shouldMoveDown(int hash, int hash2, K key, K key2) {
        if (key.getClass() == key2.getClass()
                && Comparable.class.isAssignableFrom(key.getClass())
                && Comparable.class.isAssignableFrom(key2.getClass())) {
            return ((Comparable) key).compareTo(key2) >= 0;
        }
        return hash >= hash2;
    }

    /**
     * Select an arbitrary head of the data structure to start inserting the node.  This is based on a continuous coin
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
    Map getRecordValueAsDictionary(SkipListNode<K> node) {
        ObjectBuffer buffer = fileStore.read(node.recordPosition, node.recordSize);
        return buffer.toMap(node.serializerId);
    }

    /**
     * Find the value at a position.
     *
     * @param position   The position within the file structure to pull it from
     * @param recordSize How many bytes we must read to get the object
     * @return The value as long as it serialized ok.
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    protected V findValueAtPosition(long position, int recordSize) {
        if(position == 0L)
            return null;

        final ObjectBuffer buffer = fileStore.read(position, recordSize);
        try {
            return (V) buffer.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Update the node's value.  The node acts as a record reference.  It must be changed
     * if the value is re-defined.  This is in the event of an update.  This is optimized only to write part of the
     * node.  Only re-write the record size and position after it writes the record.
     *
     * @param node  Record reference
     * @param value The value of the reference
     * @since 1.2.0
     */
    protected void updateNodeValue(SkipListNode<K> node, V value) {
        final ObjectBuffer buffer = new ObjectBuffer(fileStore.getSerializers());
        try {
            // Write the record value to the buffer and allocate the space in the store
            int sizeOfRecord = buffer.writeObject(value);
            long recordPosition = fileStore.allocate(sizeOfRecord);

            fileStore.write(buffer, recordPosition);

            // Set the node values and lets write only the updated values to the store.  No need to write the key and
            // all the other junk
            node.recordSize = sizeOfRecord;
            node.recordPosition = recordPosition;

            final ObjectBuffer nodeBuffer = new ObjectBuffer(fileStore.getSerializers());
            nodeBuffer.writeLong(node.recordPosition);
            nodeBuffer.writeInt(node.recordSize);

            // Write the node values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // node so we want to skip over that
            fileStore.write(nodeBuffer, node.position + Integer.BYTES);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the down reference of a node.  This is done during insertion.  This will also take into account if the SkipListNode
     * is a head node or a record node.
     *
     * @param node Node to update
     * @param position position to set the node.down to
     *
     * @since 1.2.0
     */
    private void updateNodeDown(SkipListHeadNode node, long position) {
        final ObjectBuffer buffer = new ObjectBuffer(ObjectBuffer.allocate(Long.BYTES), fileStore.getSerializers());
        try {

            node.down = position;
            buffer.writeLong(node.down);

            int offset = Integer.BYTES + Long.BYTES;
            if(node instanceof SkipListNode)
                offset = Integer.BYTES + Long.BYTES + Integer.BYTES + Long.BYTES;

            // Write the node values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // node so we want to skip over that
            fileStore.write(buffer, node.position + offset);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the next reference of a node.  This is done during insertion.  This will also take into account if the SkipListNode
     * is a head node or a record node.
     *
     * @param node Node to update
     * @param position position to set the node.down to
     *
     * @since 1.2.0
     */
    private void updateNodeNext(SkipListHeadNode node, long position) {
        final ObjectBuffer buffer = new ObjectBuffer(ObjectBuffer.allocate(Long.BYTES), fileStore.getSerializers());
        try {

            node.next = position;
            buffer.writeLong(node.next);

            int offset = Integer.BYTES;
            if(node instanceof SkipListNode)
                offset = Integer.BYTES + Long.BYTES + Integer.BYTES;

            // Write the node values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // node so we want to skip over that
            fileStore.write(buffer, node.position + offset);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Instantiate and create a new node.  This will insert it into the file store.  This will also configure the
     * node and set all of the necessary information it needs to refer other parts of this class
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
    @SuppressWarnings("unchecked")
    protected SkipListNode<K> createNewNode(K key, V value, byte level, long next, long down) {
        final ObjectBuffer buffer = new ObjectBuffer(fileStore.getSerializers());

        SkipListNode<K> newNode = null;

        try {
            // Write the key to the buffer just to see how big it is.  Afterwards just reset it
            int keySize = buffer.writeObject(key);
            buffer.reset();

            // Write the record to the buffer first and keep track of how big it is
            int recordSize = 0;
            if (value != null) {
                recordSize = buffer.writeObject(value);
            }

            int sizeOfNode = keySize + SkipListNode.BASE_SKIP_LIST_NODE_SIZE;

            // Allocate the space on the file.  Size of the node, record size, and size indicator as Integer.BYTES
            long recordPosition = fileStore.allocate(sizeOfNode + recordSize + Integer.BYTES);

            // Take note of the position of the node so we can indicate that within the node
            long position = recordPosition + recordSize;

            int serializerId = buffer.getSerializerId(value); // Get the serializer id.  Note: This only applies to Managed Entities in order to version

            // Instantiate the new node and write it to the buffer
            newNode = new SkipListNode(key, position, (value == null) ? 0L : recordPosition, level, next, down, recordSize, serializerId);

            // Jot down the size of the node so that we know how much data to pull
            buffer.writeInt(sizeOfNode);
            newNode.writeObject(buffer);

            // Write the node and record if it exists to the store
            fileStore.write(buffer, recordPosition);


        } catch (IOException e) {
            // TODO: Handle exceptions
            e.printStackTrace();
        }

        return newNode;
    }

    /**
     * Instantiate and create a new node.  This will insert it into the file store.  This will also configure the
     * node and set all of the necessary information it needs to refer other parts of this class
     * to the data elements it needs.
     *
     * @param level What level it exists within the skip list
     * @param next  The next value in the skip list
     * @param down  Reference to the next level
     * @return The newly created Skip List Node
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    protected SkipListHeadNode createHeadNode(byte level, long next, long down) {
        SkipListHeadNode newNode = null;

        try {

            int sizeOfNode = SkipListNode.HEAD_SKIP_LIST_NODE_SIZE;

            // Allocate the space on the file.  Size of the node, record size, and size indicator as Integer.BYTES
            long position = fileStore.allocate(sizeOfNode + Integer.BYTES);

            final ObjectBuffer buffer = new ObjectBuffer(ObjectBuffer.allocate(sizeOfNode + Integer.BYTES),fileStore.getSerializers());

            // Instantiate the new node and write it to the buffer
            newNode = new SkipListHeadNode(level, next, down);
            newNode.position = position;

            // Jot down the size of the node so that we know how much data to pull
            buffer.writeInt(sizeOfNode);
            newNode.writeObject(buffer);

            // Write the node and record if it exists to the store
            fileStore.write(buffer, position);


        } catch (IOException e) {
            // TODO: Handle exceptions
            e.printStackTrace();
        }

        return newNode;
    }

    /**
     * Pull a node from the store.  Since we do not know the size of the node, we must first look that up.
     * This will also return null if the node position is 0L.
     *
     * @param position Last known location of the node
     * @return The Hydrated Skip List Node from the file store
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    protected SkipListHeadNode findNodeAtPosition(long position) {
        if (position == 0L)
            return null;

        // First get the size of the node since it may be variable due to the size of the key
        final ObjectBuffer buffer = fileStore.read(position, Integer.SIZE);
        int sizeOfNode = buffer.readInt();

        SkipListHeadNode node;

        // Read the node
        if(sizeOfNode == SkipListNode.HEAD_SKIP_LIST_NODE_SIZE)
            node = (SkipListHeadNode)fileStore.read(position + Integer.BYTES, sizeOfNode, new SkipListHeadNode());
        else
            node = (SkipListHeadNode)fileStore.read(position + Integer.BYTES, sizeOfNode, new SkipListNode());
        node.position = position;
        return node;
    }

    /**
     * Return the quantity of the elements
     * <p>
     * Note: Please use longSize() instead
     *
     * @return The size in an int
     */
    @Deprecated
    @Override
    public int size() {
        return (int) longSize();
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    void updateHeaderRecordCount() {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(header.recordCount.get());
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, header.position + Long.BYTES);
    }

    /**
     * Only update the first position for a header
     *
     * @param header    Data structure header
     * @param firstNode First Node location
     */
    protected void updateHeaderFirstNode(Header header, long firstNode) {

        if(!detached) {
            this.header.firstNode = this.getHead().position;
            final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
            buffer.putLong(firstNode);
            final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
            fileStore.write(objectBuffer, header.position);
        }
    }

}