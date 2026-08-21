package com.onyx.diskmap.impl.base.btree

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store
import java.lang.ref.WeakReference

/**
 * Compatibility layer retained in the B-tree inheritance hierarchy.
 *
 * Key and value caching now belongs to individual tree pages, so this layer only preserves the
 * existing extension point and defines clearing as replacing the tree with a new empty root.
 */
abstract class AbstractCachedBTree<K, V>(
    fileStore: WeakReference<Store>,
    recordStore: WeakReference<Store>,
    header: Header,
    keyType: Class<*>
) : AbstractBTree<K, V>(fileStore, recordStore, header, keyType) {

    override fun clear() = resetTree()
}
