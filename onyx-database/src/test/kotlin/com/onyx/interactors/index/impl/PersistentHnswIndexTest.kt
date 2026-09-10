package com.onyx.interactors.index.impl

import com.onyx.diskmap.DiskMap
import com.onyx.lang.map.ConcurrentClockCache
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.vector.QuantizedCosineVector
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentHnswIndexTest {
    @Test
    fun `mutation working set survives cache eviction without rereading nodes`() {
        val expectedNodes = RecordingMap()
        val expectedMetadata = RecordingMap()
        val expected = PersistentHnswIndex(expectedNodes.map, expectedMetadata.map)
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, metadata.map, nodeCacheCapacity = 4)
        val cache = nodeCache(index)
        val reads = HashMap<Long, Int>()
        var evictionCount = 0
        nodes.beforeRead = { id ->
            reads[id] = (reads[id] ?: 0) + 1
            // Force eviction throughout a mutation, independent of cache access order.
            cache.clear()
            evictionCount++
        }
        fun mutate(action: (PersistentHnswIndex) -> Unit) {
            action(expected)
            cache.clear()
            reads.clear()
            nodes.writes.clear()
            metadata.writes.clear()
            action(index)
            assertTrue(reads.values.all { it == 1 }, "Eviction caused a node to be read twice in one mutation: $reads")
            assertTrue(nodes.writes.values.all { it == 1 }, "Eviction caused duplicate node writes")
            assertTrue(metadata.writes.values.all { it == 1 }, "Eviction caused duplicate metadata writes")
            assertEquals(expectedNodes.backing.keys, nodes.backing.keys)
            expectedNodes.backing.forEach { (id, bytes) -> assertContentEquals(bytes, nodes.backing[id], "Node $id") }
            assertEquals(expectedMetadata.backing.keys, metadata.backing.keys)
            expectedMetadata.backing.forEach { (id, bytes) -> assertContentEquals(bytes, metadata.backing[id], "Metadata $id") }
        }
        val random = Random(59312)
        fun vector() = ByteArray(32) { (random.nextInt(255) - 127).toByte() }
        for (id in 10_000L until 10_064L) {
            val value = vector()
            mutate { it.upsert(id, CALIBRATION, value) }
        }
        for (id in 10_000L until 10_008L) {
            val value = vector()
            mutate { it.upsert(id, CALIBRATION, value) }
        }
        for (id in 10_008L until 10_016L) mutate { it.remove(id) }
        assertTrue(evictionCount > 64, "The test must evict nodes during graph traversal")
        nodes.beforeRead = null
        assertEquals(56L, index.validateGraph(CALIBRATION))
        val query = HnswSearchQuery(CALIBRATION, vector().map(Byte::toFloat).toFloatArray(), maxCandidates = 10, efSearch = 64)
        assertEquals(expected.search(query).scores.toList(), index.search(query).scores.toList())
    }

    @Test
    fun `concurrent searches preserve exact scores while cache entries are evicted`() {
        val index = PersistentHnswIndex(RecordingMap().map, RecordingMap().map, nodeCacheCapacity = 16)
        val random = Random(19273)
        fun vector() = ByteArray(32) { (random.nextInt(255) - 127).toByte() }
        for (id in 10_000L until 10_128L) index.upsert(id, CALIBRATION, vector())
        val queries = List(8) {
            HnswSearchQuery(CALIBRATION, vector().map(Byte::toFloat).toFloatArray(), maxCandidates = 12, efSearch = 128)
        }
        val expected = queries.map { index.search(it).scores.toList() }
        val cache = nodeCache(index)
        val executor = Executors.newFixedThreadPool(9)
        val ready = CountDownLatch(9)
        val start = CountDownLatch(1)
        val stopEviction = AtomicBoolean()
        try {
            val evictions = executor.submit<Int> {
                ready.countDown()
                check(start.await(10, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                var count = 0
                do {
                    cache.clear()
                    count++
                    Thread.yield()
                } while (!stopEviction.get() && System.nanoTime() < deadline)
                count
            }
            val searches = queries.mapIndexed { ordinal, query ->
                executor.submit {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    repeat(20) {
                        assertEquals(expected[ordinal], index.search(query).scores.toList())
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            searches.forEach { it.get(30, TimeUnit.SECONDS) }
            stopEviction.set(true)
            assertTrue(evictions.get(10, TimeUnit.SECONDS) > 0)
        } finally {
            stopEviction.set(true)
            start.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
        assertEquals(128L, index.validateGraph(CALIBRATION))
    }

    @Test
    fun `bounded CLOCK gives reused nodes a second chance and reloads evicted nodes`() {
        val nodes = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, RecordingMap().map, nodeCacheCapacity = 3)
        for (id in 10_000L..10_003L) index.upsert(id, CALIBRATION, byteArrayOf(127, 1, 2))
        val cache = nodeCache(index)
        assertEquals(3, cache.size)
        cache.clear()
        val reads = ArrayList<Long>()
        nodes.beforeRead = { reads += it }
        assertNotNull(index.nodeLevel(10_000L))
        assertNotNull(index.nodeLevel(10_001L))
        assertNotNull(index.nodeLevel(10_002L))
        assertNotNull(index.nodeLevel(10_003L)) // Sweep the reference bits and evict 10_000.
        assertEquals(setOf(10_001L, 10_002L, 10_003L), cache.keys)
        assertNotNull(index.nodeLevel(10_001L)) // Give this node a second chance.
        assertNotNull(index.nodeLevel(10_000L))
        assertEquals(setOf(10_000L, 10_001L, 10_003L), cache.keys)
        assertNotNull(index.nodeLevel(10_001L))
        assertEquals(listOf(10_000L, 10_001L, 10_002L, 10_003L, 10_000L), reads)
        assertNotNull(index.nodeLevel(10_002L))
        assertEquals(listOf(10_000L, 10_001L, 10_002L, 10_003L, 10_000L, 10_002L), reads)
        assertEquals(3, cache.size)
        assertEquals(4L, index.validateGraph(CALIBRATION))
    }

    @Test
    fun `underfull neighbor selection drops missing and incompatible nodes`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val vector = byteArrayOf(127, 1, 2)
        nodes.backing[1L] = legacyNode(CALIBRATION, vector, listOf(longArrayOf(2L, 3L, 4L)))
        nodes.backing[2L] = legacyNode(CALIBRATION + 1, vector, listOf(longArrayOf()))
        // Node 3 is missing; node 4 is the only valid pre-existing neighbor.
        nodes.backing[4L] = legacyNode(CALIBRATION, vector, listOf(longArrayOf(1L)))
        metadata.backing[CALIBRATION] = metadataBytes(CALIBRATION, vector.size, 1L, 0, 2L)
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        index.upsert(5L, CALIBRATION, vector)
        assertEquals(2, index.nodeDegree(1L))
        assertEquals(3L, index.validateGraph(CALIBRATION))
    }

    @Test
    fun `coalescing preserves the legacy graph and query results through updates and removals`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        val random = Random(782347)
        fun vector() = ByteArray(688) { (random.nextInt(255) - 127).toByte() }
        fun mutation(action: () -> Unit) {
            nodes.writes.clear()
            metadata.writes.clear()
            action()
            assertTrue(nodes.writes.values.all { it == 1 }, "A graph node was persisted more than once")
            assertTrue(metadata.writes.values.all { it == 1 }, "Metadata was persisted more than once")
        }
        for (id in 1L..128L) mutation { index.upsert(id, CALIBRATION, vector()) }
        for (id in 1L..16L) mutation { index.upsert(id * 7L, CALIBRATION, vector()) }
        for (id in 1L..10L) mutation { index.remove(id * 11L) }
        for (id in 1L..10L) mutation { index.upsert(id * 11L, CALIBRATION, vector()) }
        assertEquals(128L, index.validateGraph(CALIBRATION))

        // Generated with the pre-coalescing, version-1 implementation and the same seeded operations.
        // Compare its exact persisted graph, removing only version-2's reserved padding.
        val digest = MessageDigest.getInstance("SHA-256")
        nodes.backing.toSortedMap().forEach { (id, bytes) ->
            digest.update(id.toString().toByteArray())
            digest.update(asLegacyNode(bytes))
        }
        metadata.backing.toSortedMap().forEach { (id, bytes) ->
            digest.update(id.toString().toByteArray())
            digest.update(bytes)
        }
        assertEquals(
            "f41c050ad43d557cf668ff572466890b75f05eca7249be0f488741cd11486bbc",
            digest.digest().joinToString("") { "%02x".format(it) },
        )
        val queryVector = QuantizedCosineVector.fromBytes(vector())
        val denseQuery = queryVector.toByteArray().map(Byte::toFloat).toFloatArray()
        val query = HnswSearchQuery(CALIBRATION, denseQuery, maxCandidates = 8, efSearch = 100)
        val result = index.search(
            query,
            queryVector = queryVector,
        )
        assertEquals(listOf(66L, 72L, 88L, 60L, 52L, 64L, 91L, 7L), result.scores.keys.toList())
        assertEquals(
            listOf(1033295991, 1032609295, 1032378275, 1031172800, 1031117520, 1031041620, 1030076972, 1029004941),
            result.scores.values.map(Float::toBits),
        )

        val reopened = PersistentHnswIndex(nodes.map, metadata.map)
        assertEquals(128L, reopened.validateGraph(CALIBRATION))
        assertEquals(result.scores, reopened.search(
            query,
            queryVector = queryVector,
        ).scores)
    }

    @Test
    fun `reserved node sizes stay constant while neighbor degrees grow and shrink`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        val initialSizes = HashMap<Long, Int>()
        for (id in 1L..48L) {
            index.upsert(id, CALIBRATION, byteArrayOf(127, 63, 31))
            nodes.backing.forEach { (key, bytes) ->
                assertEquals(2.toShort(), ByteBuffer.wrap(bytes).getShort(4))
                val level = assertNotNull(index.nodeLevel(key))
                val expectedSize = 4 + 2 + 8 + 4 + 3 + 4 + 4 + 32 * 8 + level * (4 + 16 * 8) + 4
                assertEquals(expectedSize, bytes.size)
                assertEquals(initialSizes.getOrPut(key) { bytes.size }, bytes.size)
            }
        }
        assertTrue(assertNotNull(index.nodeDegree(1L)) > 0)
        for (id in 48L downTo 2L) index.remove(id)
        assertEquals(0, index.nodeDegree(1L))
        assertEquals(initialSizes.getValue(1L), nodes.backing.getValue(1L).size)
        assertEquals(1L, index.validateGraph(CALIBRATION))
    }

    @Test
    fun `legacy nodes remain readable and migrate only when rewritten`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        nodes.backing[1L] = legacyNode(CALIBRATION, byteArrayOf(127, 1, 2), listOf(longArrayOf(2L)))
        nodes.backing[2L] = legacyNode(CALIBRATION, byteArrayOf(127, 1, 2), listOf(longArrayOf(1L)))
        metadata.backing[CALIBRATION] = metadataBytes(CALIBRATION, 3, 1L, 0, 2L)
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        assertEquals(2L, index.validateGraph(CALIBRATION))
        val query = HnswSearchQuery(CALIBRATION, floatArrayOf(127f, 1f, 2f), maxCandidates = 2, efSearch = 2)
        assertEquals(listOf(1L, 2L), index.search(query).scores.keys.toList())
        assertTrue(nodes.writes.isEmpty(), "Reading legacy nodes must not rewrite the index")

        index.remove(2L)
        assertEquals(2.toShort(), ByteBuffer.wrap(nodes.backing.getValue(1L)).getShort(4))
        assertEquals(0, index.nodeDegree(1L))
        assertEquals(1L, PersistentHnswIndex(nodes.map, metadata.map).validateGraph(CALIBRATION))
    }

    @Test
    fun `node codec rejects invalid padding degrees truncated frames and checksums`() {
        val nodes = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, RecordingMap().map)
        index.upsert(1L, CALIBRATION, byteArrayOf(127, 1, 2))
        val valid = nodes.backing.getValue(1L)
        val countOffset = 4 + 2 + 8 + 4 + 3 + 4
        fun reject(bytes: ByteArray) {
            val corrupt = RecordingMap()
            corrupt.backing[1L] = bytes
            assertFailsWith<IllegalArgumentException> {
                PersistentHnswIndex(corrupt.map, RecordingMap().map).nodeDegree(1L)
            }
        }
        reject(valid.copyOf().also {
            ByteBuffer.wrap(it).putLong(countOffset + 4, 99L)
            checksum(it)
        })
        reject(valid.copyOf().also {
            ByteBuffer.wrap(it).putInt(countOffset, 33)
            checksum(it)
        })
        reject(valid.copyOf(valid.size - 8).also(::checksum))
        reject(valid.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() })
        reject(legacyNode(CALIBRATION, byteArrayOf(127), listOf(longArrayOf(2L, 2L))))
        reject(legacyNode(CALIBRATION, byteArrayOf(127), listOf(longArrayOf(1L))))
    }

    @Test
    fun `failed node flush keeps metadata unpublished and invalidates old cached neighbors`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        val vector = byteArrayOf(127, 1, 2)
        index.upsert(1L, CALIBRATION, vector)
        index.upsert(2L, CALIBRATION, vector)
        assertEquals(1, index.nodeDegree(1L))
        val previousMetadata = metadata.backing.getValue(CALIBRATION).copyOf()
        metadata.writes.clear()
        nodes.beforeWrite = { id -> if (id == 2L) throw IllegalStateException("injected node write failure") }

        assertFailsWith<IllegalStateException> { index.upsert(3L, CALIBRATION, vector) }
        assertTrue(metadata.writes.isEmpty(), "Metadata must be flushed after all graph nodes")
        assertContentEquals(previousMetadata, metadata.backing.getValue(CALIBRATION))
        // Node 1 was persisted before node 2 failed. Do not return the older cached node 1.
        assertEquals(2, index.nodeDegree(1L))
        assertEquals(1, index.nodeDegree(2L))
        assertEquals(2, index.nodeDegree(3L))
        // A partial persistence failure is not an atomic rollback; recovery is a graph rebuild.
        assertFailsWith<IllegalArgumentException> { index.validateGraph(CALIBRATION) }
        nodes.beforeWrite = null
        index.clear()
        assertNull(index.nodeDegree(1L))
        index.upsert(1L, CALIBRATION, vector)
        assertEquals(1L, index.validateGraph(CALIBRATION))
    }

    @Test
    fun `failed metadata flush clears cached state and successful rebuild remains readable`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        val vector = byteArrayOf(127, 1, 2)
        index.upsert(1L, CALIBRATION, vector)
        metadata.beforeWrite = { throw IllegalStateException("injected metadata failure") }
        assertFailsWith<IllegalStateException> { index.upsert(2L, CALIBRATION, vector) }
        assertEquals(1, index.nodeDegree(1L), "Read the persisted changed node after metadata failure")
        metadata.beforeWrite = null
        index.beginRebuild()
        try {
            index.upsertDuringRebuild(5L, CALIBRATION, vector)
            index.upsertDuringRebuild(6L, CALIBRATION, vector)
        } finally {
            index.completeRebuild()
        }
        assertNull(index.nodeDegree(1L))
        assertEquals(2L, index.validateGraph(CALIBRATION))
        assertEquals(2L, PersistentHnswIndex(nodes.map, metadata.map).validateGraph(CALIBRATION))
    }

    @Test
    fun `validation failure and no-op upsert write nothing`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        val vector = byteArrayOf(127, 1, 2)
        index.upsert(1L, CALIBRATION, vector)
        nodes.writes.clear()
        metadata.writes.clear()
        index.upsert(1L, CALIBRATION, vector)
        assertFailsWith<IllegalArgumentException> { index.upsert(1L, CALIBRATION, byteArrayOf(127)) }
        assertTrue(nodes.writes.isEmpty())
        assertTrue(metadata.writes.isEmpty())
        assertEquals(1L, index.validateGraph(CALIBRATION))
    }

    @Test
    fun `rejected inputs preserve cached nodes without additional storage reads`() {
        val nodes = RecordingMap()
        val metadata = RecordingMap()
        val index = PersistentHnswIndex(nodes.map, metadata.map)
        val vector = byteArrayOf(127, 1, 2)
        index.upsert(1L, CALIBRATION, vector)
        index.upsert(2L, CALIBRATION, vector)
        nodes.writes.clear()
        metadata.writes.clear()
        var backingReads = 0
        nodes.beforeRead = { backingReads++ }

        fun reject(action: () -> Unit) {
            assertFailsWith<IllegalArgumentException> { action() }
            assertNotNull(index.nodeLevel(1L))
            assertNotNull(index.nodeLevel(2L))
            assertEquals(0, backingReads, "Rejected input must not evict cached graph nodes")
            assertTrue(nodes.writes.isEmpty())
            assertTrue(metadata.writes.isEmpty())
        }

        reject { index.upsert(0L, CALIBRATION, vector) }
        reject { index.upsert(1L, 0L, vector) }
        reject { index.upsert(1L, CALIBRATION, byteArrayOf()) }
        reject { index.upsert(1L, CALIBRATION, byteArrayOf(127)) }
        assertEquals(2L, index.validateGraph(CALIBRATION))
    }

    private class RecordingMap {
        val backing = LinkedHashMap<Long, ByteArray>()
        val writes = HashMap<Long, Int>()
        var beforeWrite: ((Long) -> Unit)? = null
        var beforeRead: ((Long) -> Unit)? = null

        // HNSW uses only MutableMap operations; keep this fake independent of storage internals.
        @Suppress("UNCHECKED_CAST")
        val map = Proxy.newProxyInstance(DiskMap::class.java.classLoader, arrayOf(DiskMap::class.java)) { _, method, args ->
            when (method.name) {
                "get" -> {
                    val key = args!![0] as Long
                    beforeRead?.invoke(key)
                    backing[key]
                }
                "put", "remove" -> {
                    val key = args!![0] as Long
                    beforeWrite?.invoke(key)
                    writes[key] = (writes[key] ?: 0) + 1
                    if (method.name == "put") backing.put(key, (args[1] as ByteArray).copyOf()) else backing.remove(key)
                }
                "entrySet" -> backing.entries
                "clear" -> backing.clear()
                else -> error("Unexpected DiskMap operation ${method.name}")
            }
        } as DiskMap<Long, ByteArray>
    }

    @Suppress("UNCHECKED_CAST")
    private fun nodeCache(index: PersistentHnswIndex): ConcurrentClockCache<Long, Any> {
        val cache = PersistentHnswIndex::class.java.getDeclaredField("nodeCache")
            .apply { isAccessible = true }.get(index)
        assertTrue(cache is ConcurrentClockCache<*, *>, "The node cache must use bounded CLOCK eviction")
        return cache as ConcurrentClockCache<Long, Any>
    }

    private fun asLegacyNode(bytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(bytes)
        assertEquals(0x4f484e44, buffer.int)
        assertEquals(2.toShort(), buffer.short)
        val calibration = buffer.long
        val vector = ByteArray(buffer.int).also(buffer::get)
        val levels = buffer.int
        val neighbors = List(levels) { layer ->
            val count = buffer.int
            LongArray(count) { buffer.long }.also {
                repeat((if (layer == 0) 32 else 16) - count) { assertEquals(0L, buffer.long) }
            }
        }
        return legacyNode(calibration, vector, neighbors)
    }

    private fun legacyNode(calibration: Long, vector: ByteArray, neighbors: List<LongArray>): ByteArray {
        val bytes = ByteArray(4 + 2 + 8 + 4 + vector.size + 4 + neighbors.sumOf { 4 + it.size * 8 } + 4)
        ByteBuffer.wrap(bytes).apply {
            putInt(0x4f484e44)
            putShort(1)
            putLong(calibration)
            putInt(vector.size)
            put(vector)
            putInt(neighbors.size)
            neighbors.forEach { values -> putInt(values.size); values.forEach(::putLong) }
        }
        checksum(bytes)
        return bytes
    }

    private fun metadataBytes(calibration: Long, dimensions: Int, entryPoint: Long, level: Int, size: Long): ByteArray {
        val bytes = ByteArray(4 + 2 + 8 + 4 + 8 + 4 + 8 + 4)
        ByteBuffer.wrap(bytes).apply {
            putInt(0x4f484d44)
            putShort(1)
            putLong(calibration)
            putInt(dimensions)
            putLong(entryPoint)
            putInt(level)
            putLong(size)
        }
        checksum(bytes)
        return bytes
    }

    private fun checksum(bytes: ByteArray) {
        val checksum = CRC32().apply { update(bytes, 0, bytes.size - 4) }.value.toInt()
        ByteBuffer.wrap(bytes).putInt(bytes.size - 4, checksum)
    }

    private companion object {
        const val CALIBRATION = 73521L
    }
}
