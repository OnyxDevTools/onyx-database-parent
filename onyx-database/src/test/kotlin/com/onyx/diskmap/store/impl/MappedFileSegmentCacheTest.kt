package com.onyx.diskmap.store.impl

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MappedFileSegmentCacheTest {

    @Test
    fun `clean readers of one segment overlap`() {
        val segment = testSegment(16)
        val start = CountDownLatch(1)
        val readersInside = CountDownLatch(2)
        val releaseReaders = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val readers = (0 until 2).map {
                executor.submit {
                    start.await()
                    segment.useBuffer(markDirty = false) {
                        readersInside.countDown()
                        assertTrue(releaseReaders.await(5, TimeUnit.SECONDS))
                    }
                }
            }

            start.countDown()
            assertTrue(readersInside.await(5, TimeUnit.SECONDS))
            releaseReaders.countDown()
            readers.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            releaseReaders.countDown()
            executor.shutdownNow()
            segment.close()
        }
    }

    @Test
    fun `clean reads skip force and dirty writes force once`() {
        val forceCount = AtomicInteger()
        val closeCount = AtomicInteger()
        val cache = MappedFileSegmentCache(defaultMaxChunks = 1)
        val key = MappedFileSegmentKey(1, 0)
        val mapper = {
            MappedFileSegment(
                buffer = ByteBuffer.allocate(16),
                forceAction = { forceCount.incrementAndGet() },
                closeAction = { closeCount.incrementAndGet() }
            )
        }

        cache.withBuffer(key, mapper, markDirty = false) { it.get(0) }
        assertEquals(0, cache.dirtySize)
        cache.forceFile(key.fileId)
        assertEquals(0, forceCount.get())

        cache.withBuffer(key, mapper, markDirty = true) { it.put(0, 7) }
        assertEquals(1, cache.dirtySize)
        cache.forceFile(key.fileId)
        cache.forceFile(key.fileId)
        assertEquals(1, forceCount.get())
        assertEquals(0, cache.dirtySize)

        cache.removeFile(key.fileId)
        assertEquals(1, forceCount.get())
        assertEquals(1, closeCount.get())
    }

    @Test
    fun `insertion writes back the dirty eldest segment`() {
        val firstForceCount = AtomicInteger()
        val secondMapperCount = AtomicInteger()
        val cache = MappedFileSegmentCache(defaultMaxChunks = 1)

        cache.withBuffer(
            MappedFileSegmentKey(1, 0),
            { testSegment(16, firstForceCount) },
            markDirty = true
        ) { it.put(0, 1) }
        cache.withBuffer(
            MappedFileSegmentKey(2, 0),
            mapper = {
                secondMapperCount.incrementAndGet()
                testSegment(16)
            },
            markDirty = false
        ) { it.get(0) }

        assertEquals(1, firstForceCount.get())
        assertEquals(1, secondMapperCount.get())
        assertEquals(1, cache.size)
        cache.removeFile(2)
    }

    @Test
    fun `failed dirty writeback prevents replacement mapping`() {
        val secondMapperCount = AtomicInteger()
        val cache = MappedFileSegmentCache(defaultMaxChunks = 1)

        cache.withBuffer(
            MappedFileSegmentKey(1, 0),
            mapper = {
                MappedFileSegment(
                    ByteBuffer.allocate(16),
                    forceAction = { throw IOException("force failed") },
                    closeAction = { }
                )
            },
            markDirty = true
        ) { it.put(0, 1) }

        assertFailsWith<IOException> {
            cache.withBuffer(
                MappedFileSegmentKey(2, 0),
                mapper = {
                    secondMapperCount.incrementAndGet()
                    testSegment(16)
                },
                markDirty = false
            ) { it.get(0) }
        }
        assertEquals(0, secondMapperCount.get())
        assertEquals(0, cache.size)
        assertEquals(0L, cache.pendingSizeBytes)
    }

    @Test
    fun `same key mapping is single flight`() {
        val cache = MappedFileSegmentCache(defaultMaxChunks = 16)
        val mappingCount = AtomicInteger()
        val start = CountDownLatch(1)
        val mappingStarted = CountDownLatch(1)
        val releaseMapping = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        try {
            val futures = (0 until 8).map {
                executor.submit<Int> {
                    start.await()
                    cache.withBuffer(
                        MappedFileSegmentKey(1, 0),
                        mapper = {
                            mappingCount.incrementAndGet()
                            mappingStarted.countDown()
                            assertTrue(releaseMapping.await(5, TimeUnit.SECONDS))
                            testSegment(16)
                        },
                        markDirty = false
                    ) { buffer -> buffer.capacity() }
                }
            }

            start.countDown()
            assertTrue(mappingStarted.await(5, TimeUnit.SECONDS))
            releaseMapping.countDown()
            futures.forEach { assertEquals(16, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, mappingCount.get())
        } finally {
            releaseMapping.countDown()
            executor.shutdownNow()
            cache.removeFile(1)
        }
    }

    @Test
    fun `different keys map concurrently`() {
        val cache = MappedFileSegmentCache(defaultMaxChunks = 16)
        val mappingsStarted = CountDownLatch(2)
        val releaseMappings = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = (1..2).map { fileId ->
                executor.submit<Int> {
                    cache.withBuffer(
                        MappedFileSegmentKey(fileId, 0),
                        mapper = {
                            mappingsStarted.countDown()
                            assertTrue(releaseMappings.await(5, TimeUnit.SECONDS))
                            testSegment(16)
                        },
                        markDirty = false
                    ) { it.capacity() }
                }
            }

            assertTrue(mappingsStarted.await(5, TimeUnit.SECONDS))
            releaseMappings.countDown()
            futures.forEach { assertEquals(16, it.get(5, TimeUnit.SECONDS)) }
        } finally {
            releaseMappings.countDown()
            cache.removeFile(1)
            cache.removeFile(2)
            executor.shutdownNow()
        }
    }

    @Test
    fun `limit reduction accounts for a mapping already in progress`() {
        val cache = MappedFileSegmentCache(defaultMaxChunks = 2)
        val mappingStarted = CountDownLatch(1)
        val releaseMapping = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            cache.withBuffer(
                MappedFileSegmentKey(1, 0),
                { testSegment(16) },
                markDirty = false
            ) { it.get(0) }

            val second = executor.submit<Int> {
                cache.withBuffer(
                    MappedFileSegmentKey(2, 0),
                    mapper = {
                        mappingStarted.countDown()
                        assertTrue(releaseMapping.await(5, TimeUnit.SECONDS))
                        testSegment(16)
                    },
                    markDirty = false
                ) { it.capacity() }
            }
            assertTrue(mappingStarted.await(5, TimeUnit.SECONDS))

            cache.maxChunks = 1
            releaseMapping.countDown()

            assertEquals(16, second.get(5, TimeUnit.SECONDS))
            assertEquals(1, cache.size)
        } finally {
            releaseMapping.countDown()
            cache.removeFile(1)
            cache.removeFile(2)
            executor.shutdownNow()
        }
    }

    @Test
    fun `retirement rejects an in-flight map and all future maps`() {
        val cache = MappedFileSegmentCache(defaultMaxChunks = 16)
        val key = MappedFileSegmentKey(7, 0)
        val mappingCount = AtomicInteger()
        val closeCount = AtomicInteger()
        val mappingStarted = CountDownLatch(1)
        val releaseMapping = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val mapping = executor.submit<Int> {
                cache.withBuffer(
                    key,
                    mapper = {
                        mappingCount.incrementAndGet()
                        mappingStarted.countDown()
                        assertTrue(releaseMapping.await(5, TimeUnit.SECONDS))
                        MappedFileSegment(
                            ByteBuffer.allocate(16),
                            closeAction = { closeCount.incrementAndGet() }
                        )
                    },
                    markDirty = false
                ) { it.capacity() }
            }
            assertTrue(mappingStarted.await(5, TimeUnit.SECONDS))

            val retirement = executor.submit<List<MappedFileSegment>> {
                cache.retireFile(key.fileId)
            }
            assertTrue(awaitCondition { cache.isFileRetired(key.fileId) })
            releaseMapping.countDown()

            val failure = assertFailsWith<ExecutionException> {
                mapping.get(5, TimeUnit.SECONDS)
            }
            assertTrue(failure.cause is ClosedChannelException)
            retirement.get(5, TimeUnit.SECONDS).forEach { it.close() }

            assertEquals(1, mappingCount.get())
            assertEquals(1, closeCount.get())
            assertEquals(0, cache.size)
            assertEquals(0L, cache.pendingSizeBytes)
            assertFailsWith<ClosedChannelException> {
                cache.withBuffer(
                    key,
                    mapper = {
                        mappingCount.incrementAndGet()
                        testSegment(16)
                    },
                    markDirty = false
                ) { it.capacity() }
            }
            assertEquals(1, mappingCount.get())
        } finally {
            releaseMapping.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `detached segment remains accounted until it closes`() {
        val cache = MappedFileSegmentCache(defaultMaxChunks = 1)
        val key = MappedFileSegmentKey(11, 0)

        cache.withBuffer(key, { testSegment(16) }, markDirty = false) { it.get(0) }
        val detached = cache.detachFile(key.fileId)

        assertEquals(0, cache.size)
        assertEquals(16L, cache.pendingSizeBytes)

        detached.forEach { it.close() }
        assertEquals(0L, cache.pendingSizeBytes)
    }

    @Test
    fun `pending mappings consume the chunk ceiling before mmap`() {
        val cache = MappedFileSegmentCache(defaultMaxChunks = 1)
        val firstKey = MappedFileSegmentKey(21, 0)
        val secondKey = MappedFileSegmentKey(22, 0)
        val firstLeaseStarted = CountDownLatch(1)
        val releaseFirstLease = CountDownLatch(1)
        val secondMapperInvoked = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        var detached: List<MappedFileSegment> = emptyList()

        try {
            val firstUse = executor.submit {
                cache.withBuffer(firstKey, { testSegment(16) }, markDirty = false) {
                    firstLeaseStarted.countDown()
                    assertTrue(releaseFirstLease.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(firstLeaseStarted.await(5, TimeUnit.SECONDS))
            detached = cache.detachFile(firstKey.fileId)

            val secondUse = executor.submit<Int> {
                cache.withBuffer(
                    secondKey,
                    mapper = {
                        secondMapperInvoked.countDown()
                        testSegment(16)
                    },
                    markDirty = false
                ) { it.capacity() }
            }
            assertFalse(secondMapperInvoked.await(200, TimeUnit.MILLISECONDS))
            assertFalse(secondUse.isDone)

            val closing = executor.submit { detached.forEach { it.close() } }
            assertFalse(closing.isDone)
            releaseFirstLease.countDown()
            firstUse.get(5, TimeUnit.SECONDS)
            closing.get(5, TimeUnit.SECONDS)
            assertTrue(secondMapperInvoked.await(5, TimeUnit.SECONDS))
            assertEquals(16, secondUse.get(5, TimeUnit.SECONDS))
        } finally {
            releaseFirstLease.countDown()
            detached.forEach { it.close() }
            cache.removeFile(secondKey.fileId)
            executor.shutdownNow()
        }
    }

    private fun testSegment(size: Int, forceCount: AtomicInteger = AtomicInteger()): MappedFileSegment =
        MappedFileSegment(
            buffer = ByteBuffer.allocate(size),
            forceAction = { forceCount.incrementAndGet() },
            closeAction = { }
        )

    private fun awaitCondition(
        timeout: Long = 5,
        unit: TimeUnit = TimeUnit.SECONDS,
        condition: () -> Boolean
    ): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.yield()
        }
        return condition()
    }
}
