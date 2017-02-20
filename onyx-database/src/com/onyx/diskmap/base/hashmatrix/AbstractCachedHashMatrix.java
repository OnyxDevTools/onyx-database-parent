package com.onyx.diskmap.base.hashmatrix;

import com.onyx.diskmap.base.concurrent.ConcurrentWeakHashMap;
import com.onyx.diskmap.node.HashMatrixNode;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.store.Store;

import java.util.Map;

public abstract class AbstractCachedHashMatrix<K, V> extends AbstractHashMatrix<K, V> {
    protected Map<Long, HashMatrixNode> nodeCache;

    public AbstractCachedHashMatrix(final Store fileStore, final Header header) {
        super(fileStore, header, true);
        nodeCache = new ConcurrentWeakHashMap<>();
    }

    public AbstractCachedHashMatrix(Store fileStore, Header header, boolean detached) {
        super(fileStore, header, detached);
        nodeCache = new ConcurrentWeakHashMap<>();
    }

    @Override
    protected HashMatrixNode getHashMatrixNode(final long position) {
        HashMatrixNode node = nodeCache.get(position);

        if (node == null) {
            node = super.getHashMatrixNode(position);
            nodeCache.put(position, node);
        }

        return node;
    }

    @Override
    protected void writeHashMatrixNode(final long position, final HashMatrixNode node) {
        nodeCache.put(position, node);
        super.writeHashMatrixNode(position, node);
    }

    @Override
    public void updateHashMatrixReference(final HashMatrixNode node, final int index, final long value) {
        nodeCache.put(node.position, node);
        super.updateHashMatrixReference(node, index, value);
    }

    @Override
    public void clear()
    {
        this.nodeCache.clear();
    }

}
