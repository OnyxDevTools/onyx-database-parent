package com.onyx.diskmap.impl

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiskBTreeMapReferenceTraversalTest {
    @Test
    fun `conditional reference callback allows a concurrent writer to finish`() {
        assertWriterProgress { map, callback ->
            map.visitReferencesWhile { reference, value ->
                callback(reference, value)
                true
            }
        }
    }

    @Test
    fun `reference callback allows a concurrent writer to finish`() {
        assertWriterProgress { map, callback -> map.forEachReference(callback) }
    }

    @Test
    fun `conditional traversal resumes in order after callback triggers splits and merges`() {
        assertTraversalSurvivesMutation { map, callback ->
            map.visitReferencesWhile { reference, value ->
                callback(reference, value)
                true
            }
        }
    }

    @Test
    fun `reference traversal resumes in order after callback triggers splits and merges`() {
        assertTraversalSurvivesMutation { map, callback -> map.forEachReference(callback) }
    }

    @Test
    fun `empty maps have no callbacks and null values survive traversal`() =
        withMap<Long, Long?>(Long::class.java) { map, _ ->
            assertEquals(0, map.visitReferencesWhile { _, _ -> error("Empty map has no records") })
            map.forEachReference { _, _ -> error("Empty map has no records") }

            val expected = (1L..300L).map { if (it % 2L == 0L) null else it }
            expected.forEachIndexed { index, value -> map[index + 1L] = value }
            val conditionalValues = ArrayList<Long?>()
            assertEquals(300, map.visitReferencesWhile { _, value ->
                conditionalValues.add(value)
                true
            })
            val values = ArrayList<Long?>()
            map.forEachReference { _, value -> values.add(value) }

            assertEquals(expected, conditionalValues)
            assertEquals(expected, values)
        }

    @Test
    fun `string continuation keys survive cache eviction and tree mutation`() =
        withMap<String, Long>(String::class.java) { map, executor ->
            fun key(value: Long) = "item-${value.toString().padStart(4, '0')}"
            (1L..800L).forEach { map[key(it)] = it }

            val reader = executor.submit<List<Long>> {
                buildList {
                    map.forEachReference { reference, value ->
                        assertEquals(value, map.getWithRecID(reference))
                        add(value)
                        if (value == 200L) {
                            executor.submit {
                                (1L..199L).forEach { map.remove(key(it)) }
                                (1L..400L).forEach { map["before-$it"] = -it }
                                map.clearCache()
                            }.get(5, TimeUnit.SECONDS)
                        }
                    }
                }
            }

            assertEquals((1L..800L).toList(), reader.get(10, TimeUnit.SECONDS))
        }

    @Test
    fun `conditional traversal stops immediately and counts the rejected record`() = withMap { map, _ ->
        (1L..1_000L).forEach { map[it] = it }
        val visited = ArrayList<Long>()

        val count = map.visitReferencesWhile { reference, value ->
            assertEquals(value, map.getWithRecID(reference))
            visited.add(value)
            value < 400L
        }

        assertEquals(400, count)
        assertEquals((1L..400L).toList(), visited)
    }

    private fun assertWriterProgress(
        traverse: (DiskBTreeMap<Long, Long>, (Long, Long) -> Unit) -> Unit,
    ) = withMap { map, executor ->
        (1L..4L).forEach { map[it] = it }
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        try {
            val reader = executor.submit {
                traverse(map) { _, value ->
                    if (value == 1L) {
                        callbackEntered.countDown()
                        check(releaseCallback.await(10, TimeUnit.SECONDS))
                    }
                }
            }
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS), "Traversal never reached its callback")

            // The callback remains blocked until after put finishes, including promotion
            // of the compact four-key root. Holding the map read lock here blocks put.
            val writer = executor.submit<Long> { map.put(5L, 5L) }
            assertEquals(5L, writer.get(5, TimeUnit.SECONDS))
            assertEquals(1L, releaseCallback.count)
            assertEquals(5L, map[5L])

            releaseCallback.countDown()
            reader.get(5, TimeUnit.SECONDS)
        } finally {
            releaseCallback.countDown()
        }
    }

    private fun assertTraversalSurvivesMutation(
        traverse: (DiskBTreeMap<Long, Long>, (Long, Long) -> Unit) -> Unit,
    ) = withMap { map, executor ->
        (1L..2_500L).forEach { map[it] = it }

        val reader = executor.submit<List<Long>> {
            buildList {
                traverse(map) { reference, value ->
                    assertEquals(value, map.getWithRecID(reference))
                    add(value)
                    if (value == 1_000L) {
                        executor.submit {
                            // Only mutate keys behind the cursor: the remaining original
                            // keys must still be visited once despite changes to leaf pages.
                            (1L..999L).forEach { map.remove(it) }
                            (-1_000L..0L).forEach { map[it] = it }
                        }.get(5, TimeUnit.SECONDS)
                    }
                }
            }
        }

        assertEquals((1L..2_500L).toList(), reader.get(10, TimeUnit.SECONDS))
        assertEquals(2_502L, map.longSize())
        assertEquals(-1_000L, map[-1_000L])
    }

    private fun withMap(action: (DiskBTreeMap<Long, Long>, ExecutorService) -> Unit) =
        withMap(Long::class.java, action)

    private fun <K, V> withMap(keyType: Class<*>, action: (DiskBTreeMap<K, V>, ExecutorService) -> Unit) {
        val factory = DefaultDiskMapFactory("reference-traversal", StoreType.IN_MEMORY)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val map = factory.getHashMap<DiskBTreeMap<K, V>>(keyType, "rows")
            action(map, executor)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
            factory.close()
        }
    }
}
