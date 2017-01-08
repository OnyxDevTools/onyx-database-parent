package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.node.SkipListNode;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.store.Store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
abstract class AbstractSkipList<K, V> implements Map<K, V> {

    private Random random; // Random number generator from 0.0 to 1.0
    protected Store fileStore; // Underlying storage mechanism
    protected SkipListNode<K> head; // Default head of the SkipList
    protected Header header;

    protected Map<Long, SkipListNode<K>> nodeCache = Collections.synchronizedMap(new WeakHashMap<Long, SkipListNode<K>>());
    protected Map<Long, V> valueCache = Collections.synchronizedMap(new WeakHashMap<Long, V>());
    protected Map<K, SkipListNode<K>> keyCache = Collections.synchronizedMap(new WeakHashMap<K, SkipListNode<K>>());
    protected Map<K, V> keyValueCache = Collections.synchronizedMap(new WeakHashMap<K, V>());

    /**
     * Constructor with file store
     *
     * @param fileStore File storage mechanism
     * @param header    Header location of the skip list
     */
    AbstractSkipList(Store fileStore, Header header) {
        this.fileStore = fileStore;
        this.header = header;

        if (header.firstNode > 0L) {
            this.head = findNodeAtPosition(header.firstNode);
        } else {
            this.head = createNewNode(null, null, Byte.MIN_VALUE, 0L, 0L);
            this.header.firstNode = this.head.position;
            updateHeaderFirstNode(this.header, this.header.firstNode);
        }
        this.random = new Random(40); //To choose the head level node randomly
    }

    /**
     * Put a key value into the Map.  The underlying algorithm for searching is a Skip List
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return What we just put in
     */
    public V put(K key, V value) {

//        readWriteLock.writeLock().lock();

        // First see if the key already exists.  If it does update it, otherwise, lets continue on trying to insert it.
        // The reason for this is because the rest of the put logic will not start from the root level
        if (updateValue(key, value))
            return value;

        try {

            final int hash = hash(key);

            final byte level = selectHeadLevel();
            if (level > head.level) {
                head = createNewNode(null, null, level, 0L, head.position);
                this.header.firstNode = this.head.position;
                updateHeaderFirstNode(header, head.position);
            }

            SkipListNode<K> current = head;
            SkipListNode<K> last = null;
            SkipListNode<K> next;

            while (current != null) {

                next = findNodeAtPosition(current.next);

                if (current.next == 0L || shouldMoveDown(hash, hash(next.key), key, next.key)) {
                    if (next != null && next.key.equals(key)) {
                        updateNodeValue(next, value);
                    }
                    if (level >= current.level) {
                        SkipListNode<K> newNode = createNewNode(key, value, current.level, next == null ? 0L : next.position, 0L);
                        if (last != null) {
                            last.down = newNode.position;
                            updateNodeDown(last);
                        }

                        current.next = newNode.position;
                        updateNodeNext(current);

                        last = newNode;
                    }
                    current = findNodeAtPosition(current.down);
                    continue;
                }

                current = next;
            }

            // Increment the size.  Since there were no failures we assume it was successfully added.
            header.recordCount.incrementAndGet();
            updateHeaderRecordCount();

            return value;
        } finally {
//            readWriteLock.writeLock().unlock();
        }

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

//        readWriteLock.writeLock().lock();

        try {
            V value = null;

            // Whether we found the corresponding reference or not.
            boolean victory = false;

            int hash = hash(key);
            SkipListNode<K> current = head;
            while (current != null) {

                SkipListNode<K> next = findNodeAtPosition(current.next);

                if (current.next == 0L
                        || shouldMoveDown(hash, hash(next.key), (K) key, next.key)) {

                    // We found the record we want
                    if (next != null && key.equals(next.key)) {
                        // Get the return value
                        value = findValueAtPosition(next.recordPosition, next.recordSize);
                        current.next = next.next;
                        updateNodeNext(current);
                        victory = true;
                    }

                    // We must continue on.  There could be multiple references within the SkipList
                    current = findNodeAtPosition(current.down);
                    continue;
                }

                current = next;
            }

            // Victory is ours.  We found you and destroyed the record.  Lets decrement the size
            if (victory) {
                header.recordCount.decrementAndGet();
                updateHeaderRecordCount();

            }

            return value;
        } finally {
//            readWriteLock.writeLock().unlock();
        }
    }

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

//        readWriteLock.readLock().lock();
        try {
            final SkipListNode<K> node = find((K) key);
            return (node != null) ? findValueAtPosition(node.recordPosition, node.recordSize) : null;
        } finally

