package com.onyx.diskmap.impl

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrimaryKeyReferencePageTest {
    @Test
    fun `count callback allows writes while preserving its captured first page`() {
        val factory = DefaultDiskMapFactory("primary-page-lock", StoreType.IN_MEMORY)
        val executor = Executors.newFixedThreadPool(2)
        val releaseCount = CountDownLatch(1)
        try {
            val map = factory.getHashMap<DiskBTreeMap<Long, Long>>(Long::class.java, "rows")
            (1L..10L).forEach { map[it] = it }
            val counted = CountDownLatch(1)
            val page = executor.submit<Pair<Long, List<Long>>> {
                var count = -1L
                val values = ArrayList<Long>()
                map.visitAscendingReferencePage(0, 3, {
                    count = it
                    counted.countDown()
                    check(releaseCount.await(10, TimeUnit.SECONDS))
                }) { reference -> values.add(map.getWithRecID(reference)!!) }
                count to values
            }
            assertTrue(counted.await(10, TimeUnit.SECONDS))
            val writer = executor.submit {
                map[0L] = 0L
            }
            writer.get(5, TimeUnit.SECONDS)
            assertEquals(1L, releaseCount.count)
            releaseCount.countDown()
            assertEquals(10L to listOf(1L, 2L, 3L), page.get(10, TimeUnit.SECONDS))
            val next = ArrayList<Long>()
            map.visitAscendingReferencePage(0, 3, { assertEquals(11L, it) }) {
                next.add(map.getWithRecID(it)!!)
            }
            assertEquals(listOf(0L, 1L, 2L), next)
        } finally {
            releaseCount.countDown()
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
            factory.close()
        }
    }

    @Test
    fun `page visitor allows writes and continues after the original offset`() {
        val factory = DefaultDiskMapFactory("primary-page-visitor", StoreType.IN_MEMORY)
        val executor = Executors.newFixedThreadPool(2)
        val releaseVisitor = CountDownLatch(1)
        try {
            val map = factory.getHashMap<DiskBTreeMap<Long, Long>>(Long::class.java, "rows")
            (1L..1_000L).forEach { map[it] = it }
            val visiting = CountDownLatch(1)
            val page = executor.submit<List<Long>> {
                buildList {
                    map.visitAscendingReferencePage(500, 300, { assertEquals(1_000L, it) }) { reference ->
                        val value = map.getWithRecID(reference)!!
                        add(value)
                        if (value == 501L) {
                            visiting.countDown()
                            check(releaseVisitor.await(10, TimeUnit.SECONDS))
                        }
                    }
                }
            }
            assertTrue(visiting.await(5, TimeUnit.SECONDS))
            val writer = executor.submit {
                (1L..250L).forEach { map.remove(it) }
                map[0L] = 0L
            }
            writer.get(5, TimeUnit.SECONDS)
            assertEquals(1L, releaseVisitor.count)

            releaseVisitor.countDown()
            assertEquals((501L..800L).toList(), page.get(10, TimeUnit.SECONDS))
        } finally {
            releaseVisitor.countDown()
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
            factory.close()
        }
    }

    @Test
    fun `rejecting the count does not visit records and releases the lock`() {
        val factory = DefaultDiskMapFactory("primary-page-rejection", StoreType.IN_MEMORY)
        try {
            val map = factory.getHashMap<DiskBTreeMap<Long, Long>>(Long::class.java, "rows")
            map[1L] = 1L
            assertFailsWith<IllegalStateException> {
                map.visitAscendingReferencePage(0, 1, { error("Count rejected") }) {
                    throw AssertionError("A rejected page must not load records")
                }
            }
            map[2L] = 2L
            assertEquals(2L, map.longSize())
        } finally {
            factory.close()
        }
    }
}
