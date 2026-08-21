package diskmap

import com.onyx.diskmap.IndexPostingMap
import com.onyx.diskmap.data.BTreePage
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.IndexPostingPage
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.impl.DiskIndexPostingMap
import com.onyx.diskmap.store.Store
import com.onyx.diskmap.store.impl.InMemoryStore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.lang.ref.WeakReference
import java.util.Date
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeIndexKeyBTreeTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun primitiveCompositePostingsStayInIndexStoreAcrossSplitsAndReopen() {
        val databasePath = temporaryFolder.root.resolve("native-long-postings").toPath()
        val duplicateCount = BTreePage.MAX_KEYS * 3 + 17
        val duplicateIds = (1L..duplicateCount.toLong()).toList()
        val postings = buildList {
            duplicateIds.forEach { add(Posting(MIDDLE_LONG_VALUE, it)) }
            add(Posting(LOWER_LONG_VALUE, 7L))
            add(Posting(LOWER_LONG_VALUE, 9L))
            add(Posting(UPPER_LONG_VALUE, 2L))
        }.shuffled(Random(29))

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Long::class.java, LONG_MAP_NAME)
                factory.commit()
                val dataBytesBeforePostings = logicalStoreSize(databasePath)

                postings.forEach { map.add(it.value, it.recordId) }

                assertEquals(postings.size.toLong(), map.longSize())
                assertEquals(duplicateIds, exactRecordIds(map, MIDDLE_LONG_VALUE))
                assertEquals(
                    duplicateIds.drop(1).dropLast(1),
                    recordIds(
                        map,
                        MIDDLE_LONG_VALUE,
                        duplicateIds.first(),
                        false,
                        MIDDLE_LONG_VALUE,
                        duplicateIds.last(),
                        false
                    )
                )
                assertEquals(
                    listOf(LOWER_LONG_VALUE, MIDDLE_LONG_VALUE, UPPER_LONG_VALUE),
                    distinctValues(map)
                )

                map.clearCache()
                assertEquals(duplicateIds, exactRecordIds(map, MIDDLE_LONG_VALUE))

                factory.commit()
                assertEquals(
                    dataBytesBeforePostings,
                    logicalStoreSize(databasePath),
                    "Primitive composite postings must allocate only in the .idx store"
                )
            } finally {
                factory.close()
            }
        }

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Long::class.java, LONG_MAP_NAME)
                val dataBytesBeforeMutation = logicalStoreSize(databasePath)

                assertEquals(postings.size.toLong(), map.longSize())
                assertEquals(duplicateIds, exactRecordIds(map, MIDDLE_LONG_VALUE))

                map.remove(MIDDLE_LONG_VALUE, duplicateIds.first())
                map.remove(MIDDLE_LONG_VALUE, duplicateIds.last())
                assertEquals(duplicateIds.drop(1).dropLast(1), exactRecordIds(map, MIDDLE_LONG_VALUE))

                factory.commit()
                assertEquals(dataBytesBeforeMutation, logicalStoreSize(databasePath))
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun dateComponentsUseInlineEpochTokensWithoutSerializingMutableDates() {
        val databasePath = temporaryFolder.root.resolve("native-date-postings").toPath()
        val dates = (-400L..400L).map { Date(it * MILLIS_PER_DAY) }
        val mutableDate = Date(MUTABLE_DATE_MILLIS)

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Date::class.java, DATE_MAP_NAME)
                factory.commit()
                val dataBytesBeforePostings = logicalStoreSize(databasePath)

                dates.shuffled(Random(31)).forEachIndexed { index, value ->
                    map.add(value, index.toLong() + 1L)
                }
                map.add(mutableDate, MUTABLE_DATE_RECORD_ID)
                mutableDate.time = MUTATED_DATE_MILLIS

                assertEquals(
                    listOf(MUTABLE_DATE_RECORD_ID),
                    exactRecordIds(map, Date(MUTABLE_DATE_MILLIS)),
                    "The page must retain an epoch token, not the caller-owned mutable Date"
                )
                assertTrue(exactRecordIds(map, Date(MUTATED_DATE_MILLIS)).isEmpty())
                assertEquals((dates + Date(MUTABLE_DATE_MILLIS)).sorted(), distinctValues(map))

                map.clearCache()
                assertEquals(listOf(MUTABLE_DATE_RECORD_ID), exactRecordIds(map, Date(MUTABLE_DATE_MILLIS)))

                factory.commit()
                assertEquals(
                    dataBytesBeforePostings,
                    logicalStoreSize(databasePath),
                    "Date composite postings must allocate only in the .idx store"
                )
            } finally {
                factory.close()
            }
        }

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Date::class.java, DATE_MAP_NAME)
                val dataBytesBeforeMutation = logicalStoreSize(databasePath)

                assertEquals(listOf(MUTABLE_DATE_RECORD_ID), exactRecordIds(map, Date(MUTABLE_DATE_MILLIS)))
                map.remove(Date(MUTABLE_DATE_MILLIS), MUTABLE_DATE_RECORD_ID)
                assertTrue(exactRecordIds(map, Date(MUTABLE_DATE_MILLIS)).isEmpty())

                factory.commit()
                assertEquals(dataBytesBeforeMutation, logicalStoreSize(databasePath))
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun objectComponentIsSerializedOncePerDistinctValueAndTokenIsReusedAfterReopen() {
        val databasePath = temporaryFolder.root.resolve("native-string-postings").toPath()
        val firstValue = "m".repeat(LARGE_STRING_LENGTH)
        val secondValue = "n".repeat(LARGE_STRING_LENGTH)
        val duplicateCount = BTreePage.MAX_KEYS * 3 + 17

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, String::class.java, STRING_MAP_NAME)
                map.add(firstValue, 1L)
                map.add("alpha", LOWER_STRING_RECORD_ID)
                map.add("omega", UPPER_STRING_RECORD_ID)
                factory.commit()
                val dataBytesAfterDistinctValues = logicalStoreSize(databasePath)

                (2L..duplicateCount.toLong()).shuffled(Random(37)).forEach { map.add(firstValue, it) }

                assertEquals((1L..duplicateCount.toLong()).toList(), exactRecordIds(map, firstValue))
                assertEquals(listOf("alpha", firstValue, "omega"), distinctValues(map))

                factory.commit()
                assertEquals(
                    dataBytesAfterDistinctValues,
                    logicalStoreSize(databasePath),
                    "Duplicate object postings must reuse one typed-component token instead of serializing each pair"
                )
            } finally {
                factory.close()
            }
        }

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, String::class.java, STRING_MAP_NAME)
                val reopenedDataBytes = logicalStoreSize(databasePath)

                map.add(firstValue, REOPENED_DUPLICATE_RECORD_ID)
                factory.commit()
                assertEquals(
                    reopenedDataBytes,
                    logicalStoreSize(databasePath),
                    "A duplicate inserted after reopen must reuse the persisted component token"
                )

                map.add(secondValue, SECOND_VALUE_FIRST_RECORD_ID)
                factory.commit()
                val dataBytesAfterNewDistinctValue = logicalStoreSize(databasePath)

                (1L..SECOND_VALUE_DUPLICATES).forEach { offset ->
                    map.add(secondValue, SECOND_VALUE_FIRST_RECORD_ID + offset)
                }
                factory.commit()
                assertEquals(
                    dataBytesAfterNewDistinctValue,
                    logicalStoreSize(databasePath),
                    "Only the first posting for a distinct object value may serialize that value"
                )

                val firstIds = exactRecordIds(map, firstValue)
                assertEquals(duplicateCount + 1, firstIds.size)
                assertTrue(REOPENED_DUPLICATE_RECORD_ID in firstIds)
                assertFalse(SECOND_VALUE_FIRST_RECORD_ID in firstIds)
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun randomizedNativePostingTreeSurvivesInternalSplitsMassDeletionAndReopen() {
        val databasePath = temporaryFolder.root.resolve("native-randomized-postings").toPath()
        val random = Random(43)
        val postings = List(RANDOM_POSTING_COUNT) { ordinal ->
            Posting(
                value = random.nextLong(RANDOM_VALUE_MIN, RANDOM_VALUE_MAX_EXCLUSIVE),
                recordId = ordinal.toLong() + 1L
            )
        }
        val expected = postings.sorted()
        val removed = postings.filterIndexed { index, _ -> index % 3 != 0 }.shuffled(Random(47))
        val remaining = (postings.toSet() - removed.toSet()).sorted()

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Long::class.java, RANDOM_MAP_NAME)
                factory.commit()
                val dataBytesBeforePostings = logicalStoreSize(databasePath)

                postings.shuffled(Random(41)).forEach { assertTrue(map.add(it.value, it.recordId)) }
                assertEquals(expected.map(Posting::recordId), allRecordIds(map))
                assertEquals(postings.size.toLong(), map.longSize())

                removed.forEach { assertTrue(map.remove(it.value, it.recordId)) }
                assertEquals(remaining.map(Posting::recordId), allRecordIds(map))
                assertEquals(remaining.size.toLong(), map.longSize())
                assertTrue(remaining.all { map.contains(it.value, it.recordId) })
                assertTrue(removed.none { map.contains(it.value, it.recordId) })

                map.clearCache()
                assertEquals(remaining.map(Posting::recordId), allRecordIds(map))

                factory.commit()
                assertEquals(dataBytesBeforePostings, logicalStoreSize(databasePath))
            } finally {
                factory.close()
            }
        }

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Long::class.java, RANDOM_MAP_NAME)
                assertEquals(remaining.size.toLong(), map.longSize())
                assertEquals(remaining.map(Posting::recordId), allRecordIds(map))
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun floatingPointComponentsPreserveJavaOrderingForSignedZeroInfinityAndNaN() {
        val databasePath = temporaryFolder.root.resolve("native-floating-postings").toPath()
        val doubleValues = listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            1.0,
            0.0,
            -0.0,
            -1.0,
            Double.NEGATIVE_INFINITY
        )
        val floatValues = listOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            1.0f,
            0.0f,
            -0.0f,
            -1.0f,
            Float.NEGATIVE_INFINITY
        )

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val doubleMap = postingMap(factory, Double::class.java, DOUBLE_MAP_NAME)
                val floatMap = postingMap(factory, Float::class.java, FLOAT_MAP_NAME)
                factory.commit()
                val dataBytesBeforePostings = logicalStoreSize(databasePath)

                doubleValues.forEachIndexed { index, value -> doubleMap.add(value, index.toLong() + 1L) }
                floatValues.forEachIndexed { index, value -> floatMap.add(value, index.toLong() + 1L) }

                assertEquals(doubleValues.sortedWith(java.lang.Double::compare), distinctValues(doubleMap))
                assertEquals(floatValues.sortedWith(java.lang.Float::compare), distinctValues(floatMap))
                doubleValues.forEachIndexed { index, value ->
                    assertEquals(listOf(index.toLong() + 1L), exactRecordIds(doubleMap, value))
                }
                floatValues.forEachIndexed { index, value ->
                    assertEquals(listOf(index.toLong() + 1L), exactRecordIds(floatMap, value))
                }

                doubleMap.clearCache()
                floatMap.clearCache()
                assertEquals(doubleValues.sortedWith(java.lang.Double::compare), distinctValues(doubleMap))
                assertEquals(floatValues.sortedWith(java.lang.Float::compare), distinctValues(floatMap))

                factory.commit()
                assertEquals(dataBytesBeforePostings, logicalStoreSize(databasePath))
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun nativeCodecNeverSerializesInlineComponentsAndSerializesEachObjectValueOnce() {
        val nodeStore = InMemoryStore(null, "native-codec-node-${System.nanoTime()}")
        val dataStore = CountingStore(InMemoryStore(null, "native-codec-data-${System.nanoTime()}"))

        try {
            assertInlineValues(nodeStore, dataStore, Long::class.java, listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE))
            assertInlineValues(nodeStore, dataStore, Int::class.java, listOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE))
            assertInlineValues(nodeStore, dataStore, Boolean::class.java, listOf(false, true))
            assertInlineValues(nodeStore, dataStore, Byte::class.java, listOf(Byte.MIN_VALUE, 0.toByte(), Byte.MAX_VALUE))
            assertInlineValues(nodeStore, dataStore, Short::class.java, listOf(Short.MIN_VALUE, 0.toShort(), Short.MAX_VALUE))
            assertInlineValues(nodeStore, dataStore, Char::class.java, listOf(Char.MIN_VALUE, 'm', Char.MAX_VALUE))
            assertInlineValues(nodeStore, dataStore, Date::class.java, listOf(Date(-1L), Date(0L), Date(1L)))
            assertInlineValues(nodeStore, dataStore, Double::class.java, listOf(-0.0, 0.0, Double.NaN))
            assertInlineValues(nodeStore, dataStore, Float::class.java, listOf(-0.0f, 0.0f, Float.NaN))
            assertEquals(0, dataStore.writeObjectCount)

            val stringMap = directPostingMap(nodeStore, dataStore, String::class.java)
            assertTrue(stringMap.add("", 1L))
            assertFalse(stringMap.add("", 1L), "An identical posting must be idempotent")
            assertTrue(stringMap.add("", 2L))
            assertTrue(stringMap.add("alpha", 3L))
            assertFalse(stringMap.add("alpha", 3L))
            assertEquals(3L, stringMap.longSize())
            assertEquals(listOf("", "alpha"), distinctValues(stringMap))
            assertEquals(2, dataStore.writeObjectCount, "Each distinct object value must be serialized exactly once")
        } finally {
            nodeStore.close()
            dataStore.close()
        }
    }

    @Test
    fun rightMergeAtAnInternalSubtreeBoundaryPropagatesTheNewMinimumAcrossReopen() {
        val databasePath = temporaryFolder.root.resolve("native-right-merge-postings").toPath()
        val removedRange = RIGHT_MERGE_REMOVE_START..RIGHT_MERGE_REMOVE_END
        val expected = (1L..RIGHT_MERGE_POSTING_COUNT.toLong()).filterNot(removedRange::contains)

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Long::class.java, RIGHT_MERGE_MAP_NAME)
                (1L..RIGHT_MERGE_POSTING_COUNT.toLong()).forEach { map.add(MIDDLE_LONG_VALUE, it) }
                removedRange.forEach { assertTrue(map.remove(MIDDLE_LONG_VALUE, it)) }

                assertEquals(expected, exactRecordIds(map, MIDDLE_LONG_VALUE))
                assertTrue(map.contains(MIDDLE_LONG_VALUE, RIGHT_MERGE_REMOVE_END + 1L))
                assertFalse(map.contains(MIDDLE_LONG_VALUE, RIGHT_MERGE_REMOVE_START))

                map.clearCache()
                assertEquals(expected, exactRecordIds(map, MIDDLE_LONG_VALUE))
            } finally {
                factory.close()
            }
        }

        DefaultDiskMapFactory(databasePath.toString()).let { factory ->
            try {
                val map = postingMap(factory, Long::class.java, RIGHT_MERGE_MAP_NAME)
                assertEquals(expected, exactRecordIds(map, MIDDLE_LONG_VALUE))
                assertTrue(map.contains(MIDDLE_LONG_VALUE, RIGHT_MERGE_REMOVE_END + 1L))
            } finally {
                factory.close()
            }
        }
    }

    private fun postingMap(
        factory: DefaultDiskMapFactory,
        valueType: Class<*>,
        name: String
    ): IndexPostingMap = factory.getIndexMap(valueType, name)

    private fun directPostingMap(
        nodeStore: Store,
        dataStore: Store,
        valueType: Class<*>
    ): IndexPostingMap {
        val header = Header().also {
            it.position = nodeStore.allocate(Header.HEADER_SIZE)
            nodeStore.write(it, it.position)
        }
        return DiskIndexPostingMap(WeakReference(nodeStore), WeakReference(dataStore), header, valueType)
    }

    private fun assertInlineValues(
        nodeStore: Store,
        dataStore: CountingStore,
        valueType: Class<*>,
        values: List<Any>
    ) {
        val map = directPostingMap(nodeStore, dataStore, valueType)
        values.forEachIndexed { index, value -> assertTrue(map.add(value, index.toLong() + 1L)) }
        assertEquals(values.size.toLong(), map.longSize())
        assertEquals(values.toSet(), distinctValues(map).toSet())
        map.clearCache()
        assertEquals(values.toSet(), distinctValues(map).toSet())
    }

    private fun exactRecordIds(map: IndexPostingMap, value: Any): List<Long> =
        recordIds(map, value, Long.MIN_VALUE, true, value, Long.MAX_VALUE, true)

    private fun allRecordIds(map: IndexPostingMap): List<Long> =
        recordIds(map, null, 0L, false, null, 0L, false)

    private fun recordIds(
        map: IndexPostingMap,
        fromValue: Any?,
        fromRecordId: Long,
        includeFrom: Boolean,
        toValue: Any?,
        toRecordId: Long,
        includeTo: Boolean
    ): List<Long> = buildList {
        map.forEachRecordIdInRange(
            fromValue,
            fromRecordId,
            includeFrom,
            toValue,
            toRecordId,
            includeTo
        ) { add(it) }
    }

    private fun distinctValues(map: IndexPostingMap): List<Any> = buildList {
        map.forEachDistinctValue { add(it) }
    }

    /** The first eight bytes contain the store's tracked logical size. */
    private fun logicalStoreSize(path: Path): Long =
        DataInputStream(BufferedInputStream(Files.newInputStream(path))).use { it.readLong() }

    private data class Posting(val value: Long, val recordId: Long) : Comparable<Posting> {
        override fun compareTo(other: Posting): Int {
            val valueComparison = value.compareTo(other.value)
            return if (valueComparison != 0) valueComparison else recordId.compareTo(other.recordId)
        }
    }

    private class CountingStore(private val delegate: Store) : Store by delegate {
        var writeObjectCount: Int = 0
            private set

        override fun writeObject(value: Any?): Long {
            writeObjectCount++
            return delegate.writeObject(value)
        }
    }

    private companion object {
        const val LONG_MAP_NAME = "native-long-postings"
        const val DATE_MAP_NAME = "native-date-postings"
        const val STRING_MAP_NAME = "native-string-postings"
        const val RANDOM_MAP_NAME = "native-randomized-postings"
        const val DOUBLE_MAP_NAME = "native-double-postings"
        const val FLOAT_MAP_NAME = "native-float-postings"
        const val RIGHT_MERGE_MAP_NAME = "native-right-merge-postings"

        const val LOWER_LONG_VALUE = 10L
        const val MIDDLE_LONG_VALUE = 20L
        const val UPPER_LONG_VALUE = 30L

        const val MILLIS_PER_DAY = 86_400_000L
        const val MUTABLE_DATE_MILLIS = 45_000L
        const val MUTATED_DATE_MILLIS = 46_000L
        const val MUTABLE_DATE_RECORD_ID = 9_999L

        const val LARGE_STRING_LENGTH = 8 * 1024
        const val LOWER_STRING_RECORD_ID = 100_001L
        const val UPPER_STRING_RECORD_ID = 100_002L
        const val REOPENED_DUPLICATE_RECORD_ID = 100_003L
        const val SECOND_VALUE_FIRST_RECORD_ID = 200_000L
        const val SECOND_VALUE_DUPLICATES = 400L

        const val RANDOM_POSTING_COUNT = 50_000
        const val RANDOM_VALUE_MIN = -500L
        const val RANDOM_VALUE_MAX_EXCLUSIVE = 501L

        // A sequential Long leaf leaves 15 keys on the new right edge, so each completed leaf is dense.
        const val POSTING_HEADER_BYTES = 32
        const val BIG_INT_BYTES = 5
        const val EDGE_KEYS_TO_RETAIN = 15
        const val LONG_LEAF_CAPACITY =
            (IndexPostingPage.PAGE_SIZE - POSTING_HEADER_BYTES) / (Long.SIZE_BYTES + BIG_INT_BYTES)
        const val LONG_INTERNAL_CAPACITY =
            (IndexPostingPage.PAGE_SIZE - POSTING_HEADER_BYTES) / (Long.SIZE_BYTES + 2 * BIG_INT_BYTES)
        const val DENSE_LONG_LEAF_KEYS = LONG_LEAF_CAPACITY + 1 - EDGE_KEYS_TO_RETAIN
        const val LONG_INTERNAL_SPLIT_MEDIAN = (LONG_INTERNAL_CAPACITY + 1) / 2

        // Force an internal split, then empty enough of the right subtree's first leaf to merge it right.
        const val RIGHT_MERGE_POSTING_COUNT =
            DENSE_LONG_LEAF_KEYS * (LONG_INTERNAL_CAPACITY + 1) + EDGE_KEYS_TO_RETAIN
        const val RIGHT_MERGE_REMOVE_START =
            (LONG_INTERNAL_SPLIT_MEDIAN + 1L) * DENSE_LONG_LEAF_KEYS + 1L
        const val RIGHT_MERGE_REMOVE_COUNT = 2 * (DENSE_LONG_LEAF_KEYS - LONG_LEAF_CAPACITY / 2) + 1
        const val RIGHT_MERGE_REMOVE_END = RIGHT_MERGE_REMOVE_START + RIGHT_MERGE_REMOVE_COUNT - 1L
    }
}