        {
//            readWriteLock.readLock().unlock();
        }

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
        if (key == null)
            return false;
        return find((K) key) != null;
    }

    /**
     * Update the object if it already exists.  The purpose of this method is because the Skip List must start
     * is search from the root head.  That is why the put is insufficient.
     *
     * @param key Key identifier
     * @param value Value to update to
     * @return Whether the value was updated.  In this case, it must already exist.
     * @since 1.2.0
     */
    public boolean updateValue(K key, V value) {

        final SkipListNode<K> node = find(key);
        if (node != null) {
            updateNodeValue(node, value);
        }

        return (node != null);
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

//        readWriteLock.readLock().lock();
        try {

            int hash = hash(key);

            SkipListNode<K> current = head;

            while (current != null) {

                // Get the next node in order to compare keys
                SkipListNode<K> next = findNodeAtPosition(current.next);
                if (next != null && key.equals(next.key)) {
                    return next;
                }

                // Next node does not have values so we must move on down and continue the loop.
                else if (current.next == 0L
                        || (next != null && shouldMoveDown(hash, hash(next.key), key, next.key))) {
                    current = findNodeAtPosition(current.down);
                    continue;
                }

                current = next;
            }

            // Boo it wasn't found.
            return null;
        }
        finally {
//            readWriteLock.readLock().unlock();
        }
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
        double COIN_TOSS_MEDIUM = 0.5;
        while (level <= header.recordCount.get() - Byte.MIN_VALUE && random.nextDouble() < COIN_TOSS_MEDIUM) {
            level++;
            // This has such a small chance of happening but if it ever does, we should return the max so we don't bust our max skip list height
            if (level == Byte.MAX_VALUE)
                return level;
        }

        return level;
    }

    /**
     * Default hashing algorithm.
     *
     * @param key Key to get the hash of
     * @return The hash value of that key.
     */
    protected int hash(final Object key) {
        if (key == null)
            return 0;
        return key.hashCode();
    }

    /**
     * Whether or not the map is empty.
     *
     * @return True if the size is 0
     * @since 1.2.0
     */
    public boolean isEmpty() {
        return longSize() == 0L;
    }

    /**
     * The size in a long value
     *
     * @return The size of the map as a long
     * @since 1.2.0
     */
    public long longSize() {
        return header.recordCount.get();
    }

    /**
     * This method is intended to get a record key as a dictionary.  Note: This is only intended for ManagedEntities
     *
     * @param node Record reference to pull
     * @return Map of key key pairs
     */
    public Map getRecordValueAsDictionary(SkipListNode<K> node) {
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
        final ObjectBuffer buffer = fileStore.read(position, recordSize);
        try {
            if (buffer == null) {
                fileStore.read(position, recordSize);
            }
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

    protected void updateNodeDown(SkipListNode<K> node) {
        final ObjectBuffer buffer = new ObjectBuffer(fileStore.getSerializers());
        try {

            buffer.writeLong(node.down);

            // Write the node values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // node so we want to skip over that
            fileStore.write(buffer, node.position + Integer.BYTES + Long.BYTES + Integer.BYTES + Long.BYTES);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void updateNodeNext(SkipListNode<K> node) {
        final ObjectBuffer buffer = new ObjectBuffer(fileStore.getSerializers());
        try {

            buffer.writeLong(node.next);

            // Write the node values to the store.  The extra Integer.BYTES is used to indicate the size of the
            // node so we want to skip over that
            fileStore.write(buffer, node.position + Integer.BYTES + Long.BYTES + Integer.BYTES);

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

            // Instantiate the new node and write it to the buffer
            newNode = new SkipListNode(key, position, (value == null) ? 0L : recordPosition, level, next, down, recordSize);

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
     * Pull a node from the store.  Since we do not know the size of the node, we must first look that up.
     * This will also return null if the node position is 0L.
     *
     * @param position Last known location of the node
     * @return The Hydrated Skip List Node from the file store
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    protected SkipListNode<K> findNodeAtPosition(long position) {
        if (position == 0L)
            return null;

        // First get the size of the node since it may be variable due to the size of the key
        final ObjectBuffer buffer = fileStore.read(position, Integer.SIZE);
        int sizeOfNode = buffer.readInt();

        // Read the node
        final SkipListNode<K> node = (SkipListNode<K>) fileStore.read(position + Integer.BYTES, sizeOfNode, SkipListNode.class);
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
    protected void updateHeaderRecordCount() {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(header.recordCount.get());
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, header.position + Header.HEADER_SIZE - Long.BYTES);
    }

    /**
     * Only update the first position for a header
     *
     * @param header    Data structure header
     * @param firstNode First Node location
     */
    public void updateHeaderFirstNode(Header header, long firstNode) {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(firstNode);
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, header.position);
    }

}