package com.onyx.diskmap.impl

import com.onyx.buffer.BufferPool.withBigIntBuffer
import com.onyx.diskmap.IndexPostingMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.IndexPostingPage
import com.onyx.diskmap.data.IndexPostingPage.ValueKind
import com.onyx.diskmap.data.putBigInt
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.ClassMetadata
import com.onyx.extension.common.canBeCastToPrimitive
import com.onyx.extension.common.castTo
import com.onyx.extension.common.forceCompare
import com.onyx.extension.common.long
import com.onyx.extension.common.toType
import com.onyx.lang.concurrent.ClosureReadWriteLock
import com.onyx.lang.concurrent.impl.DefaultClosureReadWriteLock
import com.onyx.lang.map.ConcurrentWeakValueMap
import com.onyx.persistence.query.QueryCriteriaOperator
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.Date
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Native B+ tree for regular secondary-index postings.
 *
 * Unlike a generic [DiskBTreeMap], this structure has no values or stable
 * entry handles. A leaf slot is the complete `(indexValue, recordId)` key, and
 * internal separators retain both components so duplicate values can span any
 * number of pages without a nested map.
 */
class DiskIndexPostingMap(
    private val nodeStoreReference: WeakReference<Store>,
    private val dataStoreReference: WeakReference<Store>,
    header: Header,
    override val valueType: Class<*>
) : IndexPostingMap {

    private val nodeStore: Store
        get() = requireNotNull(nodeStoreReference.get()) { "Index node store is no longer available" }

    private val dataStore: Store
        get() = requireNotNull(dataStoreReference.get()) { "Index data store is no longer available" }

    private val valueKind = valueKindFor(valueType)
    private val valueTokenWidth = valueTokenWidthFor(valueType)
    private val signedValueToken = signedValueTokenFor(valueType)
    private val reference = Header().also {
        it.firstNode = header.firstNode
        it.position = header.position
        it.recordCount = AtomicLong(header.recordCount.get())
    }

    private val pageCache = ConcurrentWeakValueMap<Long, IndexPostingPage>()
    private val objectTokensByValue = WeakHashMap<Any, Long>()
    private val objectValuesByToken = ConcurrentWeakValueMap<Long, Any>()
    private val pathPool = ThreadLocal.withInitial { ArrayDeque<SearchPath>(2) }
    private val lock: ClosureReadWriteLock = DefaultClosureReadWriteLock()

    private var root: IndexPostingPage = if (reference.firstNode > 0L) {
        findPage(reference.firstNode)
    } else {
        createRootPage()
    }

    override fun add(indexValue: Any, recordId: Long): Boolean = lock.writeLock {
        require(recordId > 0L) { "Index posting record IDs must be positive" }
        addInternal(normalize(indexValue), recordId)
    }

    override fun remove(indexValue: Any, recordId: Long): Boolean = lock.writeLock {
        if (recordId <= 0L) return@writeLock false
        removeInternal(normalize(indexValue), recordId)
    }

    override fun contains(indexValue: Any, recordId: Long): Boolean = lock.readLock {
        if (recordId <= 0L) return@readLock false
        val value = normalize(indexValue)
        val token = queryToken(value)
        val leaf = findLeaf(value, token, recordId)
        val index = lowerBound(leaf, value, token, recordId)
        index < leaf.keyCount && compareStoredToQuery(leaf, index, value, token, recordId) == 0
    }

    override fun forEachRecordIdInRange(
        fromValue: Any?,
        fromRecordId: Long,
        includeFrom: Boolean,
        toValue: Any?,
        toRecordId: Long,
        includeTo: Boolean,
        action: (Long) -> Unit
    ) = lock.readLock {
        val from = fromValue?.let(::normalize)
        val to = toValue?.let(::normalize)
        visitRecordIds(from, fromRecordId, includeFrom, to, toRecordId, includeTo, Int.MAX_VALUE) {
            action(it)
            true
        }
        Unit
    }

    override fun visitRecordIdsInRange(
        fromValue: Any?,
        fromRecordId: Long,
        includeFrom: Boolean,
        toValue: Any?,
        toRecordId: Long,
        includeTo: Boolean,
        maxVisits: Int,
        visitor: (Long) -> Boolean
    ): Int = lock.readLock {
        require(maxVisits >= 0) { "maxVisits must be non-negative" }
        if (maxVisits == 0) return@readLock 0
        val from = fromValue?.let(::normalize)
        val to = toValue?.let(::normalize)
        visitRecordIds(from, fromRecordId, includeFrom, to, toRecordId, includeTo, maxVisits, visitor)
    }

    override fun forEachDistinctValue(action: (Any) -> Unit) = lock.readLock {
        var page: IndexPostingPage? = leftMostLeaf()
        var hasPrevious = false
        var previousToken = 0L
        var previousValue: Any? = null

        while (page != null) {
            for (index in 0 until page.keyCount) {
                val token = page.valueTokens[index]
                val isNewValue = when {
                    !hasPrevious -> true
                    valueKind != ValueKind.OBJECT -> compareInlineTokens(previousToken, token) != 0
                    previousToken == token -> false
                    else -> compareValues(requireNotNull(previousValue), valueAt(page, index)) != 0
                }

                if (isNewValue) {
                    val value = valueAt(page, index)
                    action(value)
                    previousValue = value
                    previousToken = token
                    hasPrevious = true
                }
            }
            page = findPageOrNull(page.nextLeaf)
        }
    }

    override fun longSize(): Long = reference.recordCount.get()

    override fun clear() = lock.writeLock {
        pageCache.clear()
        objectTokensByValue.clear()
        objectValuesByToken.clear()
        root = createRootPage()
        reference.recordCount.set(0L)
        updateHeaderRecordCount(0L)
    }

    override fun clearCache() = lock.writeLock {
        val rootPosition = root.position
        pageCache.clear()
        objectTokensByValue.clear()
        objectValuesByToken.clear()
        root = IndexPostingPage.get(nodeStore, rootPosition, valueKind, valueTokenWidth, signedValueToken)
        pageCache[root.position] = root
    }

    private fun addInternal(value: Any, recordId: Long): Boolean {
        val queryToken = queryToken(value)
        val path = borrowPath()
        try {
            val leaf = findLeaf(value, queryToken, recordId, path)
            val index = lowerBound(leaf, value, queryToken, recordId)
            if (index < leaf.keyCount && compareStoredToQuery(leaf, index, value, queryToken, recordId) == 0) {
                return false
            }

            val storedToken: Long
            val decodedValue: Any?
            if (valueKind == ValueKind.OBJECT) {
                storedToken = findObjectToken(leaf, index, value)
                    ?: dataStore.writeObject(value)
                decodedValue = canonicalObjectValue(storedToken, value)
            } else {
                storedToken = queryToken
                decodedValue = null
            }

            leaf.insertLeaf(index, storedToken, recordId, decodedValue)
            if (index == 0) {
                propagateFirstKey(path, path.depth - 1, storedToken, recordId, decodedValue)
            }

            when {
                leaf.compact && leaf.keyCount > leaf.capacity -> promoteCompactRoot(leaf)
                leaf.keyCount > leaf.capacity -> splitLeaf(leaf, path, index)
                else -> {
                    leaf.writeSlots(nodeStore, index)
                    leaf.writeCount(nodeStore)
                }
            }

            incrementSize()
            return true
        } finally {
            recyclePath(path)
        }
    }

    private fun removeInternal(value: Any, recordId: Long): Boolean {
        val queryToken = queryToken(value)
        val path = borrowPath()
        try {
            val leaf = findLeaf(value, queryToken, recordId, path)
            val index = lowerBound(leaf, value, queryToken, recordId)
            if (index >= leaf.keyCount || compareStoredToQuery(leaf, index, value, queryToken, recordId) != 0) {
                return false
            }

            val firstChanged = index == 0
            leaf.removeLeaf(index)

            when {
                leaf.position == root.position -> {
                    leaf.writeSlots(nodeStore, index)
                    leaf.writeCount(nodeStore)
                }
                leaf.keyCount >= minimumKeys(leaf) || leaf.keyCount > 0 &&
                    (leaf.previousLeaf == 0L || leaf.nextLeaf == 0L) -> {
                    leaf.writeSlots(nodeStore, index)
                    leaf.writeCount(nodeStore)
                    if (firstChanged && leaf.keyCount > 0) {
                        propagateFirstKey(
                            path,
                            path.depth - 1,
                            leaf.valueTokens[0],
                            leaf.recordIds[0],
                            leaf.decodedValue(0)
                        )
                    }
                }
                else -> rebalanceLeaf(leaf, path, firstChanged)
            }

            decrementSize()
            return true
        } finally {
            recyclePath(path)
        }
    }

    private fun createRootPage(): IndexPostingPage =
        IndexPostingPage.create(
            nodeStore,
            leaf = true,
            compact = true,
            valueKind = valueKind,
            valueTokenWidth = valueTokenWidth,
            signedValueToken = signedValueToken
        ).also {
            writePage(it)
            updateHeaderFirstNode(it.position)
        }

    private fun findPage(position: Long): IndexPostingPage {
        require(position > 0L) { "Invalid index posting page position $position" }
        pageCache[position]?.let { return it }
        val loaded = IndexPostingPage.get(
            nodeStore,
            position,
            valueKind,
            valueTokenWidth,
            signedValueToken
        )
        return pageCache.putIfAbsent(position, loaded) ?: loaded
    }

    private fun findPageOrNull(position: Long): IndexPostingPage? =
        if (position <= 0L) null else findPage(position)

    private fun writePage(page: IndexPostingPage) {
        page.write(nodeStore)
        pageCache[page.position] = page
    }

    private fun promoteCompactRoot(compactRoot: IndexPostingPage) {
        check(compactRoot.position == root.position && compactRoot.leaf)
        val promoted = IndexPostingPage.createLike(nodeStore, compactRoot, leaf = true)
        promoted.keyCount = compactRoot.keyCount
        System.arraycopy(compactRoot.valueTokens, 0, promoted.valueTokens, 0, compactRoot.keyCount)
        System.arraycopy(compactRoot.recordIds, 0, promoted.recordIds, 0, compactRoot.keyCount)
        compactRoot.copyDecodedValues(0, promoted, 0, compactRoot.keyCount)
        writePage(promoted)
        pageCache.remove(compactRoot.position)
        root = promoted
        updateHeaderFirstNode(promoted.position)
    }

    private fun splitLeaf(leaf: IndexPostingPage, path: SearchPath, insertedIndex: Int) {
        val splitIndex = when {
            leaf.nextLeaf == 0L && insertedIndex == leaf.keyCount - 1 ->
                leaf.keyCount - EDGE_KEYS_TO_RETAIN
            leaf.previousLeaf == 0L && insertedIndex == 0 -> EDGE_KEYS_TO_RETAIN
            else -> leaf.keyCount / 2
        }
        val right = IndexPostingPage.createLike(nodeStore, leaf, leaf = true)
        val rightCount = leaf.keyCount - splitIndex
        right.keyCount = rightCount
        System.arraycopy(leaf.valueTokens, splitIndex, right.valueTokens, 0, rightCount)
        System.arraycopy(leaf.recordIds, splitIndex, right.recordIds, 0, rightCount)
        leaf.copyDecodedValues(splitIndex, right, 0, rightCount)
        clearKeyTail(leaf, splitIndex, leaf.keyCount)
        leaf.keyCount = splitIndex

        right.previousLeaf = leaf.position
        right.nextLeaf = leaf.nextLeaf
        if (right.nextLeaf > 0L) {
            findPage(right.nextLeaf).also {
                it.previousLeaf = right.position
                it.writePreviousLeaf(nodeStore)
            }
        }
        leaf.nextLeaf = right.position
        writePage(leaf)
        writePage(right)
        insertIntoParent(
            leaf,
            right.valueTokens[0],
            right.recordIds[0],
            right.decodedValue(0),
            right,
            path,
            path.depth - 1
        )
    }

    private fun insertIntoParent(
        left: IndexPostingPage,
        separatorToken: Long,
        separatorRecordId: Long,
        separatorDecoded: Any?,
        right: IndexPostingPage,
        path: SearchPath,
        parentLevel: Int
    ) {
        if (parentLevel < 0) {
            val newRoot = IndexPostingPage.createLike(nodeStore, left, leaf = false)
            newRoot.children[0] = left.position
            newRoot.insertInternal(
                0,
                separatorToken,
                separatorRecordId,
                right.position,
                separatorDecoded
            )
            writePage(newRoot)
            root = newRoot
            updateHeaderFirstNode(newRoot.position)
            return
        }

        val parent = path.page(parentLevel)
        val leftIndex = path.childIndexes[parentLevel]
        check(parent.children[leftIndex] == left.position) {
            "Index posting path does not reference child ${left.position}"
        }
        parent.insertInternal(
            leftIndex,
            separatorToken,
            separatorRecordId,
            right.position,
            separatorDecoded
        )
        if (parent.keyCount > parent.capacity) {
            splitInternal(parent, path, parentLevel)
        } else {
            parent.writeSlots(nodeStore, leftIndex)
            parent.writeCount(nodeStore)
        }
    }

    private fun splitInternal(page: IndexPostingPage, path: SearchPath, pageLevel: Int) {
        val median = page.keyCount / 2
        val promotedToken = page.valueTokens[median]
        val promotedRecordId = page.recordIds[median]
        val promotedDecoded = page.decodedValue(median)
        val right = IndexPostingPage.createLike(nodeStore, page, leaf = false)
        val rightCount = page.keyCount - median - 1
        right.keyCount = rightCount
        right.children[0] = page.children[median + 1]
        if (rightCount > 0) {
            System.arraycopy(page.valueTokens, median + 1, right.valueTokens, 0, rightCount)
            System.arraycopy(page.recordIds, median + 1, right.recordIds, 0, rightCount)
            page.copyDecodedValues(median + 1, right, 0, rightCount)
            System.arraycopy(page.children, median + 2, right.children, 1, rightCount)
        }
        clearKeyTail(page, median, page.keyCount)
        page.keyCount = median
        writePage(page)
        writePage(right)
        insertIntoParent(
            page,
            promotedToken,
            promotedRecordId,
            promotedDecoded,
            right,
            path,
            pageLevel - 1
        )
    }

    private fun rebalanceLeaf(leaf: IndexPostingPage, path: SearchPath, firstChanged: Boolean) {
        val parentLevel = path.depth - 1
        val parent = path.page(parentLevel)
        val childIndex = path.childIndexes[parentLevel]
        check(parent.children[childIndex] == leaf.position)
        val left = if (childIndex > 0) findPage(parent.children[childIndex - 1]) else null
        val right = if (childIndex < parent.keyCount) findPage(parent.children[childIndex + 1]) else null

        if (left != null && left.keyCount > minimumKeys(left)) {
            val source = left.keyCount - 1
            leaf.insertLeaf(
                0,
                left.valueTokens[source],
                left.recordIds[source],
                left.decodedValue(source)
            )
            left.removeLeaf(source)
            copyKey(leaf, 0, parent, childIndex - 1)
            left.writeCount(nodeStore)
            writePage(leaf)
            parent.writeKey(nodeStore, childIndex - 1)
            return
        }

        if (right != null && right.keyCount > minimumKeys(right)) {
            val destinationIndex = leaf.keyCount
            leaf.insertLeaf(
                destinationIndex,
                right.valueTokens[0],
                right.recordIds[0],
                right.decodedValue(0)
            )
            right.removeLeaf(0)
            copyKey(right, 0, parent, childIndex)
            if (firstChanged && childIndex > 0) copyKey(leaf, 0, parent, childIndex - 1)
            writePage(right)
            writePage(leaf)
            parent.writeKey(nodeStore, childIndex)
            if (firstChanged && childIndex > 0) parent.writeKey(nodeStore, childIndex - 1)
            if (firstChanged && childIndex == 0) {
                propagateFirstKey(
                    path,
                    parentLevel - 1,
                    leaf.valueTokens[0],
                    leaf.recordIds[0],
                    leaf.decodedValue(0)
                )
            }
            return
        }

        if (left != null) {
            appendLeaf(left, leaf)
            left.nextLeaf = leaf.nextLeaf
            if (leaf.nextLeaf > 0L) {
                findPage(leaf.nextLeaf).also {
                    it.previousLeaf = left.position
                    it.writePreviousLeaf(nodeStore)
                }
            }
            writePage(left)
            pageCache.remove(leaf.position)
            parent.removeInternal(childIndex - 1)
            rebalanceInternal(parent, path, parentLevel)
            return
        }

        check(right != null) { "Index posting leaf has no sibling" }
        appendLeaf(leaf, right)
        leaf.nextLeaf = right.nextLeaf
        if (right.nextLeaf > 0L) {
            findPage(right.nextLeaf).also {
                it.previousLeaf = leaf.position
                it.writePreviousLeaf(nodeStore)
            }
        }
        writePage(leaf)
        pageCache.remove(right.position)
        parent.removeInternal(childIndex)
        if (firstChanged && leaf.keyCount > 0) {
            if (childIndex > 0) {
                copyKey(leaf, 0, parent, childIndex - 1)
            } else {
                propagateFirstKey(
                    path,
                    parentLevel - 1,
                    leaf.valueTokens[0],
                    leaf.recordIds[0],
                    leaf.decodedValue(0)
                )
            }
        }
        rebalanceInternal(parent, path, parentLevel)
    }

    private fun appendLeaf(destination: IndexPostingPage, source: IndexPostingPage) {
        check(destination.keyCount + source.keyCount <= destination.capacity)
        val offset = destination.keyCount
        System.arraycopy(source.valueTokens, 0, destination.valueTokens, offset, source.keyCount)
        System.arraycopy(source.recordIds, 0, destination.recordIds, offset, source.keyCount)
        source.copyDecodedValues(0, destination, offset, source.keyCount)
        destination.keyCount += source.keyCount
    }

    private fun rebalanceInternal(page: IndexPostingPage, path: SearchPath, pageLevel: Int) {
        if (page.position == root.position) {
            if (page.keyCount == 0) {
                val oldRoot = page.position
                root = findPage(page.children[0])
                pageCache.remove(oldRoot)
                updateHeaderFirstNode(root.position)
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
        check(parent.children[childIndex] == page.position)
        val left = if (childIndex > 0) findPage(parent.children[childIndex - 1]) else null
        val right = if (childIndex < parent.keyCount) findPage(parent.children[childIndex + 1]) else null

        if (left != null && left.keyCount > minimumKeys(left)) {
            System.arraycopy(page.valueTokens, 0, page.valueTokens, 1, page.keyCount)
            System.arraycopy(page.recordIds, 0, page.recordIds, 1, page.keyCount)
            page.copyDecodedValues(0, page, 1, page.keyCount)
            System.arraycopy(page.children, 0, page.children, 1, page.keyCount + 1)
            page.valueTokens[0] = parent.valueTokens[childIndex - 1]
            page.recordIds[0] = parent.recordIds[childIndex - 1]
            page.setDecodedValue(0, parent.decodedValue(childIndex - 1))
            page.children[0] = left.children[left.keyCount]
            page.keyCount++

            parent.valueTokens[childIndex - 1] = left.valueTokens[left.keyCount - 1]
            parent.recordIds[childIndex - 1] = left.recordIds[left.keyCount - 1]
            parent.setDecodedValue(childIndex - 1, left.decodedValue(left.keyCount - 1))
            left.keyCount--
            clearKeyTail(left, left.keyCount, left.keyCount + 1)
            left.writeCount(nodeStore)
            writePage(page)
            parent.writeKey(nodeStore, childIndex - 1)
            return
        }

        if (right != null && right.keyCount > minimumKeys(right)) {
            val oldRightToken = right.valueTokens[0]
            val oldRightRecordId = right.recordIds[0]
            val oldRightDecoded = right.decodedValue(0)
            val destinationIndex = page.keyCount
            page.valueTokens[destinationIndex] = parent.valueTokens[childIndex]
            page.recordIds[destinationIndex] = parent.recordIds[childIndex]
            page.setDecodedValue(destinationIndex, parent.decodedValue(childIndex))
            page.children[destinationIndex + 1] = right.children[0]
            page.keyCount++

            System.arraycopy(right.valueTokens, 1, right.valueTokens, 0, right.keyCount - 1)
            System.arraycopy(right.recordIds, 1, right.recordIds, 0, right.keyCount - 1)
            right.copyDecodedValues(1, right, 0, right.keyCount - 1)
            System.arraycopy(right.children, 1, right.children, 0, right.keyCount)
            right.keyCount--
            clearKeyTail(right, right.keyCount, right.keyCount + 1)
            right.children[right.keyCount + 1] = 0L
            parent.valueTokens[childIndex] = oldRightToken
            parent.recordIds[childIndex] = oldRightRecordId
            parent.setDecodedValue(childIndex, oldRightDecoded)
            writePage(right)
            writePage(page)
            parent.writeKey(nodeStore, childIndex)
            return
        }

        if (left != null) {
            appendInternal(
                left,
                parent.valueTokens[childIndex - 1],
                parent.recordIds[childIndex - 1],
                parent.decodedValue(childIndex - 1),
                page
            )
            writePage(left)
            pageCache.remove(page.position)
            parent.removeInternal(childIndex - 1)
            rebalanceInternal(parent, path, parentLevel)
            return
        }

        check(right != null) { "Index posting internal page has no sibling" }
        appendInternal(
            page,
            parent.valueTokens[childIndex],
            parent.recordIds[childIndex],
            parent.decodedValue(childIndex),
            right
        )
        writePage(page)
        pageCache.remove(right.position)
        parent.removeInternal(childIndex)
        rebalanceInternal(parent, path, parentLevel)
    }

    private fun appendInternal(
        destination: IndexPostingPage,
        separatorToken: Long,
        separatorRecordId: Long,
        separatorDecoded: Any?,
        source: IndexPostingPage
    ) {
        check(destination.keyCount + source.keyCount + 1 <= destination.capacity)
        val offset = destination.keyCount
        destination.valueTokens[offset] = separatorToken
        destination.recordIds[offset] = separatorRecordId
        destination.setDecodedValue(offset, separatorDecoded)
        destination.children[offset + 1] = source.children[0]
        if (source.keyCount > 0) {
            System.arraycopy(source.valueTokens, 0, destination.valueTokens, offset + 1, source.keyCount)
            System.arraycopy(source.recordIds, 0, destination.recordIds, offset + 1, source.keyCount)
            source.copyDecodedValues(0, destination, offset + 1, source.keyCount)
            System.arraycopy(source.children, 1, destination.children, offset + 2, source.keyCount)
        }
        destination.keyCount += source.keyCount + 1
    }

    /** Updates the first ancestor separator that stores this subtree's minimum tuple. */
    private fun propagateFirstKey(
        path: SearchPath,
        startLevel: Int,
        valueToken: Long,
        recordId: Long,
        decodedValue: Any?
    ) {
        for (level in startLevel downTo 0) {
            val childIndex = path.childIndexes[level]
            if (childIndex == 0) continue
            val parent = path.page(level)
            val separatorIndex = childIndex - 1
            if (parent.valueTokens[separatorIndex] != valueToken || parent.recordIds[separatorIndex] != recordId) {
                parent.valueTokens[separatorIndex] = valueToken
                parent.recordIds[separatorIndex] = recordId
                parent.setDecodedValue(separatorIndex, decodedValue)
                parent.writeKey(nodeStore, separatorIndex)
            }
            return
        }
    }

    private fun visitRecordIds(
        fromValue: Any?,
        fromRecordId: Long,
        includeFrom: Boolean,
        toValue: Any?,
        toRecordId: Long,
        includeTo: Boolean,
        maxVisits: Int,
        visitor: (Long) -> Boolean
    ): Int {
        if (maxVisits == 0) return 0
        val fromToken = fromValue?.let(::queryToken)
        val toToken = toValue?.let(::queryToken)
        var visits = 0
        var page: IndexPostingPage? = if (fromValue == null) {
            leftMostLeaf()
        } else {
            findLeaf(fromValue, fromToken!!, fromRecordId)
        }
        var index = when {
            fromValue == null -> 0
            includeFrom -> lowerBound(page!!, fromValue, fromToken!!, fromRecordId)
            else -> upperBound(page!!, fromValue, fromToken!!, fromRecordId)
        }

        while (page != null) {
            while (index < page.keyCount) {
                if (toValue != null) {
                    val comparison = compareStoredToQuery(page, index, toValue, toToken!!, toRecordId)
                    if (comparison > 0 || comparison == 0 && !includeTo) return visits
                }
                val continueVisiting = visitor(page.recordIds[index++])
                visits++
                if (!continueVisiting || visits >= maxVisits) return visits
            }
            page = findPageOrNull(page.nextLeaf)
            index = 0
        }
        return visits
    }

    private fun findLeaf(
        value: Any,
        valueToken: Long,
        recordId: Long,
        path: SearchPath? = null
    ): IndexPostingPage {
        var page = root
        while (!page.leaf) {
            val childIndex = upperBound(page, value, valueToken, recordId)
            path?.push(page, childIndex)
            page = findPage(page.children[childIndex])
        }
        return page
    }

    private fun leftMostLeaf(): IndexPostingPage {
        var page = root
        while (!page.leaf) page = findPage(page.children[0])
        return page
    }

    private fun lowerBound(page: IndexPostingPage, value: Any, valueToken: Long, recordId: Long): Int {
        var low = 0
        var high = page.keyCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareStoredToQuery(page, middle, value, valueToken, recordId) < 0) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }

    private fun upperBound(page: IndexPostingPage, value: Any, valueToken: Long, recordId: Long): Int {
        var low = 0
        var high = page.keyCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareStoredToQuery(page, middle, value, valueToken, recordId) <= 0) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        return low
    }

    private fun compareStoredToQuery(
        page: IndexPostingPage,
        index: Int,
        value: Any,
        valueToken: Long,
        recordId: Long
    ): Int {
        val valueComparison = when (valueKind) {
            ValueKind.INTEGRAL, ValueKind.DATE -> java.lang.Long.compare(page.valueTokens[index], valueToken)
            ValueKind.FLOAT -> java.lang.Float.compare(
                java.lang.Float.intBitsToFloat(page.valueTokens[index].toInt()),
                value as Float
            )
            ValueKind.DOUBLE -> java.lang.Double.compare(
                java.lang.Double.longBitsToDouble(page.valueTokens[index]),
                value as Double
            )
            ValueKind.OBJECT -> compareValues(valueAt(page, index), value)
        }
        return if (valueComparison != 0) valueComparison else java.lang.Long.compare(page.recordIds[index], recordId)
    }

    private fun compareInlineTokens(first: Long, second: Long): Int = when (valueKind) {
        ValueKind.INTEGRAL, ValueKind.DATE -> java.lang.Long.compare(first, second)
        ValueKind.FLOAT -> java.lang.Float.compare(
            java.lang.Float.intBitsToFloat(first.toInt()),
            java.lang.Float.intBitsToFloat(second.toInt())
        )
        ValueKind.DOUBLE -> java.lang.Double.compare(
            java.lang.Double.longBitsToDouble(first),
            java.lang.Double.longBitsToDouble(second)
        )
        ValueKind.OBJECT -> error("Object index values do not have sortable inline tokens")
    }

    private fun valueAt(page: IndexPostingPage, index: Int): Any {
        if (valueKind != ValueKind.OBJECT) return inlineValue(page.valueTokens[index])
        page.decodedValue(index)?.let { return it }

        val token = page.valueTokens[index]
        objectValuesByToken[token]?.let { cached ->
            page.setDecodedValue(index, cached)
            return cached
        }

        val decoded = dataStore.getObject<Any>(token)
        page.setDecodedValue(index, decoded)
        return decoded
    }

    private fun inlineValue(token: Long): Any = when (valueKind) {
        ValueKind.INTEGRAL, ValueKind.FLOAT, ValueKind.DOUBLE -> token.toType(valueType)
        ValueKind.DATE -> Date(token)
        ValueKind.OBJECT -> error("Object index values must be decoded from the data store")
    }

    private fun findObjectToken(leaf: IndexPostingPage, insertionIndex: Int, value: Any): Long? {
        objectTokensByValue[value]?.let { return it }

        fun tokenIfEqual(page: IndexPostingPage, index: Int): Long? {
            if (index !in 0 until page.keyCount) return null
            val canonical = valueAt(page, index)
            if (compareValues(canonical, value) != 0) return null
            val token = page.valueTokens[index]
            objectValuesByToken.putIfAbsent(token, canonical)
            objectTokensByValue.putIfAbsent(canonical, token)
            return token
        }

        tokenIfEqual(leaf, insertionIndex - 1)?.let { return it }
        tokenIfEqual(leaf, insertionIndex)?.let { return it }
        if (insertionIndex == 0) {
            findPageOrNull(leaf.previousLeaf)?.let { previous ->
                tokenIfEqual(previous, previous.keyCount - 1)?.let { return it }
            }
        }
        if (insertionIndex == leaf.keyCount) {
            findPageOrNull(leaf.nextLeaf)?.let { next ->
                tokenIfEqual(next, 0)?.let { return it }
            }
        }
        return null
    }

    private fun canonicalObjectValue(token: Long, fallback: Any): Any {
        objectValuesByToken[token]?.let { return it }
        return fallback
    }

    private fun normalize(value: Any): Any {
        if (valueType.isInstance(value)) return value
        return value.castTo(valueType)
            ?: throw IllegalArgumentException(
                "Index value ${value.javaClass.name} cannot be converted to ${valueType.name}"
            )
    }

    private fun queryToken(value: Any): Long = when (valueKind) {
        ValueKind.INTEGRAL, ValueKind.FLOAT, ValueKind.DOUBLE -> value.long()
        ValueKind.DATE -> (value as Date).time
        ValueKind.OBJECT -> 0L
    }

    @Suppress("UNCHECKED_CAST")
    private fun compareValues(first: Any, second: Any): Int {
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

    private fun copyKey(
        source: IndexPostingPage,
        sourceIndex: Int,
        destination: IndexPostingPage,
        destinationIndex: Int
    ) {
        destination.valueTokens[destinationIndex] = source.valueTokens[sourceIndex]
        destination.recordIds[destinationIndex] = source.recordIds[sourceIndex]
        destination.setDecodedValue(destinationIndex, source.decodedValue(sourceIndex))
    }

    private fun clearKeyTail(page: IndexPostingPage, fromIndex: Int, toIndex: Int) {
        for (index in fromIndex until toIndex) {
            page.valueTokens[index] = 0L
            page.recordIds[index] = 0L
            page.setDecodedValue(index, null)
        }
    }

    private fun minimumKeys(page: IndexPostingPage): Int = page.capacity / 2

    private fun incrementSize() {
        updateHeaderRecordCount(reference.recordCount.incrementAndGet())
    }

    private fun decrementSize() {
        updateHeaderRecordCount(reference.recordCount.decrementAndGet())
    }

    private fun updateHeaderRecordCount(count: Long) {
        withBigIntBuffer {
            it.putBigInt(count)
            it.rewind()
            nodeStore.write(it, reference.position + HEADER_RECORD_COUNT_OFFSET)
        }
    }

    private fun updateHeaderFirstNode(position: Long) {
        if (reference.firstNode == position) return
        reference.firstNode = position
        withBigIntBuffer {
            it.putBigInt(position)
            it.rewind()
            nodeStore.write(it, reference.position)
        }
    }

    private fun borrowPath(): SearchPath = pathPool.get().pollFirst()?.also { it.clear() } ?: SearchPath()

    private fun recyclePath(path: SearchPath) {
        path.clear()
        pathPool.get().offerFirst(path)
    }

    private class SearchPath {
        val pages: Array<IndexPostingPage?> = arrayOfNulls(MAX_HEIGHT)
        val childIndexes = IntArray(MAX_HEIGHT)
        var depth = 0

        fun push(page: IndexPostingPage, childIndex: Int) {
            check(depth < MAX_HEIGHT) { "Index posting BTree exceeds supported height $MAX_HEIGHT" }
            pages[depth] = page
            childIndexes[depth] = childIndex
            depth++
        }

        fun page(level: Int): IndexPostingPage = requireNotNull(pages[level])

        fun clear() {
            for (index in 0 until depth) pages[index] = null
            depth = 0
        }
    }

    private companion object {
        const val HEADER_RECORD_COUNT_OFFSET = 5L
        const val EDGE_KEYS_TO_RETAIN = 15
        const val MAX_HEIGHT = 16

        fun valueKindFor(type: Class<*>): ValueKind = when (type) {
            ClassMetadata.FLOAT_TYPE, ClassMetadata.FLOAT_PRIMITIVE_TYPE -> ValueKind.FLOAT
            ClassMetadata.DOUBLE_TYPE, ClassMetadata.DOUBLE_PRIMITIVE_TYPE -> ValueKind.DOUBLE
            Date::class.java -> ValueKind.DATE
            else -> if (type.canBeCastToPrimitive()) ValueKind.INTEGRAL else ValueKind.OBJECT
        }

        fun valueTokenWidthFor(type: Class<*>): Int = when (type) {
            ClassMetadata.BOOLEAN_TYPE, ClassMetadata.BOOLEAN_PRIMITIVE_TYPE,
            ClassMetadata.BYTE_TYPE, ClassMetadata.BYTE_PRIMITIVE_TYPE -> Byte.SIZE_BYTES

            ClassMetadata.CHAR_TYPE, ClassMetadata.CHAR_PRIMITIVE_TYPE,
            ClassMetadata.SHORT_TYPE, ClassMetadata.SHORT_PRIMITIVE_TYPE -> Short.SIZE_BYTES

            ClassMetadata.INT_TYPE, ClassMetadata.INT_PRIMITIVE_TYPE,
            ClassMetadata.FLOAT_TYPE, ClassMetadata.FLOAT_PRIMITIVE_TYPE -> Int.SIZE_BYTES

            else -> Long.SIZE_BYTES
        }

        fun signedValueTokenFor(type: Class<*>): Boolean = when (type) {
            ClassMetadata.BYTE_TYPE, ClassMetadata.BYTE_PRIMITIVE_TYPE,
            ClassMetadata.SHORT_TYPE, ClassMetadata.SHORT_PRIMITIVE_TYPE,
            ClassMetadata.INT_TYPE, ClassMetadata.INT_PRIMITIVE_TYPE,
            ClassMetadata.LONG_TYPE, ClassMetadata.LONG_PRIMITIVE_TYPE,
            Date::class.java -> true

            else -> false
        }
    }
}
