package com.onyx.vector

import java.lang.management.ManagementFactory
import java.util.Random

/**
 * Opt-in benchmark of the real vector implementation; never runs as a unit test.
 * Run com.onyx.vector.QuantizedCosineBenchmark with the test runtime classpath and optional
 * dimensions (default 688). Use separate JVM forks when comparing implementations.
 */
object QuantizedCosineBenchmark {
    private const val CANDIDATE_COUNT = 1_024
    private const val CHUNK_OPERATIONS = 4_096
    private const val MEASURED_BATCHES = 7
    private const val WARMUP_NANOS = 2_000_000_000L
    private const val CALIBRATION_NANOS = 100_000_000L
    private const val BATCH_NANOS = 250_000_000L

    @Volatile
    private var comparisonSink = 0.0

    @Volatile
    private var constructionSink: Array<QuantizedCosineVector?>? = null

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size <= 1) { "Expected optional dimensions (default 688)" }
        val dimensions = args.firstOrNull()?.toInt() ?: 688
        require(dimensions in 1..QuantizedCosineVector.MAX_DIMENSIONS)
        val random = Random(348_921)
        val bytes = Array(CANDIDATE_COUNT) {
            ByteArray(dimensions) { (random.nextInt(256) - 128).toByte() }.apply {
                // Keep the smallest dimension valid without adding a special timed path.
                if (all { it == 0.toByte() }) this[0] = 1
            }
        }
        val candidates = Array(CANDIDATE_COUNT) { QuantizedCosineVector.fromBytes(bytes[it]) }
        val query = QuantizedCosineVector.fromBytes(
            ByteArray(dimensions) { (random.nextInt(255) - 127).toByte() }.apply { this[0] = 127 },
        )
        val ring = arrayOfNulls<QuantizedCosineVector>(256)
        constructionSink = ring
        val allocationBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(allocationBean.isThreadAllocatedMemorySupported) { "Thread allocation accounting is required" }
        if (!allocationBean.isThreadAllocatedMemoryEnabled) allocationBean.isThreadAllocatedMemoryEnabled = true
        val threadId = Thread.currentThread().threadId()

        // A fixed work set gives baseline and optimized forks a directly comparable score digest.
        // Timed loop checksums are consumed separately because calibration changes operation counts.
        var checksum = 1L
        for (index in candidates.indices) {
            val candidate = candidates[index]
            checksum = 31L * checksum + query.cosineSimilarity(candidate).toRawBits()
            checksum = 31L * checksum + candidate.cosineSimilarity(candidates[(index * 13 + 7) and 1_023]).toRawBits()
            check(query.cosineSimilarity(QuantizedCosineVector.fromBytes(bytes[index])) ==
                query.cosineSimilarity(candidate))
        }

        val comparisons = measure(allocationBean, threadId) { operations ->
            compare(query, candidates, operations)
        }
        val constructions = measure(allocationBean, threadId) { operations ->
            construct(bytes, ring, operations)
        }
        // Observe the retained objects' initialized state after measuring their construction.
        var retainedChecksum = 0.0
        for (vector in constructionSink!!) retainedChecksum += query.cosineSimilarity(vector!!)
        comparisonSink += retainedChecksum

        val result = linkedMapOf<String, Any>(
            "dimensions" to dimensions,
            "candidates" to CANDIDATE_COUNT,
            "measured_batches" to MEASURED_BATCHES,
            "cosine_ns_per_op" to comparisons.medianNanos,
            "cosine_allocated_bytes_per_op" to comparisons.medianAllocatedBytes,
            "cosine_operations_per_batch" to comparisons.operationsPerBatch,
            "from_bytes_ns_per_op" to constructions.medianNanos,
            "from_bytes_allocated_bytes_per_op" to constructions.medianAllocatedBytes,
            "from_bytes_operations_per_batch" to constructions.operationsPerBatch,
            "checksum" to checksum,
            "retained_checksum" to retainedChecksum,
        )
        println("COSINE_BENCHMARK\t" + result.entries.joinToString("\t") { "${it.key}=${it.value}" })
    }

    private fun compare(query: QuantizedCosineVector, candidates: Array<QuantizedCosineVector>, operations: Int) {
        var checksum = 0.0
        for (index in 0 until operations) {
            checksum += query.cosineSimilarity(candidates[(index * 13 + 7) and (CANDIDATE_COUNT - 1)])
        }
        comparisonSink = checksum
    }

    private fun construct(bytes: Array<ByteArray>, ring: Array<QuantizedCosineVector?>, operations: Int) {
        for (index in 0 until operations) {
            // Publishing every constructed object prevents scalar replacement or removal of work.
            ring[index and (ring.size - 1)] = QuantizedCosineVector.fromBytes(
                bytes[(index * 13 + 7) and (CANDIDATE_COUNT - 1)],
            )
        }
    }

    private fun measure(
        allocationBean: com.sun.management.ThreadMXBean,
        threadId: Long,
        operation: (Int) -> Unit,
    ): Measurement {
        val warmupStarted = System.nanoTime()
        do {
            operation(CHUNK_OPERATIONS)
        } while (System.nanoTime() - warmupStarted < WARMUP_NANOS)

        val calibrationStarted = System.nanoTime()
        var calibrationOperations = 0L
        do {
            operation(CHUNK_OPERATIONS)
            calibrationOperations += CHUNK_OPERATIONS
        } while (System.nanoTime() - calibrationStarted < CALIBRATION_NANOS)
        val calibrationElapsed = System.nanoTime() - calibrationStarted
        val estimatedOperations = calibrationOperations.toDouble() * BATCH_NANOS / calibrationElapsed
        // Full chunks keep candidate coverage and final retained objects identical across forks.
        val operationsPerBatch = ((estimatedOperations / CHUNK_OPERATIONS).toInt()
            .coerceIn(1, Int.MAX_VALUE / CHUNK_OPERATIONS)) * CHUNK_OPERATIONS
        val elapsed = DoubleArray(MEASURED_BATCHES)
        val allocated = DoubleArray(MEASURED_BATCHES)
        repeat(MEASURED_BATCHES) { batch ->
            val allocatedBefore = allocationBean.getThreadAllocatedBytes(threadId)
            val started = System.nanoTime()
            operation(operationsPerBatch)
            elapsed[batch] = (System.nanoTime() - started).toDouble() / operationsPerBatch
            allocated[batch] = (allocationBean.getThreadAllocatedBytes(threadId) - allocatedBefore)
                .toDouble() / operationsPerBatch
        }
        elapsed.sort()
        allocated.sort()
        return Measurement(elapsed[MEASURED_BATCHES / 2], allocated[MEASURED_BATCHES / 2], operationsPerBatch)
    }

    private data class Measurement(
        val medianNanos: Double,
        val medianAllocatedBytes: Double,
        val operationsPerBatch: Int,
    )
}
