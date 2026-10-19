package com.onyx.diskmap.store.impl

import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MemoryMappedStoreConcurrencyTest {

    @Test
    fun `in-capacity writes share the mapping lock even at overlapping positions`() {
        val path = Files.createTempFile("onyx-mapped-concurrent-write", ".db")
        val store = MemoryMappedStore()
        val executor = Executors.newFixedThreadPool(2)

        try {
            assertTrue(store.open(path.toString()))
            store.allocate(java.lang.Long.BYTES)
            val firstPosition = store.allocate(OVERLAPPING_RANGE_SIZE)
            val secondPosition = firstPosition + OVERLAP_OFFSET
            val firstPayload = ByteArray(WRITE_SIZE) { index ->
                if (index < OVERLAP_OFFSET) FIRST_ONLY_BYTE else SHARED_BYTE
            }
            val secondPayload = ByteArray(WRITE_SIZE) { index ->
                if (index < OVERLAP_OFFSET) SHARED_BYTE else SECOND_ONLY_BYTE
            }
            val writersReady = CountDownLatch(2)
            val startWriters = CountDownLatch(1)
            val heldMappingReadLock = mappingLock(store).readLock()
            val writes = ArrayList<Future<Int>>(2)

            heldMappingReadLock.withLock {
                writes += executor.submit<Int> {
                    repeatedlyWrite(store, firstPosition, firstPayload, writersReady, startWriters)
                }
                writes += executor.submit<Int> {
                    repeatedlyWrite(store, secondPosition, secondPayload, writersReady, startWriters)
                }

                assertTrue(writersReady.await(5, TimeUnit.SECONDS))
                startWriters.countDown()
                writes.forEach {
                    assertEquals(WRITE_SIZE, it.get(5, TimeUnit.SECONDS))
                }
            }

            val stored = ByteBuffer.allocate(OVERLAPPING_RANGE_SIZE)
            store.read(stored, firstPosition)
            val expected = firstPayload.copyOf().let {
                it + secondPayload.copyOfRange(OVERLAP_OFFSET, secondPayload.size)
            }
            assertContentEquals(expected, stored.array())
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            runCatching { store.close() }
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `out-of-capacity write waits for exclusive access before remapping`() {
        val path = Files.createTempFile("onyx-mapped-concurrent-growth", ".db")
        val store = MemoryMappedStore()
        val executor = Executors.newSingleThreadExecutor()

        try {
            assertTrue(store.open(path.toString()))
            store.allocate(java.lang.Long.BYTES)
            val mappingLock = mappingLock(store)
            val currentCapacity = currentMapping(store).capacity
            val writerStarted = CountDownLatch(1)
            val heldMappingReadLock = mappingLock.readLock()
            var growthWrite: Future<Int>? = null

            heldMappingReadLock.withLock {
                growthWrite = executor.submit<Int> {
                    writerStarted.countDown()
                    store.write(ByteBuffer.wrap(byteArrayOf(GROWTH_BYTE)), currentCapacity)
                }

                assertTrue(writerStarted.await(5, TimeUnit.SECONDS))
                assertFailsWith<TimeoutException> {
                    growthWrite!!.get(100, TimeUnit.MILLISECONDS)
                }
            }

            assertEquals(1, growthWrite!!.get(5, TimeUnit.SECONDS))
            val stored = ByteBuffer.allocate(1)
            store.read(stored, currentCapacity)
            assertContentEquals(byteArrayOf(GROWTH_BYTE), stored.array())
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            runCatching { store.close() }
            Files.deleteIfExists(path)
        }
    }

    private fun repeatedlyWrite(
        store: MemoryMappedStore,
        position: Long,
        payload: ByteArray,
        writersReady: CountDownLatch,
        startWriters: CountDownLatch
    ): Int {
        writersReady.countDown()
        assertTrue(startWriters.await(5, TimeUnit.SECONDS))
        repeat(WRITE_REPETITIONS) {
            assertEquals(payload.size, store.write(ByteBuffer.wrap(payload), position))
        }
        return payload.size
    }

    private fun mappingLock(store: MemoryMappedStore): ReentrantReadWriteLock {
        val field = MemoryMappedStore::class.java.getDeclaredField("mappingLock")
        field.isAccessible = true
        return field.get(store) as ReentrantReadWriteLock
    }

    private fun currentMapping(store: MemoryMappedStore): WholeFileMapping {
        val field = MemoryMappedStore::class.java.getDeclaredField("wholeFileMapping")
        field.isAccessible = true
        return field.get(store) as WholeFileMapping
    }

    private companion object {
        const val WRITE_SIZE = 64
        const val OVERLAP_OFFSET = WRITE_SIZE / 2
        const val OVERLAPPING_RANGE_SIZE = WRITE_SIZE + OVERLAP_OFFSET
        const val WRITE_REPETITIONS = 1_000
        const val FIRST_ONLY_BYTE: Byte = 0x11
        const val SHARED_BYTE: Byte = 0x55
        const val SECOND_ONLY_BYTE: Byte = 0x22
        const val GROWTH_BYTE: Byte = 0x7f
    }
}
