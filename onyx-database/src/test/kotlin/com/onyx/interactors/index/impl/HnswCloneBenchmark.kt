package com.onyx.interactors.index.impl

import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.ValueUpdateMode
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.query.HnswSearchQuery
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import kotlin.system.measureNanoTime

/** Opt-in comparison of compression graph reconstruction and topology copying on mapped files. */
object HnswCloneBenchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        val count = args.getOrNull(0)?.toInt() ?: 5_000
        val dimensions = args.getOrNull(1)?.toInt() ?: 384
        require(count > 0 && dimensions > 0)
        val directory = Files.createTempDirectory("onyx-hnsw-clone-benchmark-")
        val random = Random(77329)
        val vectors = List(count) { ByteArray(dimensions) { (random.nextInt(255) - 127).toByte() } }
        val mapping = (0 until count).associate { oldId(it) to newId(it) }
        try {
            GraphStore(directory.resolve("source")).use { source ->
                vectors.forEachIndexed { index, vector -> source.graph.upsert(oldId(index), CALIBRATION, vector) }
                source.factory.flush()
                GraphStore(directory.resolve("rebuilt")).use { rebuilt ->
                    val rebuildMs = measureNanoTime {
                        vectors.forEachIndexed { index, vector -> rebuilt.graph.upsert(newId(index), CALIBRATION, vector) }
                        rebuilt.factory.flush()
                    } / 1e6
                    val rebuildReads = rebuilt.nodes.reads
                    val rebuildWrites = rebuilt.nodes.writes
                    check(rebuilt.graph.validateGraph(CALIBRATION) == count.toLong())
                    GraphStore(directory.resolve("copied")).use { copied ->
                        val readsBefore = source.nodes.reads
                        val copyMs = measureNanoTime {
                            copied.graph.beginRebuild()
                            try {
                                copied.graph.copyDuringRebuild(source.graph, mapping)
                            } finally {
                                copied.graph.completeRebuild()
                            }
                            copied.factory.flush()
                        } / 1e6
                        val cloneLookups = source.nodes.reads - readsBefore
                        check(copied.nodes.writes == count.toLong())
                        check(cloneLookups == 0L)
                        check(copied.graph.validateGraph(CALIBRATION) == count.toLong())
                        repeat(16) { ordinal ->
                            val query = HnswSearchQuery(CALIBRATION, vectors[ordinal % count].map(Byte::toFloat).toFloatArray(), 10, 200)
                            val expected = source.graph.search(query).scores.mapKeys { mapping.getValue(it.key) }
                            check(copied.graph.search(query).scores == expected)
                        }
                        println("HNSW_CLONE_BENCHMARK rows=$count dimensions=$dimensions rebuild_ms=$rebuildMs copy_ms=$copyMs " +
                            "speedup=${rebuildMs / copyMs} rebuild_node_reads=$rebuildReads " +
                            "rebuild_node_writes=$rebuildWrites copy_neighbor_reads=$cloneLookups " +
                            "copy_node_writes=${copied.nodes.writes}")
                    }
                }
                GraphStore(directory.resolve("copied")).use { reopened ->
                    check(reopened.graph.validateGraph(CALIBRATION) == count.toLong())
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class GraphStore(path: Path) : AutoCloseable {
        val factory = DefaultDiskMapFactory(path.toString(), StoreType.MEMORY_MAPPED_FILE)
        val nodes = CountingMap(factory.getHashMap(Long::class.java, "nodes", ValueUpdateMode.OVERWRITE_SAME_SIZE))
        private val metadata: DiskMap<Long, ByteArray> = factory.getHashMap(Long::class.java, "metadata", ValueUpdateMode.OVERWRITE_SAME_SIZE)
        val graph = PersistentHnswIndex(nodes, metadata)
        override fun close() { factory.close() }
    }

    private class CountingMap(private val delegate: DiskMap<Long, ByteArray>) : DiskMap<Long, ByteArray> by delegate {
        var reads = 0L
        var writes = 0L
        override fun get(key: Long): ByteArray? { reads++; return delegate[key] }
        override fun put(key: Long, value: ByteArray): ByteArray? { writes++; return delegate.put(key, value) }
    }

    private fun oldId(ordinal: Int) = 10_000L + ordinal * 64L
    private fun newId(ordinal: Int) = 1_000_000L + ordinal * 128L
    private const val CALIBRATION = 6754L
}
