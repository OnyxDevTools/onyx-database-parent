package transaction

import com.onyx.buffer.BufferPool
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * Opt-in benchmark for the WAL framing write path.
 *
 * Enable with:
 *
 * ONYX_WAL_WRITE_BENCHMARK=true ./gradlew :onyx-database-tests:test \
 *   --tests transaction.WalWriteBenchmarkTest
 *
 * Payload size, iteration count, and measured rounds can be overridden with
 * ONYX_WAL_WRITE_BENCHMARK_PAYLOAD_BYTES, ONYX_WAL_WRITE_BENCHMARK_ITERATIONS,
 * and ONYX_WAL_WRITE_BENCHMARK_ROUNDS respectively.
 */
class WalWriteBenchmarkTest {

    @Test
    fun compareCopiedFrameWithGatheringWrite() {
        assumeTrue(
            "Set $ENABLED_ENV=true to run the WAL write benchmark",
            setting(ENABLED_PROPERTY, ENABLED_ENV, "false").toBoolean()
        )

        val payloadSize = setting(PAYLOAD_SIZE_PROPERTY, PAYLOAD_SIZE_ENV, "4096").toInt()
        val iterations = setting(ITERATIONS_PROPERTY, ITERATIONS_ENV, "25000").toInt()
        val rounds = setting(ROUNDS_PROPERTY, ROUNDS_ENV, "5").toInt()
        require(payloadSize > 0) { "Payload size must be positive" }
        require(iterations > 0) { "Iteration count must be positive" }
        require(rounds > 0) { "Round count must be positive" }

        val benchmarkRoot = Paths.get("build", "benchmarks").toAbsolutePath()
        val scratchFile = benchmarkRoot.resolve("wal-write.tmp")
        val reportFile = benchmarkRoot.resolve("wal-write.txt")
        Files.createDirectories(benchmarkRoot)

        val payloadBytes = ByteArray(payloadSize) { index -> (index * 31).toByte() }
        val warmupIterations = maxOf(100, iterations / 10)
        benchmark(scratchFile, payloadBytes, warmupIterations, WriteMode.COPIED_FRAME)
        benchmark(scratchFile, payloadBytes, warmupIterations, WriteMode.GATHERING)

        val copiedSamples = ArrayList<Long>(rounds)
        val gatheringSamples = ArrayList<Long>(rounds)
        repeat(rounds) { round ->
            if (round % 2 == 0) {
                copiedSamples += benchmark(scratchFile, payloadBytes, iterations, WriteMode.COPIED_FRAME)
                gatheringSamples += benchmark(scratchFile, payloadBytes, iterations, WriteMode.GATHERING)
            } else {
                gatheringSamples += benchmark(scratchFile, payloadBytes, iterations, WriteMode.GATHERING)
                copiedSamples += benchmark(scratchFile, payloadBytes, iterations, WriteMode.COPIED_FRAME)
            }
        }
        Files.deleteIfExists(scratchFile)

        val copiedMedianNanos = copiedSamples.median()
        val gatheringMedianNanos = gatheringSamples.median()
        val copiedRecordsPerSecond = recordsPerSecond(iterations, copiedMedianNanos)
        val gatheringRecordsPerSecond = recordsPerSecond(iterations, gatheringMedianNanos)
        val speedup = gatheringRecordsPerSecond / copiedRecordsPerSecond
        val copiedBytesAvoided = iterations.toLong() * payloadSize

        val report = buildString {
            appendLine("WAL framing write benchmark")
            appendLine("payloadBytes=$payloadSize")
            appendLine("iterationsPerRound=$iterations")
            appendLine("rounds=$rounds")
            appendLine("copiedFrameMedianNanos=$copiedMedianNanos")
            appendLine("gatheringMedianNanos=$gatheringMedianNanos")
            appendLine("copiedFrameRecordsPerSecond=${format(copiedRecordsPerSecond)}")
            appendLine("gatheringRecordsPerSecond=${format(gatheringRecordsPerSecond)}")
            appendLine("gatheringSpeedup=${format(speedup)}x")
            appendLine("payloadCopyBytesAvoidedPerRecord=$payloadSize")
            appendLine("payloadCopyBytesAvoidedPerRound=$copiedBytesAvoided")
            appendLine()
            appendLine("Both modes append to a real FileChannel without forcing each record, matching WAL write semantics.")
            appendLine("The benchmark isolates framing and file writes; object serialization is intentionally excluded.")
        }
        Files.writeString(
            reportFile,
            report,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        println(report)
        println("Benchmark report: $reportFile")
    }

    private fun benchmark(
        scratchFile: Path,
        payloadBytes: ByteArray,
        iterations: Int,
        mode: WriteMode
    ): Long {
        FileChannel.open(
            scratchFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).use { channel ->
            val payload = ByteBuffer.wrap(payloadBytes)
            val metadata = ByteBuffer.allocate(WAL_HEADER_SIZE)
            val gatheringBuffers = arrayOf(metadata, payload)
            val startedAt = System.nanoTime()
            repeat(iterations) {
                payload.rewind()
                when (mode) {
                    WriteMode.COPIED_FRAME -> writeCopiedFrame(channel, payload)
                    WriteMode.GATHERING -> writeGatheredFrame(channel, gatheringBuffers)
                }
            }
            return System.nanoTime() - startedAt
        }
    }

    private fun writeCopiedFrame(channel: FileChannel, payload: ByteBuffer) {
        BufferPool.allocateAndLimit(WAL_HEADER_SIZE + payload.limit()) { transactionBuffer ->
            transactionBuffer.put(TRANSACTION_TYPE)
            transactionBuffer.putInt(payload.limit())
            transactionBuffer.put(payload)
            transactionBuffer.flip()
            while (transactionBuffer.hasRemaining()) {
                channel.write(transactionBuffer)
            }
        }
    }

    private fun writeGatheredFrame(channel: FileChannel, buffers: Array<ByteBuffer>) {
        val metadata = buffers[0]
        val payload = buffers[1]
        metadata.clear()
        metadata.put(TRANSACTION_TYPE)
        metadata.putInt(payload.limit())
        metadata.flip()
        while (metadata.hasRemaining() || payload.hasRemaining()) {
            channel.write(buffers)
        }
    }

    private fun recordsPerSecond(iterations: Int, nanos: Long): Double =
        iterations.toDouble() * NANOS_PER_SECOND / nanos

    private fun List<Long>.median(): Long = sorted()[size / 2]

    private fun setting(property: String, environment: String, default: String): String =
        System.getProperty(property) ?: System.getenv(environment) ?: default

    private fun format(value: Double): String = "%.2f".format(Locale.ROOT, value)

    private enum class WriteMode {
        COPIED_FRAME,
        GATHERING
    }

    private companion object {
        const val ENABLED_PROPERTY = "onyx.benchmark.wal.enabled"
        const val ENABLED_ENV = "ONYX_WAL_WRITE_BENCHMARK"
        const val PAYLOAD_SIZE_PROPERTY = "onyx.benchmark.wal.payloadBytes"
        const val PAYLOAD_SIZE_ENV = "ONYX_WAL_WRITE_BENCHMARK_PAYLOAD_BYTES"
        const val ITERATIONS_PROPERTY = "onyx.benchmark.wal.iterations"
        const val ITERATIONS_ENV = "ONYX_WAL_WRITE_BENCHMARK_ITERATIONS"
        const val ROUNDS_PROPERTY = "onyx.benchmark.wal.rounds"
        const val ROUNDS_ENV = "ONYX_WAL_WRITE_BENCHMARK_ROUNDS"

        const val WAL_HEADER_SIZE = 5
        const val TRANSACTION_TYPE: Byte = 3
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
