package com.onyx.diskmap.base.hashmatrix;

import com.onyx.diskmap.base.DiskSkipListMap;
import com.onyx.diskmap.node.HashMatrixNode;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.store.Store;

import java.nio.ByteBuffer;

abstract class AbstractHashMatrix<K, V> extends DiskSkipListMap<K, V> {

    AbstractHashMatrix(Store fileStore, Header header, boolean detached) {
        super(fileStore, header, detached);
    }

    public void updateHashMatrixReference(HashMatrixNode node, int index, long value) {
        node.next[index] = value;
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, node.position + (Long.BYTES * index) + Long.BYTES);
    }

    protected HashMatrixNode getHashMatrixNode(long position) {
        return (HashMatrixNode) fileStore.read(position, getBitmapNodeSize(), HashMatrixNode.class);
    }

    protected void writeHashMatrixNode(long position, HashMatrixNode node) {
        fileStore.write(node, node.position);
    }

    protected int getBitmapNodeSize() {
        return Long.BYTES * (11);
    }
}
