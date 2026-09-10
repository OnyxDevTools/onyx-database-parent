package com.onyx.interactors.index.impl

import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.ValueUpdateMode
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import com.onyx.lang.map.ConcurrentLinkedHashMap
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.vector.QuantizedCosineVector
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random
import java.util.TreeMap
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Opt-in, forked benchmark; never runs as a unit test. See benchmarks/run-hnsw-save.py.
 * Measures the real index upsert/search methods with either memory or mapped DiskMap storage.
 * Comparison caches are installed before any operations; "production" leaves the shipped cache intact.
 */
object HnswSaveBenchmark {
    private const val CALIBRATION = 123L

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 6) { "Expected: cache storage rows dimensions kind measuredSaves" }
        val cache = args[0]
        val storage = args[1]
        val count = args[2].toInt()
        val dimensions = args[3].toInt()
        val kind = args[4]
        val measured = args[5].toInt()
        require(count > measured && measured > 0)
        require(cache in setOf("production", "lru", "weak"))
        require(storage in setOf("memory", "mapped"))
        require(kind in setOf("random", "clustered"))
        val random = Random(192734)
        val vectors = Array(count) {
            ByteArray(dimensions) { dimension ->
                if (kind == "clustered") {
                    ((if (dimension % 7 == 0) 70 else 20) + random.nextInt(11) - 5).toByte()
                } else (random.nextInt(255) - 127).toByte()
            }
        }
        val queries = List(64) {
            val bytes = vectors[random.nextInt(count)]
            HnswSearchQuery(CALIBRATION, bytes.map(Byte::toFloat).toFloatArray(), 10, 200) to
                QuantizedCosineVector.fromBytes(bytes)
        }
        // Separate warmup graph, followed by an untimed prefix in the measured graph.
        Storage("memory").use { warmup ->
            val graph = newGraph(warmup, cache)
            repeat(minOf(2_000, count)) { graph.upsert(recordId(it), CALIBRATION, vectors[it]) }
            repeat(512) { ordinal ->
                val (query, vector) = queries[ordinal % queries.size]
                graph.search(query, queryVector = vector)
            }
        }
        Storage(storage).use { maps ->
            val graph = newGraph(maps, cache)
            repeat(count - measured) { graph.upsert(recordId(it), CALIBRATION, vectors[it]) }
            val readsBefore = maps.nodeReads.sum()
            val writesBefore = maps.nodeWrites.sum()
            val gcBefore = gcMillis()
            val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
            val allocatedBefore = threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId())
            val elapsed = LongArray(measured)
            val begin = System.nanoTime()
            repeat(measured) { offset ->
                val ordinal = count - measured + offset
                val start = System.nanoTime()
                graph.upsert(recordId(ordinal), CALIBRATION, vectors[ordinal])
                elapsed[offset] = System.nanoTime() - start
            }
            val saveMillis = (System.nanoTime() - begin) / 1e6
            val allocated = threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocatedBefore
            val result = linkedMapOf<String, Any>(
                "cache" to cache,
                "cache_class" to nodeCache(graph).javaClass.simpleName,
                "cache_capacity" to cacheCapacity(nodeCache(graph)),
                "cache_entries_after_saves" to nodeCache(graph).size,
                "storage" to storage, "rows" to count, "dimensions" to dimensions, "kind" to kind,
                "measured_saves" to measured, "save_ms" to saveMillis,
                "save_ms_per_op" to saveMillis / measured,
                "save_p50_ms" to percentile(elapsed, 0.50), "save_p95_ms" to percentile(elapsed, 0.95),
                "node_reads_per_save" to (maps.nodeReads.sum() - readsBefore).toDouble() / measured,
                "node_writes_per_save" to (maps.nodeWrites.sum() - writesBefore).toDouble() / measured,
                "allocated_bytes_per_save" to allocated.toDouble() / measured,
                "save_gc_ms" to gcMillis() - gcBefore,
            )
            check(graph.validateGraph(CALIBRATION) == count.toLong())
            result["graph_sha256"] = graphDigest(maps)
            val expected = queries.map { (query, vector) -> graph.search(query, queryVector = vector).scores.toList() }
            val queryDigest = MessageDigest.getInstance("SHA-256")
            expected.forEach { scores -> scores.forEach { (id, score) ->
                queryDigest.update(ByteBuffer.allocate(12).putLong(id).putFloat(score).array())
            } }
            result["query_sha256"] = queryDigest.digest().toHexString()
            for (threads in listOf(1, 8)) {
                val pool = Executors.newFixedThreadPool(threads)
                try {
                    // Warm up each worker and its query path before starting the shared timer.
                    val ready = CountDownLatch(threads)
                    val start = CountDownLatch(1)
                    val searches = 4_096
                    val tasks = List(threads) { worker ->
                        pool.submit<LongArray> {
                            repeat(128) { ordinal ->
                                val queryIndex = (ordinal + worker * 7) % queries.size
                                val (query, vector) = queries[queryIndex]
                                check(graph.search(query, queryVector = vector).scores.toList() == expected[queryIndex])
                            }
                            ready.countDown()
                            check(start.await(60, TimeUnit.SECONDS))
                            LongArray(searches) { ordinal ->
                                val queryIndex = (ordinal + worker * 7) % queries.size
                                val (query, vector) = queries[queryIndex]
                                val before = System.nanoTime()
                                val scores = graph.search(query, queryVector = vector).scores.toList()
                                val nanos = System.nanoTime() - before
                                check(scores == expected[queryIndex])
                                nanos
                            }
                        }
                    }
                    check(ready.await(60, TimeUnit.SECONDS))
                    val before = System.nanoTime()
                    start.countDown()
                    val times = tasks.flatMap { it.get(60, TimeUnit.SECONDS).toList() }.toLongArray()
                    val seconds = (System.nanoTime() - before) / 1e9
                    result["search_${threads}_qps"] = searches * threads / seconds
                    result["search_${threads}_p95_ms"] = percentile(times, 0.95)
                    result["search_${threads}_operations"] = searches * threads
                } finally {
                    pool.shutdownNow()
                    check(pool.awaitTermination(10, TimeUnit.SECONDS))
                }
            }
            benchmarkCacheHits(nodeCache(graph), result)
            // Separate retention diagnostic, outside every throughput measurement, in this child JVM only.
            val hotIds = (count - 256 until count).map(::recordId)
            hotIds.forEach(graph::nodeLevel)
            System.gc()
            val beforeGcProbe = maps.nodeReads.sum()
            hotIds.forEach(graph::nodeLevel)
            result["hot_node_reads_after_gc"] = maps.nodeReads.sum() - beforeGcProbe
            println("HNSW_BENCHMARK\t" + result.entries.joinToString("\t") { "${it.key}=${it.value}" })
        }
    }

    private fun newGraph(storage: Storage, cache: String): PersistentHnswIndex {
        // Use the two-argument JVM constructor so the same harness can measure a pre-change build.
        val graph = PersistentHnswIndex::class.java.getConstructor(DiskMap::class.java, DiskMap::class.java)
            .newInstance(storage.nodes, storage.metadata)
        if (cache == "weak") {
            PersistentHnswIndex::class.java.getDeclaredField("nodeCache").apply { isAccessible = true }
                .set(graph, OptimisticLockingMap(WeakHashMap<Long, Any>()))
        } else if (cache == "lru") {
            val capacity = cacheCapacity(nodeCache(graph))
            check(capacity > 0) { "LRU comparison requires a bounded production cache" }
            PersistentHnswIndex::class.java.getDeclaredField("nodeCache").apply { isAccessible = true }
                .set(graph, ConcurrentLinkedHashMap<Long, Any>(capacity, onEvict = {}))
        }
        return graph
    }

    @Suppress("UNCHECKED_CAST")
    private fun nodeCache(graph: PersistentHnswIndex): MutableMap<Long, Any> =
        PersistentHnswIndex::class.java.getDeclaredField("nodeCache").apply { isAccessible = true }
            .get(graph) as MutableMap<Long, Any>

    private fun cacheCapacity(cache: Map<*, *>): Int = cache.javaClass.declaredFields
        .firstOrNull { it.name == "maxCapacity" || it.name == "maxCapacityValue" }
        ?.apply { isAccessible = true }?.getInt(cache) ?: 0

    private fun benchmarkCacheHits(cache: Map<Long, Any>, result: MutableMap<String, Any>) {
        // Reuse actual decoded-node cache entries. Retain boxed keys for a fair all-hit comparison,
        // including the optional weak cache. No backing reads, vector work, or graph lock here.
        val keys = cache.keys.sorted().take(4_096).toTypedArray()
        check(keys.isNotEmpty())
        result["cache_hit_working_set"] = keys.size
        for (threads in listOf(1, 8)) {
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val start = CountDownLatch(1)
            val deadline = AtomicLong()
            try {
                val tasks = List(threads) { worker -> pool.submit<Long> {
                    var cursor = worker % keys.size
                    fun batch() {
                        repeat(4_096) {
                            check(cache[keys[cursor]] != null)
                            cursor++
                            if (cursor == keys.size) cursor = 0
                        }
                    }
                    val warmUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400)
                    do { batch() } while (System.nanoTime() < warmUntil)
                    ready.countDown()
                    check(start.await(60, TimeUnit.SECONDS))
                    val stop = deadline.get()
                    var operations = 0L
                    do {
                        batch()
                        operations += 4_096
                    } while (System.nanoTime() < stop)
                    operations
                } }
                check(ready.await(60, TimeUnit.SECONDS))
                val before = System.nanoTime()
                deadline.set(before + TimeUnit.SECONDS.toNanos(2))
                start.countDown()
                val operations = tasks.sumOf { it.get(60, TimeUnit.SECONDS) }
                val seconds = (System.nanoTime() - before) / 1e9
                result["cache_hit_${threads}_ops_per_second"] = operations / seconds
            } finally {
                start.countDown()
                pool.shutdownNow()
                check(pool.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }

    private fun recordId(ordinal: Int): Long = 1000L + ordinal * 37L
    private fun gcMillis(): Long = ManagementFactory.getGarbageCollectorMXBeans().sumOf { maxOf(0, it.collectionTime) }
    private fun percentile(times: LongArray, fraction: Double): Double =
        times.sorted()[(times.size * fraction).toInt().coerceAtMost(times.lastIndex)] / 1e6

    private fun graphDigest(storage: Storage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (map in listOf(storage.nodes, storage.metadata)) {
            map.entries.sortedBy { it.key }.forEach { (id, bytes) ->
                digest.update(ByteBuffer.allocate(8).putLong(id).array())
                digest.update(bytes)
            }
        }
        return digest.digest().toHexString()
    }

    private class Storage(kind: String) : AutoCloseable {
        private val directory = if (kind == "mapped") Files.createTempDirectory("hnsw-save-benchmark-") else null
        private val factory = directory?.let {
            DefaultDiskMapFactory(it.resolve("graph").toString(), StoreType.MEMORY_MAPPED_FILE)
        }
        val nodeReads = LongAdder()
        val nodeWrites = LongAdder()
        val nodes = map("nodes", nodeReads, nodeWrites)
        val metadata = map("metadata", LongAdder(), LongAdder())

        @Suppress("UNCHECKED_CAST")
        private fun map(name: String, reads: LongAdder, writes: LongAdder): DiskMap<Long, ByteArray> {
            val disk: DiskMap<Long, ByteArray>? = factory?.getHashMap(
                Long::class.java, name, ValueUpdateMode.OVERWRITE_SAME_SIZE,
            )
            val memory = if (disk == null) TreeMap<Long, ByteArray>() else null
            return Proxy.newProxyInstance(DiskMap::class.java.classLoader, arrayOf(DiskMap::class.java)) { _, method, args ->
                when (method.name) {
                    "get" -> reads.increment()
                    "put", "remove" -> writes.increment()
                }
                if (disk != null) {
                    try {
                        method.invoke(disk, *(args ?: emptyArray()))
                    } catch (failure: InvocationTargetException) {
                        throw failure.cause ?: failure
                    }
                } else when (method.name) {
                    "get" -> memory!![args!![0] as Long]
                    "put" -> memory!!.put(args!![0] as Long, (args[1] as ByteArray).copyOf())
                    "remove" -> memory!!.remove(args!![0] as Long)
                    "entrySet" -> memory!!.entries
                    "clear" -> memory!!.clear()
                    else -> error("Unexpected DiskMap operation ${method.name}")
                }
            } as DiskMap<Long, ByteArray>
        }

        override fun close() {
            factory?.close()
            directory?.toFile()?.deleteRecursively()
        }
    }
}
