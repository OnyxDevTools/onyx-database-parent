package database.index

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import entities.index.Bar
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.SplittableRandom
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * Opt-in storage benchmark for the persistent BTree implementation.
 *
 * The default run inserts one million Bars with unique 128-character,
 * pseudo-random String identifiers. All records use one partition so the
 * primary record map and the three secondary indexes share one measurable
 * `bar<partition>.idx` file.
 *
 * Enable with:
 *
 * ONYX_BAR_BTREE_BENCHMARK=true ./gradlew :onyx-database-tests:test \
 *   --tests database.index.BarBTreeIndexSizeBenchmarkTest
 *
 * A smaller smoke run can override ONYX_BAR_BTREE_BENCHMARK_RECORDS.
 */
class BarBTreeIndexSizeBenchmarkTest {

    @Test
    fun insertOneMillionBarsAndReportIndexSize() {
        assumeTrue(
            "Set $ENABLED_ENV=true to run the one-million-record storage benchmark",
            setting(ENABLED_PROPERTY, ENABLED_ENV, "false").toBoolean()
        )

        val recordCount = setting(RECORD_COUNT_PROPERTY, RECORD_COUNT_ENV, "1000000").toInt()
        val idLength = setting(ID_LENGTH_PROPERTY, ID_LENGTH_ENV, "128").toInt()
        require(recordCount > 0) { "Record count must be positive" }
        require(idLength >= UNIQUE_SUFFIX_LENGTH) {
            "ID length must be at least $UNIQUE_SUFFIX_LENGTH characters"
        }

        val benchmarkRoot = Paths.get("build", "benchmarks").toAbsolutePath()
        val databaseDirectory = benchmarkRoot.resolve(DATABASE_DIRECTORY_NAME)
        val reportFile = benchmarkRoot.resolve(REPORT_FILE_NAME)
        prepareOutput(benchmarkRoot, databaseDirectory, reportFile)

        val random = SplittableRandom(RANDOM_SEED)
        val startedAt = System.nanoTime()
        val factory = EmbeddedPersistenceManagerFactory(databaseDirectory.toString(), addShutdownHook = false)
        var closed = false
        var insertElapsed = Duration.ZERO

        try {
            factory.initialize()
            val manager = factory.persistenceManager
            val progressInterval = maxOf(1, recordCount / 10)
            val insertStartedAt = System.nanoTime()

            var inserted = 0
            while (inserted < recordCount) {
                val batchEnd = minOf(recordCount, inserted + BATCH_SIZE)
                val batch = ArrayList<IManagedEntity>(batchEnd - inserted)
                while (inserted < batchEnd) {
                    batch.add(newBar(inserted, idLength, random))
                    inserted++
                }
                manager.saveEntities(batch)

                if (inserted == recordCount || inserted % progressInterval < BATCH_SIZE) {
                    println("Bar BTree benchmark: inserted %,d / %,d records".format(inserted, recordCount))
                }
            }
            insertElapsed = Duration.ofNanos(System.nanoTime() - insertStartedAt)

            factory.close()
            closed = true
        } finally {
            if (!closed) factory.close()
            Contexts.clear()
        }

        val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
        val entityDataFile = databaseDirectory.resolve("$ENTITY_FILE_NAME$PARTITION_VALUE")
        val entityIndexFile = databaseDirectory.resolve("$ENTITY_FILE_NAME$PARTITION_VALUE.idx")
        check(entityIndexFile.exists()) { "Expected Bar index file was not created: $entityIndexFile" }

        val physicalIndexBytes = entityIndexFile.fileSize()
        val logicalIndexBytes = readLogicalStoreSize(entityIndexFile)
        val physicalDataBytes = entityDataFile.fileSize()
        val logicalDataBytes = readLogicalStoreSize(entityDataFile)
        val bytesPerRecord = physicalIndexBytes.toDouble() / recordCount
        val recordsPerSecond = recordCount.toDouble() / (insertElapsed.toNanos().toDouble() / NANOS_PER_SECOND)

        val report = buildString {
            appendLine("Bar persistent BTree index-size benchmark")
            appendLine("records=$recordCount")
            appendLine("idLength=$idLength")
            appendLine("partition=$PARTITION_VALUE")
            appendLine("secondaryIndexes=symbol,timestamp,sequence")
            appendLine("secondaryIndexLayout=flatNativeComposite(indexValue,recordId)")
            appendLine("insertElapsed=${formatDuration(insertElapsed)}")
            appendLine("insertRecordsPerSecond=${"%.2f".format(Locale.ROOT, recordsPerSecond)}")
            appendLine("elapsed=${formatDuration(elapsed)}")
            appendLine("indexPath=$entityIndexFile")
            appendLine("indexPhysicalBytes=$physicalIndexBytes")
            appendLine("indexLogicalBytes=$logicalIndexBytes")
            appendLine("indexPhysicalSize=${formatBytes(physicalIndexBytes)}")
            appendLine("indexBytesPerRecord=${"%.2f".format(Locale.ROOT, bytesPerRecord)}")
            appendLine("dataPath=$entityDataFile")
            appendLine("dataPhysicalBytes=$physicalDataBytes")
            appendLine("dataLogicalBytes=$logicalDataBytes")
            appendLine("dataPhysicalSize=${formatBytes(physicalDataBytes)}")
            appendLine()
            appendLine("The Bar .idx measurement combines the primary record BTree and all three secondary indexes.")
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

    private fun newBar(ordinal: Int, idLength: Int, random: SplittableRandom): Bar {
        val midpoint = 50.0 + random.nextDouble() * 450.0
        val spread = 0.01 + random.nextDouble() * 5.0
        val open = midpoint - spread / 4.0
        val close = midpoint + spread / 4.0
        val volume = random.nextInt(1, 1_000_001)

        return Bar().apply {
            id = randomId(ordinal, idLength, random)
            this.close = close
            high = midpoint + spread
            low = midpoint - spread
            this.open = open
            symbol = "SYM-${random.nextInt(SYMBOL_CARDINALITY).toString().padStart(4, '0')}"
            timestamp = Date(BASE_TIMESTAMP_MILLIS + ordinal.toLong() * MILLIS_PER_MINUTE)
            underlying = PARTITION_VALUE
            this.volume = volume
            interval = "MINUTE"
            volumeWeightedPrice = (open + close) / 2.0
            sequence = ordinal.toLong()
        }
    }

    /** Produces a random payload with a deterministic suffix so IDs cannot collide. */
    private fun randomId(ordinal: Int, length: Int, random: SplittableRandom): String {
        val value = CharArray(length) {
            ID_ALPHABET[random.nextInt(ID_ALPHABET.size)]
        }
        ordinal.toString(36)
            .padStart(UNIQUE_SUFFIX_LENGTH, '0')
            .toCharArray()
            .copyInto(value, destinationOffset = length - UNIQUE_SUFFIX_LENGTH)
        return String(value)
    }

    private fun prepareOutput(root: Path, databaseDirectory: Path, reportFile: Path) {
        Files.createDirectories(root)
        if (databaseDirectory.exists()) {
            check(databaseDirectory.toFile().deleteRecursively()) {
                "Unable to remove previous benchmark database: $databaseDirectory"
            }
        }
        Files.deleteIfExists(reportFile)
    }

    /** The first eight bytes contain the store's tracked size in big-endian order. */
    private fun readLogicalStoreSize(path: Path): Long =
        DataInputStream(BufferedInputStream(Files.newInputStream(path))).use { it.readLong() }

    private fun formatDuration(duration: Duration): String =
        "%d:%02d:%02d.%03d".format(
            duration.toHours(),
            duration.toMinutesPart(),
            duration.toSecondsPart(),
            duration.toMillisPart()
        )

    private fun formatBytes(bytes: Long): String =
        "%.2f MiB".format(Locale.ROOT, bytes.toDouble() / (1024.0 * 1024.0))

    private fun setting(property: String, environment: String, default: String): String =
        System.getProperty(property) ?: System.getenv(environment) ?: default

    private companion object {
        const val ENABLED_PROPERTY = "onyx.benchmark.bar.enabled"
        const val ENABLED_ENV = "ONYX_BAR_BTREE_BENCHMARK"
        const val RECORD_COUNT_PROPERTY = "onyx.benchmark.bar.records"
        const val RECORD_COUNT_ENV = "ONYX_BAR_BTREE_BENCHMARK_RECORDS"
        const val ID_LENGTH_PROPERTY = "onyx.benchmark.bar.idLength"
        const val ID_LENGTH_ENV = "ONYX_BAR_BTREE_BENCHMARK_ID_LENGTH"

        const val DATABASE_DIRECTORY_NAME = "bar-btree-index-size.oxd"
        const val REPORT_FILE_NAME = "bar-btree-index-size.txt"
        const val ENTITY_FILE_NAME = "bar"
        const val PARTITION_VALUE = "benchmark-underlying"
        const val BATCH_SIZE = 10_000
        const val SYMBOL_CARDINALITY = 1_024
        const val UNIQUE_SUFFIX_LENGTH = 13
        const val RANDOM_SEED = 0x0B_A4_2026L
        const val BASE_TIMESTAMP_MILLIS = 1_704_067_200_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val NANOS_PER_SECOND = 1_000_000_000L

        val ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()
    }
}
