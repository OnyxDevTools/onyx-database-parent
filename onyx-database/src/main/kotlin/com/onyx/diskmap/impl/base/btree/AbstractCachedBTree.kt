package com.onyx.diskmap.impl.base.btree

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store
import java.lang.ref.WeakReference

/** Retained layer for the public class hierarchy; page-local caches supersede key maps. */
abstract class AbstractCachedBTree<K, V>(
    fileStore: WeakReference<Store>,
    recordStore: WeakReference<Store>,
    header: Header,
    keyType: Class<*>
) : AbstractBTree<K, V>(fileStore, recordStore, header, keyType) {

    override fun clear() = resetTree()
}
