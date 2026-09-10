package com.onyx.interactors.index.impl

import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.ValueUpdateMode
import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.vector.QuantizedCosineVector
import java.nio.file.Files
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HnswIndexStorageTest {
    @Test
    fun `mapped graph allocation tracks live nodes without reclamation checkpoints`() {
        val directory = Files.createTempDirectory("hnsw-fixed-node-growth-")
        val path = directory.resolve("graph").toString()
        var factory = DefaultDiskMapFactory(path, StoreType.MEMORY_MAPPED_FILE)
        try {
            var nodes = nodeMap(factory, "nodes")
            var metadata = nodeMap(factory, "metadata")
            var graph = PersistentHnswIndex(nodes, metadata)
            val random = Random(91309)
            val vectors = (1..1500).map { angularVector(random) }
            var firstNodePosition = 0L
            var metadataPosition = 0L
            vectors.forEachIndexed { index, vector ->
                graph.upsert(index + 1L, 123L, QuantizedCosineVector.fromDenseMaxAbsolute(vector).toByteArray())
                if (index == 0) {
                    firstNodePosition = BTreeEntry.readRecord(nodes.fileStore, nodes.getRecID(1L))
                    metadataPosition = BTreeEntry.readRecord(metadata.fileStore, metadata.getRecID(123L))
                }
            }
            assertEquals(1500L, graph.validateGraph(123L))
            assertEquals(firstNodePosition, BTreeEntry.readRecord(nodes.fileStore, nodes.getRecID(1L)))
            assertEquals(metadataPosition, BTreeEntry.readRecord(metadata.fileStore, metadata.getRecID(123L)))
            val liveBytes = nodes.values.sumOf { it.size.toLong() }
            val allocatedBytes = nodes.records.getFileSize()
            assertTrue(allocatedBytes < liveBytes * 2,
                "HNSW allocated $allocatedBytes bytes for $liveBytes bytes of live nodes")
            println("HNSW fixed-slot growth: nodes=1500 liveBytes=$liveBytes allocatedBytes=$allocatedBytes")

            val query = HnswSearchQuery(123L, vectors[500], 10, 1000)
            val quantized = QuantizedCosineVector.fromDenseMaxAbsolute(vectors[500])
            val before = graph.search(query, queryVector = quantized).scores
            factory.close()
            factory = DefaultDiskMapFactory(path, StoreType.MEMORY_MAPPED_FILE)
            nodes = nodeMap(factory, "nodes")
            metadata = nodeMap(factory, "metadata")
            graph = PersistentHnswIndex(nodes, metadata)
            assertEquals(1500L, graph.validateGraph(123L))
            assertEquals(before, graph.search(query, queryVector = quantized).scores)

            val sizeBeforeUpdates = nodes.records.getFileSize()
            repeat(20) { ordinal ->
                graph.upsert(ordinal + 1L, 123L,
                    QuantizedCosineVector.fromDenseMaxAbsolute(angularVector(random)).toByteArray())
            }
            assertEquals(1500L, graph.validateGraph(123L))
            assertEquals(sizeBeforeUpdates, nodes.records.getFileSize(), "Equal-size node updates must reuse frames")
        } finally {
            factory.close()
            directory.toFile().deleteRecursively()
        }
    }

    private fun nodeMap(factory: DefaultDiskMapFactory, name: String): DiskMap<Long, ByteArray> =
        factory.getHashMap(Long::class.java, name, ValueUpdateMode.OVERWRITE_SAME_SIZE)

    private fun angularVector(random: Random): FloatArray = FloatArray(688).also { vector ->
        repeat(344) { index ->
            val angle = (random.nextDouble() * 2 - 1) * PI / 2
            vector[2 * index] = cos(angle).toFloat()
            vector[2 * index + 1] = sin(angle).toFloat()
        }
    }
}
