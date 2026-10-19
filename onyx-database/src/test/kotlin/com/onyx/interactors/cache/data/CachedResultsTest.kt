package com.onyx.interactors.cache.data

import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.query.QueryListener
import com.onyx.persistence.query.QueryListenerEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.StreamSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachedResultsTest {
    private val first = Reference(1, 30)
    private val second = Reference(1, 10)
    private val otherPartition = Reference(2, 30)

    @Test
    fun `cache owns initial and replacement collections`() {
        val initial = linkedSetOf(first, second)
        val cached = CachedResults(initial)
        initial.clear()
        assertEquals(listOf(first, second), cached.references!!.toList())

        val replacement = linkedSetOf(otherPartition, first)
        cached.references = replacement
        replacement.clear()
        assertEquals(listOf(otherPartition, first), cached.references!!.toList())
    }

    @Test
    fun `unpopulated and empty caches remain distinct`() {
        val cached = CachedResults()
        assertNull(cached.references)
        cached.replaceReferences(emptyList())
        assertTrue(assertNotNull(cached.references).isEmpty())
        cached.references = null
        assertNull(cached.references)
    }

    @Test
    fun `membership preserves arrival order and distinguishes partitions`() {
        val cached = CachedResults().apply {
            replaceReferences(listOf(first, second, first.copy(), otherPartition))
        }
        val references = cached.references!!
        assertEquals(listOf(first, second, otherPartition), references.toList())
        assertFalse(references.add(second.copy()))
        assertTrue(references.remove(first.copy()))
        assertTrue(references.add(first.copy()))
        assertEquals(listOf(second, otherPartition, first), references.toList())
    }

    @Test
    fun `iterators and streams retain their snapshot after mutations`() {
        val references = CachedResults(linkedSetOf(first, second)).references!!
        val iterator = references.iterator()
        val spliterator = references.spliterator()
        references.remove(first)
        references.add(otherPartition)

        assertEquals(listOf(first, second), iterator.asSequence().toList())
        assertEquals(listOf(first, second), StreamSupport.stream(spliterator, false).toList())
        assertEquals(listOf(second, otherPartition), references.toList())

        val readOnly = references.iterator()
        readOnly.next()
        assertFailsWith<UnsupportedOperationException> { readOnly.remove() }
    }

    @Test
    fun `bulk mutations update the live set without iterator removal`() {
        val references = CachedResults(linkedSetOf(first, second)).references!!
        assertTrue(references.addAll(listOf(first, otherPartition)))
        assertEquals(listOf(first, second, otherPartition), references.toList())
        assertTrue(references.removeAll(listOf(second.copy())))
        assertTrue(references.retainAll(listOf(otherPartition)))
        assertEquals(listOf(otherPartition), references.toList())
        references.addAll(listOf(first, second))
        assertTrue(references.removeIf { it.partition == 1L })
        assertEquals(listOf(otherPartition), references.toList())
        references.clear()
        assertTrue(references.isEmpty())
    }

    @Test
    fun `concurrent readers can iterate while writers change membership`() {
        val references = CachedResults(linkedSetOf()).references!!
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val writers = (1L..2L).map { partition ->
                executor.submit {
                    start.await()
                    for (record in 1L..2000L) {
                        val reference = Reference(partition, record)
                        assertTrue(references.add(reference))
                        if (record % 2L == 0L) assertTrue(references.remove(reference))
                    }
                }
            }
            val readers = (0 until 2).map {
                executor.submit {
                    start.await()
                    repeat(200) {
                        val snapshot = references.toList()
                        assertEquals(snapshot.size, snapshot.toSet().size)
                        val streamSnapshot = references.stream().toList()
                        assertEquals(streamSnapshot.size, streamSnapshot.toSet().size)
                    }
                }
            }
            start.countDown()
            (writers + readers).forEach { it.get(20, TimeUnit.SECONDS) }

            val expected = (1L..2L).flatMap { partition ->
                (1L..2000L step 2).map { Reference(partition, it) }
            }.toSet()
            assertEquals(expected, references)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `listener callbacks do not hold the listener set lock`() {
        val cached = CachedResults(linkedSetOf())
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val callbackFinished = CountDownLatch(1)
        val listener = object : QueryListener<Any> {
            override fun onItemUpdated(item: Any) = Unit

            override fun onItemAdded(item: Any) {
                callbackStarted.countDown()
                try {
                    releaseCallback.await(10, TimeUnit.SECONDS)
                } finally {
                    callbackFinished.countDown()
                }
            }

            override fun onItemRemoved(item: Any) = Unit
        }

        cached.subscribe(listener)
        cached.put(first, Any(), QueryListenerEvent.INSERT)
        assertTrue(callbackStarted.await(10, TimeUnit.SECONDS))

        val executor = Executors.newSingleThreadExecutor()
        try {
            val unsubscribe = executor.submit<Boolean> { cached.unSubscribe(listener) }
            assertTrue(unsubscribe.get(2, TimeUnit.SECONDS))
        } finally {
            releaseCallback.countDown()
            executor.shutdownNow()
        }
        assertTrue(callbackFinished.await(10, TimeUnit.SECONDS))
    }
}
