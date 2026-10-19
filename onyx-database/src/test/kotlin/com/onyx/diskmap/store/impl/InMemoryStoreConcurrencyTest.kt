package com.onyx.diskmap.store.impl

import java.nio.ByteBuffer
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class InMemoryStoreConcurrencyTest {
    @Test
    fun `parallel reads and disjoint writes preserve bytes across slice boundaries`() {
        val store = InMemoryStore(null, "concurrent-memory-store")
        // Include ranges crossing both the small-device and ordinary slice capacities.
        val positions = listOf(16L, 4_096L, 16_384L, 65_536L, 128 * 1_024L - 257, 256 * 1_024L,
            1_024 * 1_024L, 4 * 1_024 * 1_024L - 257)
        val payloads = positions.indices.map { index ->
            ByteArray(701 + index * 137) { offset -> (offset * 31 + index).toByte() }
        }
        val executor = Executors.newFixedThreadPool(positions.size)
        val together = CyclicBarrier(positions.size)
        try {
            positions.indices.forEach { index ->
                store.write(ByteBuffer.wrap(payloads[index]), positions[index])
            }
            val readers = positions.indices.map { index ->
                executor.submit {
                    val actual = ByteBuffer.allocate(payloads[index].size)
                    repeat(512) {
                        together.await(5, TimeUnit.SECONDS)
                        actual.clear()
                        store.read(actual, positions[index])
                        assertEquals(actual.capacity(), actual.position())
                        assertContentEquals(payloads[index], actual.array())
                    }
                }
            }
            readers.forEach { it.get(20, TimeUnit.SECONDS) }

            val writers = positions.indices.map { index ->
                executor.submit {
                    val actual = ByteBuffer.allocate(payloads[index].size)
                    repeat(512) { iteration ->
                        val original = payloads[index]
                        val expected = ByteArray(original.size) { offset ->
                            (original[offset] + iteration).toByte()
                        }
                        together.await(5, TimeUnit.SECONDS)
                        assertEquals(expected.size, store.write(ByteBuffer.wrap(expected), positions[index]))
                        actual.clear()
                        store.read(actual, positions[index])
                        assertContentEquals(expected, actual.array())
                    }
                }
            }
            writers.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
            store.close()
        }
    }
}
