package com.onyx.diskmap.base.hashmatrix;

import com.onyx.buffer.BufferStream;
import com.onyx.diskmap.base.DiskSkipListMap;
import com.onyx.diskmap.node.HashMatrixNode;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.store.Store;

import java.nio.ByteBuffer;

import static com.onyx.diskmap.node.HashMatrixNode.DEFAULT_BITMAP_ITERATIONS;

/**
 * This class is responsible for the store i/o that writes and reads the hash matrix nodes.  It inherits all of the
 * disk skip list functionaltiy but, should only be responsible for the hash matrix i/o
 *
 * @param <K> Key
 * @param <V> Value
 *
 * @since 1.2.0 This was migrated from the AbstractBitMap and simplified to be only a part of an index rather than have
 *              record management
 */
abstract class AbstractHashMatrix<K, V> extends DiskSkipListMap<K, V> {

    /**
     * Constructor
     *
     * @param fileStore Storage volume for hash matrix
     * @param header Head of the data structure
     * @param detached Whether the object is detached.  If it is detached, ignore writing
     *                 values to the header
     *
     * @since 1.2.0
     */
    AbstractHashMatrix(Store fileStore, Header header, boolean detached) {
        super(fileStore, header, detached);
    }

    /**
     * Update hash matrix reference.  This will not write the entire node, it will only update one of the sub references.
     *
     * Pre-requisite, the value must be defined within the node.  It will update the cache.
     *
     * @param node Hash Matrix node.
     * @param index Index of the reference within the next[] property for a node.
     * @param value New reference value
     *
     * @since 1.2.0
     */
    @SuppressWarnings("WeakerAccess")
    public void updateHashMatrixReference(HashMatrixNode node, int index, long value) {
        node.next[index] = value;
        final ByteBuffer buffer = BufferStream.allocateAndLimit(Long.BYTES);
        try {
            buffer.putLong(value);
            buffer.flip();
            fileStore.write(buffer, node.position + (Long.BYTES * index) + Long.BYTES + Integer.BYTES);
        } finally {
            BufferStream.recycle(buffer);
        }
    }

    /**
     * Get Hash Matrix Node.  Return the deserialized value from the store
     *
     * @param position Position to find the node
     * @return Node retrieved from the cache or the volume
     *
     * @since 1.2.0
     */
    @SuppressWarnings("WeakerAccess")
    protected HashMatrixNode getHashMatrixNode(long position) {
        return (HashMatrixNode) fileStore.read(position, getHashMatrixNodeSize(), HashMatrixNode.class);
    }

    /**
     * Write the hash matrix node to the store.
     *
     * @param position  Position to write the node to
     * @param node Node to write to disk
     *
     * @since 1.2.0
     */
    @SuppressWarnings("WeakerAccess")
    protected void writeHashMatrixNode(long position, HashMatrixNode node) {
        fileStore.write(node, node.position);
    }

    /**
     * Get the size of a hash matrix node.
     *
     * @since 1.2.0
     * @return Number of levels + position
     */
    protected int getHashMatrixNodeSize() {
        return (Long.BYTES * (DEFAULT_BITMAP_ITERATIONS+1)) + Integer.SIZE;
    }
}
