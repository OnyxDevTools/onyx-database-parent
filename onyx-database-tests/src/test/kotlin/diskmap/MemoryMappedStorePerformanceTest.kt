package diskmap

import com.onyx.diskmap.store.impl.MemoryMappedStore
import org.junit.Ignore
//import com.onyx.diskmap.store.impl.PagedFileChannel
//import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.Locale
import java.util.SplittableRandom
import kotlin.math.min

/**
 * Opt-in random I/O benchmark for [MemoryMappedStore].
 *
 * By default this creates and fully initializes an exact 1 GiB file under
 * `/media/tosborn/Expansion/test`, then measures random 4 KiB reads and durable
 * random 4 KiB writes. The scratch file is removed after the report is written.
 *
 * Enable with:
 *
 * ONYX_MEMORY_MAPPED_STORE_BENCHMARK=true ./gradlew :onyx-database-tests:test \
 *   --tests diskmap.MemoryMappedStorePerformanceTest
 *
 * File size, operation count, block size, output directory, random seed, and
 * scratch-file retention can be overridden with the
 * `ONYX_MEMORY_MAPPED_STORE_BENCHMARK_*` environment variables declared below.
 */
class MemoryMappedStorePerformanceTest {

    @Test
    @Ignore
    fun benchmarkRandomReadsAndWritesOnOneGiBStore() {
//        assumeTrue(
//            "Set $ENABLED_ENV=true to run the MemoryMappedStore benchmark",
//            setting(ENABLED_PROPERTY, ENABLED_ENV, "false").toBoolean()
//        )

        val benchmarkRoot = Paths.get(
            setting(DIRECTORY_PROPERTY, DIRECTORY_ENV, DEFAULT_DIRECTORY)
        ).toAbsolutePath()
        val fileBytes = setting(FILE_BYTES_PROPERTY, FILE_BYTES_ENV, ONE_GIB.toString()).toLong()
        val operationCount = setting(OPERATIONS_PROPERTY, OPERATIONS_ENV, "500000").toInt()
        val blockBytes = setting(BLOCK_BYTES_PROPERTY, BLOCK_BYTES_ENV, "500").toInt()
        val randomSeed = setting(SEED_PROPERTY, SEED_ENV, RANDOM_SEED.toString()).toLong()
        val keepFile = setting(KEEP_FILE_PROPERTY, KEEP_FILE_ENV, "false").toBoolean()

        require(fileBytes > STORE_HEADER_BYTES + blockBytes) {
            "File size must leave room for at least one complete block"
        }
        require(fileBytes - STORE_HEADER_BYTES <= Int.MAX_VALUE.toLong()) {
            "MemoryMappedStore.allocate accepts at most Int.MAX_VALUE bytes"
        }
        require(operationCount > 0) { "Operation count must be positive" }
        require(blockBytes >= java.lang.Long.BYTES) { "Block size must be at least 8 bytes" }

        Files.createDirectories(benchmarkRoot)
        check(Files.isDirectory(benchmarkRoot) && Files.isWritable(benchmarkRoot)) {
            "Benchmark directory is not writable: $benchmarkRoot"
        }

        val benchmarkFile = benchmarkRoot.resolve(BENCHMARK_FILE_NAME)
        val reportFile = benchmarkRoot.resolve(REPORT_FILE_NAME)
        Files.deleteIfExists(benchmarkFile)
        Files.deleteIfExists(reportFile)
        check(Files.getFileStore(benchmarkRoot).usableSpace >= fileBytes) {
            "Not enough usable space for ${formatBytes(fileBytes)} under $benchmarkRoot"
        }

        try {
            val creationNanos = createAndInitializeStore(benchmarkFile, fileBytes, randomSeed)
            check(Files.size(benchmarkFile) == fileBytes) {
                "Expected a $fileBytes-byte file, found ${Files.size(benchmarkFile)} bytes"
            }

            val positions = randomBlockPositions(
                fileBytes = fileBytes,
                blockBytes = blockBytes,
                count = operationCount,
                seed = randomSeed
            )
            val readSample = benchmarkRandomReads(benchmarkFile, positions, blockBytes)
            val writeSample = benchmarkRandomWrites(
                benchmarkFile,
                positions,
                blockBytes,
                randomSeed
            )

            val bytesPerPhase = Math.multiplyExact(operationCount.toLong(), blockBytes.toLong())
            val report = buildString {
                appendLine("MemoryMappedStore random I/O benchmark")
                appendLine("filePath=$benchmarkFile")
                appendLine("fileBytes=$fileBytes")
                appendLine("fileSize=${formatBytes(fileBytes)}")
                appendLine("blockBytes=$blockBytes")
                appendLine("operationsPerPhase=$operationCount")
                appendLine("randomSeed=$randomSeed")
                appendLine("scratchFileRetained=$keepFile")
                appendLine()
                appendLine("initializationElapsed=${formatDuration(creationNanos)}")
                appendLine("initializationMiBPerSecond=${format(rateMiB(fileBytes, creationNanos))}")
                appendLine()
                appendLine("randomReadElapsed=${formatDuration(readSample.elapsedNanos)}")
                appendLine("randomReadOperationsPerSecond=${format(rate(operationCount, readSample.elapsedNanos))}")
                appendLine("randomReadMiBPerSecond=${format(rateMiB(bytesPerPhase, readSample.elapsedNanos))}")
                appendLine("randomReadChecksum=${readSample.checksum}")
                appendLine()
                appendLine("randomWriteBufferedElapsed=${formatDuration(writeSample.bufferedNanos)}")
                appendLine("randomWriteBufferedOperationsPerSecond=${format(rate(operationCount, writeSample.bufferedNanos))}")
                appendLine("randomWriteBufferedMiBPerSecond=${format(rateMiB(bytesPerPhase, writeSample.bufferedNanos))}")
                appendLine("randomWriteFlushElapsed=${formatDuration(writeSample.flushNanos)}")
                appendLine("randomWriteDurableElapsed=${formatDuration(writeSample.durableNanos)}")
                appendLine("randomWriteDurableOperationsPerSecond=${format(rate(operationCount, writeSample.durableNanos))}")
                appendLine("randomWriteDurableMiBPerSecond=${format(rateMiB(bytesPerPhase, writeSample.durableNanos))}")
                appendLine()
                appendLine("Random offsets are block-aligned and generated before timing begins.")
                appendLine("The operating-system file cache is not cleared between phases.")
                appendLine("Write durability includes MemoryMappedStore.commit().")
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
        } finally {
            if (!keepFile) Files.deleteIfExists(benchmarkFile)
        }
    }

    private fun createAndInitializeStore(path: Path, fileBytes: Long, seed: Long): Long {
        val initializationBytes = ByteArray(
            min(INITIALIZATION_BUFFER_BYTES.toLong(), fileBytes - STORE_HEADER_BYTES).toInt()
        )
        SplittableRandom(seed).nextBytes(initializationBytes)
        val buffer = ByteBuffer.wrap(initializationBytes)
        val startedAt = System.nanoTime()
        val store = MemoryMappedStore(path.toString(), null, false)

        try {
            check(store.getFileSize() == STORE_HEADER_BYTES) {
                "A new MemoryMappedStore should contain only its $STORE_HEADER_BYTES-byte header"
            }
            val dataStart = store.allocate((fileBytes - STORE_HEADER_BYTES).toInt())
            check(dataStart == STORE_HEADER_BYTES)

            var position = dataStart
            var nextProgress = PROGRESS_INTERVAL_BYTES
            while (position < fileBytes) {
                val bytesToWrite = min(buffer.capacity().toLong(), fileBytes - position).toInt()
                buffer.clear()
                buffer.putLong(0, position)
                buffer.limit(bytesToWrite)
                store.write(buffer, position)
                position += bytesToWrite

                val initializedBytes = position - dataStart
                if (initializedBytes >= nextProgress || position == fileBytes) {
                    val percent = initializedBytes.toDouble() * 100.0 / (fileBytes - dataStart)
                    println("MemoryMappedStore benchmark initialization: ${format(percent)}%")
                    nextProgress += PROGRESS_INTERVAL_BYTES
                }
            }
            store.commit()
            return System.nanoTime() - startedAt
        } finally {
            store.close()
        }
    }

    private fun randomBlockPositions(
        fileBytes: Long,
        blockBytes: Int,
        count: Int,
        seed: Long
    ): LongArray {
        val firstPosition = alignUp(STORE_HEADER_BYTES, blockBytes)
        val blockCount = (fileBytes - firstPosition) / blockBytes
        require(blockCount > 0) { "File size must contain at least one aligned I/O block" }
        val random = SplittableRandom(seed xor POSITION_SEED_SALT)
        return LongArray(count) {
            firstPosition + random.nextLong(blockCount) * blockBytes
        }
    }

    private fun benchmarkRandomReads(
        path: Path,
        positions: LongArray,
        blockBytes: Int
    ): ReadSample {
        val store = MemoryMappedStore(path.toString(), null, false)
        val buffer = ByteBuffer.allocate(blockBytes)
        var checksum = 0L

        try {
            val startedAt = System.nanoTime()
            positions.forEach { position ->
                buffer.clear()
                store.read(buffer, position)
                checksum = java.lang.Long.rotateLeft(checksum, 1) xor buffer.getLong(0)
            }
            return ReadSample(System.nanoTime() - startedAt, checksum)
        } finally {
            store.close()
        }
    }

    private fun benchmarkRandomWrites(
        path: Path,
        positions: LongArray,
        blockBytes: Int,
        seed: Long
    ): WriteSample {
        val bytes = ByteArray(blockBytes)
        SplittableRandom(seed xor WRITE_SEED_SALT).nextBytes(bytes)
        val buffer = ByteBuffer.wrap(bytes)
        val store = MemoryMappedStore(path.toString(), null, false)

        try {
            val startedAt = System.nanoTime()
            positions.forEachIndexed { index, position ->
                buffer.clear()
                buffer.putLong(0, seed xor index.toLong())
                store.write(buffer, position)
            }
            val writesCompletedAt = System.nanoTime()
            store.commit()
            val committedAt = System.nanoTime()
            return WriteSample(
                bufferedNanos = writesCompletedAt - startedAt,
                flushNanos = committedAt - writesCompletedAt,
                durableNanos = committedAt - startedAt
            )
        } finally {
            store.close()
        }
    }

    private fun alignUp(value: Long, alignment: Int): Long =
        (value + alignment - 1) / alignment * alignment

    private fun rate(operations: Int, nanos: Long): Double =
        operations.toDouble() * NANOS_PER_SECOND / nanos

    private fun rateMiB(bytes: Long, nanos: Long): Double =
        bytes.toDouble() * NANOS_PER_SECOND / nanos / BYTES_PER_MIB

    private fun format(value: Double): String = "%.2f".format(Locale.ROOT, value)

    private fun formatBytes(bytes: Long): String =
        "%.2f MiB".format(Locale.ROOT, bytes.toDouble() / BYTES_PER_MIB)

    private fun formatDuration(nanos: Long): String {
        val duration = Duration.ofNanos(nanos)
        return "%d:%02d:%02d.%03d".format(
            duration.toHours(),
            duration.toMinutesPart(),
            duration.toSecondsPart(),
            duration.toMillisPart()
        )
    }

    private fun setting(property: String, environment: String, default: String): String =
        System.getProperty(property) ?: System.getenv(environment) ?: default

    private data class ReadSample(val elapsedNanos: Long, val checksum: Long)

    private data class WriteSample(
        val bufferedNanos: Long,
        val flushNanos: Long,
        val durableNanos: Long
    )

    private companion object {
        const val ENABLED_PROPERTY = "onyx.benchmark.memoryMappedStore.enabled"
        const val ENABLED_ENV = "ONYX_MEMORY_MAPPED_STORE_BENCHMARK"
        const val DIRECTORY_PROPERTY = "onyx.benchmark.memoryMappedStore.directory"
        const val DIRECTORY_ENV = "ONYX_MEMORY_MAPPED_STORE_BENCHMARK_DIRECTORY"
        const val FILE_BYTES_PROPERTY = "onyx.benchmark.memoryMappedStore.fileBytes"
        const val FILE_BYTES_ENV = "ONYX_MEMORY_MAPPED_STORE_BENCHMARK_FILE_BYTES"
        const val OPERATIONS_PROPERTY = "onyx.benchmark.memoryMappedStore.operations"
        const val OPERATIONS_ENV = "ONYX_MEMORY_MAPPED_STORE_BENCHMARK_OPERATIONS"
        const val BLOCK_BYTES_PROPERTY = "onyx.benchmark.memoryMappedStore.blockBytes"
        const val BLOCK_BYTES_ENV = "ONYX_MEMORY_MAPPED_STORE_BENCHMARK_BLOCK_BYTES"
        const val SEED_PROPERTY = "onyx.benchmark.memoryMappedStore.seed"
        const val SEED_ENV = "ONYX_MEMORY_MAPPED_STORE_BENCHMARK_SEED"
        const val KEEP_FILE_PROPERTY = "onyx.benchmark.memoryMappedStore.keepFile"
        const val KEEP_FILE_ENV = "ONYX_MEMORY_MAPPED_STORE_BENCHMARK_KEEP_FILE"

        const val DEFAULT_DIRECTORY = "/media/tosborn/Expansion/test"
        const val BENCHMARK_FILE_NAME = "memory-mapped-store-benchmark.db"
        const val REPORT_FILE_NAME = "memory-mapped-store-benchmark.txt"
        const val STORE_HEADER_BYTES = java.lang.Long.BYTES.toLong()
        const val ONE_GIB = 1024L * 1024 * 1024
        const val INITIALIZATION_BUFFER_BYTES = 1024 * 1024
        const val PROGRESS_INTERVAL_BYTES = 128L * 1024 * 1024
        const val RANDOM_SEED = 0x4D_4D_53_2026L
        const val POSITION_SEED_SALT = 0x50_4F_53_49_54_49_4F_4EL
        const val WRITE_SEED_SALT = 0x57_52_49_54_45L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val BYTES_PER_MIB = 1024.0 * 1024.0
    }
}
