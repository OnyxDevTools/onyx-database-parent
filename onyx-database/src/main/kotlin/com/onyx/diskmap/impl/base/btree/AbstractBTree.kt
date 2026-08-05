package com.onyx.diskmap.impl.base.btree

import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.data.BTreePage
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.PutResult
import com.onyx.diskmap.impl.base.AbstractDiskMap
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.ClassMetadata
import com.onyx.extension.common.castTo
import com.onyx.extension.common.forceCompare
import com.onyx.extension.common.long
import com.onyx.extension.common.toType
import com.onyx.persistence.query.QueryCriteriaOperator
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/** Persistent B+ tree with page-local keys, stable value handles, and linked leaves. */
@Suppress("UNCHECKED_CAST")
abstract class AbstractBTree<K, V>(
    store: WeakReference<Store>,
    recordStore: WeakReference<Store>,
    header: Header,
    keyType: Class<*>
) : AbstractDiskMap<K, V>(store, recordStore, header, keyType) {

    /** Pages are few and expensive to hydrate, so retain canonical strong instances. */
    protected open val pageCache = ConcurrentHashMap<Long, BTreePage>()
    protected var root: BTreePage

    private val keyKind = KeyKind.forType(keyType)
    private var mutationVersion = 0L
    private val pathPool = ThreadLocal.withInitial { ArrayDeque<SearchPath>(2) }

    init {
        root = if (reference.firstNode > 0L) {
            findPageAtPosition(reference.firstNode)
        } else {
            createRootPage()
        }
    }

    private fun createRootPage(): BTreePage =
        BTreePage.create(
            fileStore,
            leaf = true,
            compact = true,
            cacheDecodedKeys = !storeKeyWithinNode
        ).also {
            writePage(it)
            updateHeaderFirstNode(reference, it.position)
        }

    protected fun findPageAtPosition(position: Long): BTreePage {
        require(position > 0L) { "Invalid B-tree page position $position" }
        pageCache[position]?.let { return it }
        val loaded = BTreePage.get(fileStore, position, cacheDecodedKeys = !storeKeyWithinNode)
        return pageCache.putIfAbsent(position, loaded) ?: loaded
    }

    protected fun findPageAtPositionOrNull(position: Long): BTreePage? =
        if (position <= 0L) null else findPageAtPosition(position)

    protected fun findEntryAtPosition(position: Long): BTreeEntry? =
        if (position <= 0L) null else BTreeEntry.get(fileStore, position)

    protected fun updatePageCache(page: BTreePage?) {
        if (page != null) pageCache[page.position] = page
    }

    private fun writePage(page: BTreePage) {
        page.write(fileStore)
        updatePageCache(page)
    }

    override fun containsKey(key: K): Boolean {
        val castKey = key.castTo(keyType) as K
        val token = queryToken(castKey)
        val leaf = findLeaf(castKey, token)
        val index = lowerBound(leaf, castKey, token)
        return index < leaf.keyCount && compareStoredToKey(leaf, index, castKey, token) == 0
    }

    fun internalPutAndGet(
        key: K,
        value: V,
        preUpdate: ((Long) -> Unit)?,
        capturePreviousValue: Boolean
    ): PutResult {
        if (key == null) throw NullPointerException("Disk map keys cannot be null")
        val result = PutResult(key as Any, true, -1L)
        internalPut(key, value, preUpdate, result, capturePreviousValue)
        return result
    }

    private fun internalPut(
        key: K,
        value: V,
        preUpdate: ((Long) -> Unit)?,
        result: PutResult?,
        capturePreviousValue: Boolean = false
    ): Long {
        if (key == null) throw NullPointerException("Disk map keys cannot be null")
        val token = queryToken(key)
        val path = borrowPath()
        try {
            var leaf = findLeaf(key, token, path)
            var index = lowerBound(leaf, key, token)
            var found = index < leaf.keyCount && compareStoredToKey(leaf, index, key, token) == 0
            var currentRecordId = if (found) leaf.pointers[index] else -1L
            result?.isInsert = !found
            result?.recordId = currentRecordId
            result?.previousValue = if (capturePreviousValue && found) valueAt(leaf, index) else null
            val searchedAtVersion = mutationVersion

            preUpdate?.invoke(currentRecordId)

            if (mutationVersion != searchedAtVersion) {
                path.clear()
                leaf = findLeaf(key, token, path)
                index = lowerBound(leaf, key, token)
                found = index < leaf.keyCount && compareStoredToKey(leaf, index, key, token) == 0
                currentRecordId = if (found) leaf.pointers[index] else -1L
                result?.isInsert = !found
                result?.recordId = currentRecordId
                result?.previousValue = if (capturePreviousValue && found) valueAt(leaf, index) else null
            }

            val valueLocation = if (value == null) BTreeEntry.NULL_RECORD else records.writeObject(value)
            if (found) {
                val entryPosition = leaf.pointers[index]
                BTreeEntry.writeRecord(fileStore, entryPosition, valueLocation)
                leaf.recordPointers[index] = valueLocation
                result?.recordId = entryPosition
                result?.isInsert = false
                mutationVersion++
                return entryPosition
            }

            val storedKey = if (storeKeyWithinNode) token else records.writeObject(key)
            val entryPosition = BTreeEntry.createPosition(fileStore, valueLocation)
            leaf.insertLeaf(index, storedKey, entryPosition, valueLocation)
            if (!storeKeyWithinNode) leaf.decodedKeys[index] = key
            if (index == 0) propagateFirstKey(path, path.depth - 1, storedKey, leaf.decodedKeys[index])

            when {
                leaf.compact && leaf.keyCount > leaf.capacity -> promoteCompactRoot(leaf)
                leaf.keyCount > leaf.capacity -> splitLeaf(leaf, path, index)
                else -> {
                    leaf.writeSlots(fileStore, index)
                    leaf.writeCount(fileStore)
                }
            }

            incrementSize()
            mutationVersion++
            result?.recordId = entryPosition
            return entryPosition
        } finally {
            recyclePath(path)
        }
    }

    private fun promoteCompactRoot(compactRoot: BTreePage) {
        check(compactRoot.position == root.position && compactRoot.leaf)
        val promoted = BTreePage.create(
            fileStore,
            leaf = true,
            cacheDecodedKeys = !storeKeyWithinNode
        )
        promoted.keyCount = compactRoot.keyCount
        System.arraycopy(compactRoot.keys, 0, promoted.keys, 0, compactRoot.keyCount)
        System.arraycopy(compactRoot.pointers, 0, promoted.pointers, 0, compactRoot.keyCount)
        compactRoot.decodedKeys.copyTo(promoted.decodedKeys, 0, 0, compactRoot.keyCount)
        System.arraycopy(compactRoot.recordPointers, 0, promoted.recordPointers, 0, compactRoot.keyCount)
        writePage(promoted)
        pageCache.remove(compactRoot.position)
        root = promoted
        updateHeaderFirstNode(reference, promoted.position)
    }

    private fun splitLeaf(leaf: BTreePage, path: SearchPath, insertedIndex: Int) {
        val splitIndex = when {
            leaf.nextLeaf == 0L && insertedIndex == leaf.keyCount - 1 -> leaf.keyCount - EDGE_RETAINED_KEYS
            leaf.previousLeaf == 0L && insertedIndex == 0 -> EDGE_RETAINED_KEYS
            else -> leaf.keyCount / 2
        }
        val right = BTreePage.create(
            fileStore,
            leaf = true,
            cacheDecodedKeys = !storeKeyWithinNode
        )
        val rightCount = leaf.keyCount - splitIndex
        right.keyCount = rightCount
        System.arraycopy(leaf.keys, splitIndex, right.keys, 0, rightCount)
        System.arraycopy(leaf.pointers, splitIndex, right.pointers, 0, rightCount)
        leaf.decodedKeys.copyTo(right.decodedKeys, splitIndex, 0, rightCount)
        System.arraycopy(leaf.recordPointers, splitIndex, right.recordPointers, 0, rightCount)
        leaf.keyCount = splitIndex

        right.previousLeaf = leaf.position
        right.nextLeaf = leaf.nextLeaf
        if (right.nextLeaf > 0L) {
            findPageAtPosition(right.nextLeaf).also {
                it.previousLeaf = right.position
                it.writePreviousLeaf(fileStore)
            }
        }
        leaf.nextLeaf = right.position
        writePage(leaf)
        writePage(right)
        insertIntoParent(leaf, right.keys[0], right.decodedKeys[0], right, path, path.depth - 1)
    }

    private fun insertIntoParent(
        left: BTreePage,
        separator: Long,
        separatorDecoded: Any?,
        right: BTreePage,
        path: SearchPath,
        parentLevel: Int
    ) {
        if (parentLevel < 0) {
            val newRoot = BTreePage.create(
                fileStore,
                leaf = false,
                cacheDecodedKeys = !storeKeyWithinNode
            )
            newRoot.pointers[0] = left.position
            newRoot.insertInternal(0, separator, right.position)
            newRoot.decodedKeys[0] = separatorDecoded
            writePage(newRoot)
            root = newRoot
            updateHeaderFirstNode(reference, newRoot.position)
            return
        }

        val parent = path.page(parentLevel)
        val leftIndex = path.childIndexes[parentLevel]
        check(parent.pointers[leftIndex] == left.position) {
            "B-tree path does not reference child ${left.position}"
        }
        parent.insertInternal(leftIndex, separator, right.position)
        parent.decodedKeys[leftIndex] = separatorDecoded
        if (parent.keyCount > parent.capacity) {
            splitInternal(parent, path, parentLevel)
        } else {
            parent.writeSlots(fileStore, leftIndex)
            parent.writeCount(fileStore)
        }
    }

    private fun splitInternal(page: BTreePage, path: SearchPath, pageLevel: Int) {
        val median = page.keyCount / 2
        val promotedKey = page.keys[median]
        val promotedDecoded = page.decodedKeys[median]
        val right = BTreePage.create(
            fileStore,
            leaf = false,
            cacheDecodedKeys = !storeKeyWithinNode
        )
        val rightCount = page.keyCount - median - 1
        right.keyCount = rightCount
        right.pointers[0] = page.pointers[median + 1]
        if (rightCount > 0) {
            System.arraycopy(page.keys, median + 1, right.keys, 0, rightCount)
            System.arraycopy(page.pointers, median + 2, right.pointers, 1, rightCount)
            page.decodedKeys.copyTo(right.decodedKeys, median + 1, 0, rightCount)
        }
        page.keyCount = median
        writePage(page)
        writePage(right)
        insertIntoParent(page, promotedKey, promotedDecoded, right, path, pageLevel - 1)
    }

    override fun put(key: K, value: V): V {
        internalPut(key.castTo(keyType) as K, value, null, null)
        return value
    }

    override fun remove(key: K): V? {
        val castKey = key.castTo(keyType) as K
        val token = queryToken(castKey)
        val path = borrowPath()
        try {
            val leaf = findLeaf(castKey, token, path)
            val index = lowerBound(leaf, castKey, token)
            if (index >= leaf.keyCount || compareStoredToKey(leaf, index, castKey, token) != 0) return null

            val previous = valueAt(leaf, index)
            val firstChanged = index == 0
            leaf.removeLeaf(index)

            when {
                leaf.position == root.position -> {
                    leaf.writeSlots(fileStore, index)
                    leaf.writeCount(fileStore)
                }
                leaf.keyCount >= minimumKeys(leaf) || leaf.keyCount > 0 &&
                    (leaf.previousLeaf == 0L || leaf.nextLeaf == 0L) -> {
                    leaf.writeSlots(fileStore, index)
                    leaf.writeCount(fileStore)
                    if (firstChanged && leaf.keyCount > 0) {
                        propagateFirstKey(path, path.depth - 1, leaf.keys[0], leaf.decodedKeys[0])
                    }
                }
                else -> rebalanceLeaf(leaf, path, firstChanged)
            }

            decrementSize()
            mutationVersion++
            return previous
        } finally {
            recyclePath(path)
        }
    }

    private fun rebalanceLeaf(leaf: BTreePage, path: SearchPath, firstChanged: Boolean) {
        val parentLevel = path.depth - 1
        val parent = path.page(parentLevel)
        val childIndex = path.childIndexes[parentLevel]
        check(parent.pointers[childIndex] == leaf.position)
        val left = if (childIndex > 0) findPageAtPosition(parent.pointers[childIndex - 1]) else null
        val right = if (childIndex < parent.keyCount) findPageAtPosition(parent.pointers[childIndex + 1]) else null

        if (left != null && left.keyCount > minimumKeys(left)) {
            val source = left.keyCount - 1
            leaf.insertLeaf(0, left.keys[source], left.pointers[source], left.recordPointers[source])
            leaf.decodedKeys[0] = left.decodedKeys[source]
            left.removeLeaf(source)
            parent.keys[childIndex - 1] = leaf.keys[0]
            parent.decodedKeys[childIndex - 1] = leaf.decodedKeys[0]
            left.writeCount(fileStore)
            writePage(leaf)
            parent.writeKey(fileStore, childIndex - 1)
            return
        }

        if (right != null && right.keyCount > minimumKeys(right)) {
            val appendAt = leaf.keyCount
            leaf.insertLeaf(appendAt, right.keys[0], right.pointers[0], right.recordPointers[0])
            leaf.decodedKeys[appendAt] = right.decodedKeys[0]
            right.removeLeaf(0)
            parent.keys[childIndex] = right.keys[0]
            parent.decodedKeys[childIndex] = right.decodedKeys[0]
            if (firstChanged && childIndex > 0) {
                parent.keys[childIndex - 1] = leaf.keys[0]
                parent.decodedKeys[childIndex - 1] = leaf.decodedKeys[0]
            }
            writePage(right)
            writePage(leaf)
            if (firstChanged && childIndex > 0) {
                parent.writeSlots(fileStore, childIndex - 1, childIndex + 1)
            } else {
                parent.writeKey(fileStore, childIndex)
            }
            if (firstChanged && childIndex == 0) {
                propagateFirstKey(path, parentLevel - 1, leaf.keys[0], leaf.decodedKeys[0])
            }
            return
        }

        if (left != null) {
            appendLeaf(left, leaf)
            left.nextLeaf = leaf.nextLeaf
            if (leaf.nextLeaf > 0L) {
                findPageAtPosition(leaf.nextLeaf).also {
                    it.previousLeaf = left.position
                    it.writePreviousLeaf(fileStore)
                }
            }
            writePage(left)
            pageCache.remove(leaf.position)
            parent.removeInternal(childIndex - 1)
            rebalanceInternal(parent, path, parentLevel)
            return
        }

        check(right != null) { "B-tree leaf has no sibling" }
        appendLeaf(leaf, right)
        leaf.nextLeaf = right.nextLeaf
        if (right.nextLeaf > 0L) {
            findPageAtPosition(right.nextLeaf).also {
                it.previousLeaf = leaf.position
                it.writePreviousLeaf(fileStore)
            }
        }
        writePage(leaf)
        pageCache.remove(right.position)
        parent.removeInternal(childIndex)
        if (firstChanged && leaf.keyCount > 0) {
            propagateFirstKey(path, parentLevel - 1, leaf.keys[0], leaf.decodedKeys[0])
        }
        rebalanceInternal(parent, path, parentLevel)
    }

    private fun appendLeaf(destination: BTreePage, source: BTreePage) {
        check(destination.keyCount + source.keyCount <= destination.capacity)
        val offset = destination.keyCount
        System.arraycopy(source.keys, 0, destination.keys, offset, source.keyCount)
        System.arraycopy(source.pointers, 0, destination.pointers, offset, source.keyCount)
        source.decodedKeys.copyTo(destination.decodedKeys, 0, offset, source.keyCount)
        System.arraycopy(source.recordPointers, 0, destination.recordPointers, offset, source.keyCount)
        destination.keyCount += source.keyCount
    }

    private fun rebalanceInternal(page: BTreePage, path: SearchPath, pageLevel: Int) {
        if (page.position == root.position) {
            if (page.keyCount == 0) {
                val oldRoot = page.position
                root = findPageAtPosition(page.pointers[0])
                pageCache.remove(oldRoot)
                updateHeaderFirstNode(reference, root.position)
            } else {
                writePage(page)
            }
            return
        }

        if (page.keyCount >= minimumKeys(page)) {
            writePage(page)
            return
        }

        val parentLevel = pageLevel - 1
        val parent = path.page(parentLevel)
        val childIndex = path.childIndexes[parentLevel]
        check(parent.pointers[childIndex] == page.position)
        val left = if (childIndex > 0) findPageAtPosition(parent.pointers[childIndex - 1]) else null
        val right = if (childIndex < parent.keyCount) findPageAtPosition(parent.pointers[childIndex + 1]) else null

        if (left != null && left.keyCount > minimumKeys(left)) {
            System.arraycopy(page.keys, 0, page.keys, 1, page.keyCount)
            page.decodedKeys.move(0, 1, page.keyCount)
            System.arraycopy(page.pointers, 0, page.pointers, 1, page.keyCount + 1)
            page.keys[0] = parent.keys[childIndex - 1]
            page.decodedKeys[0] = parent.decodedKeys[childIndex - 1]
            page.pointers[0] = left.pointers[left.keyCount]
            page.keyCount++

            parent.keys[childIndex - 1] = left.keys[left.keyCount - 1]
            parent.decodedKeys[childIndex - 1] = left.decodedKeys[left.keyCount - 1]
            left.keyCount--
            left.writeCount(fileStore)
            writePage(page)
            parent.writeKey(fileStore, childIndex - 1)
            return
        }

        if (right != null && right.keyCount > minimumKeys(right)) {
            val oldRightFirst = right.keys[0]
            val oldRightDecoded = right.decodedKeys[0]
            page.keys[page.keyCount] = parent.keys[childIndex]
            page.decodedKeys[page.keyCount] = parent.decodedKeys[childIndex]
            page.pointers[page.keyCount + 1] = right.pointers[0]
            page.keyCount++

            System.arraycopy(right.keys, 1, right.keys, 0, right.keyCount - 1)
            right.decodedKeys.move(1, 0, right.keyCount - 1)
            System.arraycopy(right.pointers, 1, right.pointers, 0, right.keyCount)
            right.keyCount--
            parent.keys[childIndex] = oldRightFirst
            parent.decodedKeys[childIndex] = oldRightDecoded
            writePage(right)
            writePage(page)
            parent.writeKey(fileStore, childIndex)
            return
        }

        if (left != null) {
            appendInternal(left, parent.keys[childIndex - 1], parent.decodedKeys[childIndex - 1], page)
            writePage(left)
            pageCache.remove(page.position)
            parent.removeInternal(childIndex - 1)
            rebalanceInternal(parent, path, parentLevel)
            return
        }

        check(right != null) { "B-tree internal page has no sibling" }
        appendInternal(page, parent.keys[childIndex], parent.decodedKeys[childIndex], right)
        writePage(page)
        pageCache.remove(right.position)
        parent.removeInternal(childIndex)
        rebalanceInternal(parent, path, parentLevel)
    }

    private fun appendInternal(destination: BTreePage, separator: Long, decoded: Any?, source: BTreePage) {
        check(destination.keyCount + source.keyCount + 1 <= destination.capacity)
        val offset = destination.keyCount
        destination.keys[offset] = separator
        destination.decodedKeys[offset] = decoded
        destination.pointers[offset + 1] = source.pointers[0]
        if (source.keyCount > 0) {
            System.arraycopy(source.keys, 0, destination.keys, offset + 1, source.keyCount)
            source.decodedKeys.copyTo(destination.decodedKeys, 0, offset + 1, source.keyCount)
            System.arraycopy(source.pointers, 1, destination.pointers, offset + 2, source.keyCount)
        }
        destination.keyCount += source.keyCount + 1
    }

    /** Updates the first ancestor separator that stores this subtree's minimum. */
    private fun propagateFirstKey(path: SearchPath, startLevel: Int, key: Long, decoded: Any?) {
        for (level in startLevel downTo 0) {
            val childIndex = path.childIndexes[level]
            if (childIndex == 0) continue
            val parent = path.page(level)
            if (parent.keys[childIndex - 1] != key) {
                parent.keys[childIndex - 1] = key
                parent.decodedKeys[childIndex - 1] = decoded
                parent.writeKey(fileStore, childIndex - 1)
            }
            return
        }
    }

    override fun get(key: K): V? {
        val castKey = key.castTo(keyType) as K
        val token = queryToken(castKey)
        val leaf = findLeaf(castKey, token)
        val index = lowerBound(leaf, castKey, token)
        if (index >= leaf.keyCount || compareStoredToKey(leaf, index, castKey, token) != 0) return null
        return valueAt(leaf, index)
    }

    protected open fun find(key: K): BTreeEntry? {
        val token = queryToken(key)
        val leaf = findLeaf(key, token)
        val index = lowerBound(leaf, key, token)
        if (index >= leaf.keyCount || compareStoredToKey(leaf, index, key, token) != 0) return null
        return BTreeEntry(leaf.pointers[index], recordPointerAt(leaf, index))
    }

    protected fun findEntryId(key: K): Long {
        val token = queryToken(key)
        val leaf = findLeaf(key, token)
        val index = lowerBound(leaf, key, token)
        return if (index < leaf.keyCount && compareStoredToKey(leaf, index, key, token) == 0) {
            leaf.pointers[index]
        } else {
            -1L
        }
    }

    protected fun findLeaf(key: K): BTreePage = findLeaf(key, queryToken(key))

    private fun findLeaf(key: K, token: Long, path: SearchPath? = null): BTreePage {
        var page = root
        while (!page.leaf) {
            val childIndex = upperBound(page, key, token)
            path?.push(page, childIndex)
            page = findPageAtPosition(page.pointers[childIndex])
        }
        return page
    }

    protected fun leftMostLeaf(): BTreePage {
        var page = root
        while (!page.leaf) page = findPageAtPosition(page.pointers[0])
        return page
    }

    protected fun lowerBound(page: BTreePage, key: K): Int = lowerBound(page, key, queryToken(key))

    private fun lowerBound(page: BTreePage, key: K, token: Long): Int {
        var low = 0
        var high = page.keyCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareStoredToKey(page, middle, key, token) < 0) low = middle + 1 else high = middle
        }
        return low
    }

    protected fun upperBound(page: BTreePage, key: K): Int = upperBound(page, key, queryToken(key))

    private fun upperBound(page: BTreePage, key: K, token: Long): Int {
        var low = 0
        var high = page.keyCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareStoredToKey(page, middle, key, token) <= 0) low = middle + 1 else high = middle
        }
        return low
    }

    protected fun keyAt(page: BTreePage, index: Int): K {
        if (storeKeyWithinNode) return page.keys[index].toType(keyType) as K
        page.decodedKeys[index]?.let { return it as K }
        val key = records.getObject<K>(page.keys[index])
        page.decodedKeys[index] = key
        return key
    }

    protected fun recordPointerAt(page: BTreePage, index: Int): Long {
        val cached = page.recordPointers[index]
        if (cached != BTreePage.UNLOADED_RECORD) return cached
        val loaded = BTreeEntry.readRecord(fileStore, page.pointers[index])
        page.recordPointers[index] = loaded
        return loaded
    }

    protected fun entryAt(page: BTreePage, index: Int): BTreeEntry =
        BTreeEntry(page.pointers[index], recordPointerAt(page, index))

    protected fun entryIdAt(page: BTreePage, index: Int): Long = page.pointers[index]

    protected fun valueAt(page: BTreePage, index: Int): V {
        val record = recordPointerAt(page, index)
        return if (record == BTreeEntry.NULL_RECORD) null as V else records.getObject(record)
    }

    protected fun compareKeys(first: K, second: K): Int {
        if (first === second || first == second) return 0
        return try {
            (first as Comparable<Any?>).compareTo(second)
        } catch (_: Exception) {
            when {
                first.forceCompare(second, QueryCriteriaOperator.EQUAL) -> 0
                second.forceCompare(first, QueryCriteriaOperator.GREATER_THAN) -> 1
                else -> -1
            }
        }
    }

    protected fun addPositionsAscending(key: K, inclusive: Boolean, destination: MutableSet<Long>) {
        val token = queryToken(key)
        var page: BTreePage? = findLeaf(key, token)
        var index = if (inclusive) lowerBound(page!!, key, token) else upperBound(page!!, key, token)
        while (page != null) {
            while (index < page.keyCount) destination.add(page.pointers[index++])
            page = findPageAtPositionOrNull(page.nextLeaf)
            index = 0
        }
    }

    protected fun addPositionsDescending(key: K, inclusive: Boolean, destination: MutableSet<Long>) {
        val token = queryToken(key)
        var page: BTreePage? = findLeaf(key, token)
        var index = (if (inclusive) upperBound(page!!, key, token) else lowerBound(page!!, key, token)) - 1
        while (page != null) {
            while (index >= 0) destination.add(page.pointers[index--])
            page = findPageAtPositionOrNull(page.previousLeaf)
            index = (page?.keyCount ?: 0) - 1
        }
    }

    protected fun addPositionsBetween(
        from: K,
        includeFrom: Boolean,
        to: K,
        includeTo: Boolean,
        destination: MutableSet<Long>
    ) {
        val fromToken = queryToken(from)
        val toToken = queryToken(to)
        var page: BTreePage? = findLeaf(from, fromToken)
        var index = if (includeFrom) lowerBound(page!!, from, fromToken) else upperBound(page!!, from, fromToken)
        while (page != null) {
            while (index < page.keyCount) {
                val comparison = compareStoredToKey(page, index, to, toToken)
                if (comparison > 0 || comparison == 0 && !includeTo) return
                destination.add(page.pointers[index++])
            }
            page = findPageAtPositionOrNull(page.nextLeaf)
            index = 0
        }
    }

    private fun queryToken(key: K): Long = if (storeKeyWithinNode) (key as Any).long() else 0L

    private fun compareStoredToKey(page: BTreePage, index: Int, key: K, token: Long): Int =
        when (keyKind) {
            KeyKind.INTEGRAL -> java.lang.Long.compare(page.keys[index], token)
            KeyKind.FLOAT -> java.lang.Float.compare(
                java.lang.Float.intBitsToFloat(page.keys[index].toInt()),
                key as Float
            )
            KeyKind.DOUBLE -> java.lang.Double.compare(java.lang.Double.longBitsToDouble(page.keys[index]), key as Double)
            KeyKind.OBJECT -> compareKeys(keyAt(page, index), key)
        }

    override fun clearCache() {
        val rootPosition = root.position
        pageCache.clear()
        root = BTreePage.get(fileStore, rootPosition, cacheDecodedKeys = !storeKeyWithinNode)
        updatePageCache(root)
    }

    protected fun resetTree() {
        pageCache.clear()
        root = createRootPage()
        reference.recordCount.set(0L)
        updateHeaderRecordCount(0L)
        mutationVersion++
    }

    private fun borrowPath(): SearchPath = pathPool.get().pollFirst()?.also { it.clear() } ?: SearchPath()

    private fun recyclePath(path: SearchPath) {
        path.clear()
        pathPool.get().offerFirst(path)
    }

    private fun minimumKeys(page: BTreePage): Int = page.capacity / 2

    private class SearchPath {
        val pages: Array<BTreePage?> = arrayOfNulls(MAX_HEIGHT)
        val childIndexes = IntArray(MAX_HEIGHT)
        var depth = 0

        fun push(page: BTreePage, childIndex: Int) {
            check(depth < MAX_HEIGHT) { "B-tree exceeds supported height $MAX_HEIGHT" }
            pages[depth] = page
            childIndexes[depth] = childIndex
            depth++
        }

        fun page(level: Int): BTreePage = requireNotNull(pages[level])

        fun clear() {
            for (index in 0 until depth) pages[index] = null
            depth = 0
        }
    }

    private enum class KeyKind {
        INTEGRAL,
        FLOAT,
        DOUBLE,
        OBJECT;

        companion object {
            fun forType(type: Class<*>): KeyKind = when (type) {
                ClassMetadata.FLOAT_TYPE, ClassMetadata.FLOAT_PRIMITIVE_TYPE -> FLOAT
                ClassMetadata.DOUBLE_TYPE, ClassMetadata.DOUBLE_PRIMITIVE_TYPE -> DOUBLE
                ClassMetadata.LONG_TYPE, ClassMetadata.LONG_PRIMITIVE_TYPE,
                ClassMetadata.INT_TYPE, ClassMetadata.INT_PRIMITIVE_TYPE,
                ClassMetadata.BYTE_TYPE, ClassMetadata.BYTE_PRIMITIVE_TYPE,
                ClassMetadata.CHAR_TYPE, ClassMetadata.CHAR_PRIMITIVE_TYPE,
                ClassMetadata.SHORT_TYPE, ClassMetadata.SHORT_PRIMITIVE_TYPE,
                ClassMetadata.BOOLEAN_TYPE, ClassMetadata.BOOLEAN_PRIMITIVE_TYPE -> INTEGRAL
                else -> OBJECT
            }
        }
    }

    companion object {
        private const val EDGE_RETAINED_KEYS = 15
        private const val MAX_HEIGHT = 16
    }
}
