package database.store

import com.onyx.descriptor.DEFAULT_DATA_FILE
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.from
import entities.PerformanceEntity
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.lang.management.ManagementFactory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Date
import java.util.Locale
import java.util.SplittableRandom
import kotlin.math.ceil

/**
 * Opt-in comparison using ordinary PerformanceEntity records.
 * Adds records until their stored data exceeds 100 GB (100,000,000,000 bytes).
 * Record fields and string lengths never depend on the requested database size.
 * Run :onyx-database-tests:largeStoreBenchmark; see benchmarks/README.md.
 */
class LargeStoreBenchmarkTest {
    @Test
    fun compareRandomReadsAndUpdates() {
        assumeTrue("Run :onyx-database-tests:largeStoreBenchmark to enable this benchmark",
            setting("enabled", "false").toBooleanStrict())
        val settings = Settings.load()
        Files.createDirectories(settings.directory)
        requireSpace(settings.directory, Math.addExact(Math.multiplyExact(settings.targetBytes, 2), SPACE_RESERVE))
        val root = Files.createTempDirectory(settings.directory, if (settings.smoke) "smoke-" else "run-")
        println("Large store benchmark: $root")
        println("Target per data file: ${settings.targetBytes} bytes; fixed PerformanceEntity schema, no padding")
        val databases = listOf(StoreType.MEMORY_MAPPED_FILE, StoreType.FILE).map {
            Database(it, root.resolve("${it.name.lowercase(Locale.ROOT)}.oxd"))
        }
        val records = Records(settings.seed)
        val samples = ArrayList<Sample>()
        val timings = root.resolve("results.csv")
        val storage = root.resolve("storage.csv")
        Files.writeString(timings, "store,round,phase,operations,elapsed_ns,ops_per_second," +
            "p50_us,p95_us,p99_us,checksum,gc_count,gc_ms,finalize_ns,ops_per_second_with_finalize\n")
        Files.writeString(storage, "store,stage,actual_record_count,data_file_bytes,index_file_bytes," +
            "database_bytes,data_file_gb,growth_since_seed_bytes\n")
        var completed = false
        try {
            // Copy a closed seed so both engines start with identical records and indexes.
            // Seeding is untimed; the comparison measures reads and updates, not insertion.
            seed(databases[0], settings, records)
            copySeed(databases[0], databases[1])
            databases.forEach {
                verifySeed(it, settings, records)
                recordStorage(storage, it, "seed")
            }
            check(databases.all {
                it.recordCount == databases[0].recordCount && it.seedFileBytes == databases[0].seedFileBytes
            })
            repeat(settings.rounds) { round ->
                val random = SplittableRandom(settings.seed xor (round + 1).toLong())
                fun ids(count: Int) = LongArray(count) { random.nextLong(databases[0].recordCount) }
                val reads = ids(settings.operations)
                val writes = ids(settings.operations)
                val warmupReads = ids(settings.warmupOperations)
                val warmupWrites = ids(settings.warmupOperations)
                val order = if (round % 2 == 0) databases else databases.reversed()
                order.forEach { database ->
                    val result = runRound(database, records, round + 1, reads, writes, warmupReads, warmupWrites)
                    samples.addAll(result)
                    recordStorage(storage, database, "round-${round + 1}")
                    result.forEach {
                        Files.writeString(timings, it.csv() + "\n", StandardOpenOption.APPEND)
                        println("${it.store} round=${it.round} ${it.phase}: ${format(it.opsPerSecond)} ops/s, " +
                            "p95=${format(it.p95Micros)} us")
                    }
                }
                check(samples.filter { it.round == round + 1 && it.phase == "read" }
                    .map { it.checksum }.distinct().size == 1) { "Store read checksums differ" }
            }
            val report = report(settings, databases, samples, root)
            Files.writeString(root.resolve("report.txt"), report)
            println(report)
            completed = true
        } finally {
            if (completed && !settings.keepDatabases) {
                databases.forEach { check(it.path.toFile().deleteRecursively()) { "Cannot remove ${it.path}" } }
            } else if (!completed) {
                println("Incomplete benchmark databases retained for diagnosis: $root")
            }
        }
    }

    private fun seed(database: Database, settings: Settings, records: Records) {
        val started = System.nanoTime()
        var lastProgress = started
        withManager(database) { manager ->
            val map = primaryMap(manager)
            val initialBytes = map.records.getFileSize()
            var count = 0L
            while (map.records.getFileSize() - initialBytes < settings.targetBytes) {
                manager.saveEntity(records.entity(count, 0))
                count++
                if (count % 100_000L == 0L) {
                    val bytes = map.records.getFileSize() - initialBytes
                    // Include observed index overhead when checking room for both databases.
                    val projectedBytes = ceil(settings.targetBytes.toDouble() *
                        (map.records.getFileSize() + map.fileStore.getFileSize()) / bytes).toLong()
                    val alreadyUsed = map.records.getFileSize() + map.fileStore.getFileSize()
                    requireSpace(database.path, maxOf(0, projectedBytes * 2 - alreadyUsed) + SPACE_RESERVE)
                    val now = System.nanoTime()
                    if (now - lastProgress >= 20_000_000_000L) {
                        commitEntityData(manager)
                        println("Seeding: $count records, $bytes / ${settings.targetBytes} data bytes " +
                            "(${format(bytes.toDouble() * 100 / settings.targetBytes)}%), " +
                            "${format(count * 1e9 / (now - started))} records/s")
                        lastProgress = now
                    }
                }
            }
            commitEntityData(manager)
            database.recordCount = verifyRecordCount(manager, count)
            database.seedDataBytes = map.records.getFileSize() - initialBytes
            println("Seed complete: ${database.recordCount} records, ${database.seedDataBytes} data-file bytes")
        }
        forceFiles(database.path)
        database.preparationNanos = System.nanoTime() - started
        database.preparation = "saveEntity"
        database.seedFileBytes = Files.size(database.dataFile)
    }

    private fun copySeed(source: Database, target: Database) {
        requireSpace(source.path, databaseBytes(source.path) + SPACE_RESERVE)
        val started = System.nanoTime()
        Files.walk(source.path).use { paths ->
            paths.forEach { path ->
                if (path.fileName.toString() == "lock") return@forEach
                val destination = target.path.resolve(source.path.relativize(path))
                if (Files.isDirectory(path)) Files.createDirectories(destination) else {
                    println("Copying closed seed file: ${path.fileName}, ${Files.size(path)} bytes")
                    Files.copy(path, destination)
                    check(Files.size(path) == Files.size(destination)) { "Incomplete seed copy: $destination" }
                }
            }
        }
        forceFiles(target.path)
        target.recordCount = source.recordCount
        target.seedDataBytes = source.seedDataBytes
        target.seedFileBytes = Files.size(target.dataFile)
        target.preparation = "copy of closed MEMORY_MAPPED_FILE seed"
        target.preparationNanos = System.nanoTime() - started
    }

    private fun verifySeed(database: Database, settings: Settings, records: Records) {
        check(database.seedDataBytes >= settings.targetBytes) { "Not enough record data" }
        check(database.seedFileBytes >= settings.targetBytes) { "Seed data file is too small" }
        check(logicalBytes(database.dataFile) == database.seedFileBytes) { "Seed includes an unused mapping reservation" }
        withManager(database) { manager ->
            verifyRecordCount(manager, database.recordCount)
            val count = minOf(1024L, database.recordCount).toInt()
            repeat(count) { index ->
                val ordinal = index.toLong() * (database.recordCount - 1) / maxOf(1, count - 1)
                records.verify(find(manager, ordinal), ordinal, 0)
            }
        }
    }

    private fun runRound(
        database: Database, records: Records, round: Int,
        reads: LongArray, writes: LongArray, warmupReads: LongArray, warmupWrites: LongArray
    ): List<Sample> {
        val factory = factory(database)
        var closed = false
        val read: Sample
        val write: Sample
        val warmupRevision = round * 2L - 1
        val writeRevision = round * 2L
        try {
            factory.initialize()
            val manager = factory.persistenceManager
            checkStore(database, manager)
            warmupReads.forEach { records.verify(find(manager, it), it, database.revision(it)) }
            val expected = reads.sumOf { records.checksum(records.entity(it, database.revision(it))) }
            read = measure(database.store, round, "read", reads.size) { index ->
                val ordinal = reads[index]
                val entity = find(manager, ordinal)
                check(entity.id == ordinal + 1 && entity.longValue == database.revision(ordinal)) {
                    "Incorrect record/revision for $ordinal"
                }
                records.checksum(entity)
            }
            check(read.checksum == expected) { "Unexpected read checksum for ${database.store}" }
            warmupWrites.forEach { manager.saveEntity(records.entity(it, warmupRevision)) }
            commitEntityData(manager)
            forceFiles(database.path)
            write = measure(database.store, round, "write", writes.size) { index ->
                val entity = records.entity(writes[index], writeRevision)
                manager.saveEntity(entity)
                records.checksum(entity)
            }
            val finalizeStarted = System.nanoTime()
            factory.close()
            closed = true
            forceFiles(database.path)
            write.finalizeNanos = System.nanoTime() - finalizeStarted
        } finally {
            if (!closed) factory.close()
        }
        warmupWrites.forEach { database.revisions[it] = warmupRevision }
        writes.forEach { database.revisions[it] = writeRevision }
        val touched = (warmupWrites.asSequence() + writes.asSequence()).toSortedSet()
        withManager(database) { manager ->
            verifyRecordCount(manager, database.recordCount)
            touched.forEach { records.verify(find(manager, it), it, database.revision(it)) }
        }
        println("${database.store} round=$round: verified ${touched.size} updated records after reopening")
        return listOf(read, write)
    }

    private fun primaryMap(manager: PersistenceManager): DiskMap<Long, PerformanceEntity> {
        val descriptor = manager.context.getDescriptorForEntity(PerformanceEntity())
        return manager.context.getDataFile(descriptor).getHashMap(descriptor.identifier!!.type, PerformanceEntity::class.java.name)
    }

    private fun checkStore(database: Database, manager: PersistenceManager) {
        val map = primaryMap(manager)
        val expected = if (database.store == StoreType.FILE) "FileChannelStore" else "MemoryMappedStore"
        check(map.records.javaClass.simpleName == expected && map.fileStore.javaClass.simpleName == expected)
    }

    private fun factory(database: Database) = EmbeddedPersistenceManagerFactory(
        database.path.toString(), addShutdownHook = false
    ).apply {
        storeType = database.store
        isEnableJournaling = false
    }

    private fun <T> withManager(database: Database, action: (PersistenceManager) -> T): T {
        val factory = factory(database)
        try {
            factory.initialize()
            checkStore(database, factory.persistenceManager)
            return action(factory.persistenceManager)
        } finally {
            factory.close()
        }
    }

    private fun find(manager: PersistenceManager, ordinal: Long): PerformanceEntity =
        checkNotNull(manager.findById<PerformanceEntity>(PerformanceEntity::class.java, ordinal + 1)) { "Missing record $ordinal" }

    private fun commitEntityData(manager: PersistenceManager) {
        val context = manager.context
        context.getDataFile(context.getDescriptorForEntity(PerformanceEntity())).commit()
    }

    private fun verifyRecordCount(manager: PersistenceManager, expected: Long): Long {
        val actual = manager.from(PerformanceEntity::class).count()
        check(actual == expected) { "Database contains $actual records; expected $expected" }
        return actual
    }

    private fun recordStorage(csv: Path, database: Database, stage: String) {
        val dataBytes = Files.size(database.dataFile)
        val indexBytes = Files.size(database.path.resolve("$DEFAULT_DATA_FILE.idx"))
        val row = listOf(database.store, stage, database.recordCount, dataBytes, indexBytes,
            databaseBytes(database.path), format(dataBytes.toDouble() / GB), dataBytes - database.seedFileBytes)
        Files.writeString(csv, row.joinToString(",") + "\n", StandardOpenOption.APPEND)
        println("${database.store} $stage: actual records=${database.recordCount}, " +
            "data file=$dataBytes bytes (${format(dataBytes.toDouble() / GB)} GB), index=$indexBytes bytes")
    }

    private fun logicalBytes(path: Path): Long = DataInputStream(Files.newInputStream(path)).use { it.readLong() }

    private fun databaseBytes(path: Path): Long = Files.walk(path).use { paths ->
        paths.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
    }

    private fun requireSpace(path: Path, bytes: Long) {
        check(Files.getFileStore(path).usableSpace >= bytes) { "Need $bytes more free bytes under $path for both seeds and indexes" }
    }

    private fun forceFiles(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString() != "lock" }.forEach { path ->
                FileChannel.open(path, StandardOpenOption.WRITE).use { it.force(true) }
            }
        }
    }

    private fun measure(store: StoreType, round: Int, phase: String, count: Int, operation: (Int) -> Long): Sample {
        val latencies = LongArray(count)
        val beforeGc = gc()
        var checksum = 0L
        val started = System.nanoTime()
        repeat(count) { index ->
            val operationStarted = System.nanoTime()
            checksum += operation(index)
            latencies[index] = System.nanoTime() - operationStarted
        }
        val elapsed = System.nanoTime() - started
        val afterGc = gc()
        latencies.sort()
        fun percentile(fraction: Double) = latencies[(ceil(count * fraction).toInt() - 1).coerceAtLeast(0)] / 1e3
        return Sample(store, round, phase, count, elapsed, percentile(0.5), percentile(0.95), percentile(0.99),
            checksum, afterGc.first - beforeGc.first, afterGc.second - beforeGc.second)
    }

    private fun gc(): Pair<Long, Long> = ManagementFactory.getGarbageCollectorMXBeans().let { beans ->
        beans.sumOf { maxOf(0, it.collectionCount) } to beans.sumOf { maxOf(0, it.collectionTime) }
    }

    private fun report(settings: Settings, databases: List<Database>, samples: List<Sample>, root: Path) = buildString {
        appendLine(if (settings.smoke) "SMOKE CHECK ONLY: below the 100 GB minimum" else "Large database store benchmark")
        appendLine("runDirectory=$root")
        appendLine("java=${System.getProperty("java.runtime.version")}")
        appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("fileSystem=${Files.getFileStore(root).type()}")
        appendLine("jvmArguments=${ManagementFactory.getRuntimeMXBean().inputArguments}")
        appendLine("maxHeapBytes=${Runtime.getRuntime().maxMemory()}")
        appendLine("entity=${PerformanceEntity::class.java.name}")
        appendLine("targetDataBytesPerDatabase=${settings.targetBytes}")
        appendLine("gigabyteDefinition=1000000000 bytes")
        appendLine("databaseCount=${databases.size}")
        appendLine("operationsPerPhase=${settings.operations}")
        appendLine("warmupOperationsPerPhase=${settings.warmupOperations}")
        appendLine("rounds=${settings.rounds}")
        appendLine("randomSeed=${settings.seed}")
        appendLine("databasesRetained=${settings.keepDatabases}")
        databases.forEach {
            appendLine("${it.store}.actualRecordCount=${it.recordCount}")
            appendLine("${it.store}.seedDataBytes=${it.seedDataBytes}")
            appendLine("${it.store}.averageDataBytesPerRecord=${format(it.seedDataBytes.toDouble() / it.recordCount)}")
            appendLine("${it.store}.seedFileBytes=${it.seedFileBytes}")
            appendLine("${it.store}.finalFileBytes=${Files.size(it.dataFile)}")
            appendLine("${it.store}.finalDatabaseBytes=${databaseBytes(it.path)}")
            appendLine("${it.store}.preparation=${it.preparation}")
            appendLine("${it.store}.preparationSeconds=${format(it.preparationNanos / 1e9)}")
        }
        listOf("read", "write").forEach { phase ->
            val rates = databases.associate { database ->
                val values = samples.filter { it.store == database.store && it.phase == phase }.map { it.opsPerSecond }.sorted()
                database.store to ((values[(values.size - 1) / 2] + values[values.size / 2]) / 2)
            }
            rates.forEach { (store, rate) -> appendLine("$store.$phase.medianOpsPerSecond=${format(rate)}") }
            appendLine("$phase.mappedOverFileRatio=${format(rates.getValue(StoreType.MEMORY_MAPPED_FILE) / rates.getValue(StoreType.FILE))}")
        }
        appendLine()
        appendLine("Fixed PerformanceEntity fields: numbers, booleans, dates, an 11-character symbol, and the existing idValue index.")
        appendLine("No blob or padding. Data size increases only by inserting more distinct primary keys.")
        appendLine("Seed created once through saveEntity, closed and copied; each arm opens its own files with the asserted store implementation.")
        appendLine("One thread; identical random keys and values; execution order alternates each round.")
        appendLine("Reads use findById; writes use saveEntity on existing IDs. Timings include serialization, indexes, checks, allocation, and GC.")
        appendLine("Updates allocate replacement records; old frames can grow the file while the verified live record count stays fixed.")
        appendLine("Seeding, copying, warmup, opening, and verification are outside operation timers. Finalization is reported separately.")
        appendLine("WAL journaling is disabled. Finalization includes close/commit and FileChannel.force(true); there is no fsync per operation.")
        appendLine("OS page cache is not cleared. Both stores share one JVM. These are database operations, not raw device IOPS.")
    }

    private class Records(private val seed: Long) {
        private val symbols = Array(1024) { "SYMBOL-${it.toString().padStart(4, '0')}" }

        fun entity(ordinal: Long, revision: Long) = PerformanceEntity().apply {
            id = ordinal + 1
            idValue = ordinal + 1
            longValue = revision
            longPrimitive = ordinal xor seed
            intValue = (ordinal % Int.MAX_VALUE).toInt()
            intPrimitive = (ordinal % 1000).toInt() + 1
            stringValue = symbols[(ordinal % symbols.size).toInt()]
            dateValue = Date(BASE_TIME + ordinal * 1000)
            dateCreated = Date(BASE_TIME)
            dateUpdated = Date(BASE_TIME + revision)
            doubleValue = (ordinal % 100_000) / 100.0
            doublePrimitive = (ordinal % 10_000) / 100.0 + revision / 10.0
            doubleSample = 0.125
            dblSample = 0.25
            booleanValue = ordinal and 1L == 0L
            booleanPrimitive = revision and 1L != 0L
        }

        fun checksum(entity: PerformanceEntity): Long {
            var hash = checkNotNull(entity.id)
            hash = hash * 31 + entity.idValue
            hash = hash * 31 + checkNotNull(entity.longValue)
            hash = hash * 31 + entity.longPrimitive
            hash = hash * 31 + checkNotNull(entity.intValue)
            hash = hash * 31 + entity.intPrimitive
            hash = hash * 31 + checkNotNull(entity.stringValue).hashCode()
            hash = hash * 31 + checkNotNull(entity.dateValue).time
            hash = hash * 31 + checkNotNull(entity.dateCreated).time
            hash = hash * 31 + checkNotNull(entity.dateUpdated).time
            hash = hash * 31 + checkNotNull(entity.doubleValue).toRawBits()
            hash = hash * 31 + entity.doublePrimitive.toRawBits()
            hash = hash * 31 + entity.doubleSample.toRawBits()
            hash = hash * 31 + checkNotNull(entity.dblSample).toRawBits()
            hash = hash * 31 + checkNotNull(entity.booleanValue).hashCode()
            return hash * 31 + entity.booleanPrimitive.hashCode()
        }

        fun verify(actual: PerformanceEntity, ordinal: Long, revision: Long) {
            val expected = entity(ordinal, revision)
            check(actual.id == expected.id && actual.longValue == revision && actual.child == null)
            check(checksum(actual) == checksum(expected)) { "Record mismatch for $ordinal, revision=$revision" }
        }
    }

    private data class Database(
        val store: StoreType,
        val path: Path,
        val revisions: MutableMap<Long, Long> = HashMap(),
        var recordCount: Long = 0,
        var seedDataBytes: Long = 0,
        var seedFileBytes: Long = 0,
        var preparationNanos: Long = 0,
        var preparation: String = ""
    ) {
        val dataFile: Path get() = path.resolve(DEFAULT_DATA_FILE)
        fun revision(ordinal: Long) = revisions[ordinal] ?: 0L
    }

    private data class Sample(
        val store: StoreType, val round: Int, val phase: String, val operations: Int,
        val elapsedNanos: Long, val p50Micros: Double, val p95Micros: Double, val p99Micros: Double,
        val checksum: Long, val gcCount: Long, val gcMillis: Long, var finalizeNanos: Long = 0
    ) {
        val opsPerSecond get() = operations * 1e9 / elapsedNanos
        fun csv() = listOf(store, round, phase, operations, elapsedNanos, format(opsPerSecond),
            format(p50Micros), format(p95Micros), format(p99Micros), checksum, gcCount, gcMillis,
            finalizeNanos, format(operations * 1e9 / (elapsedNanos + finalizeNanos))).joinToString(",")
    }

    private data class Settings(
        val directory: Path, val smoke: Boolean, val targetBytes: Long, val operations: Int,
        val warmupOperations: Int, val rounds: Int, val seed: Long, val keepDatabases: Boolean
    ) {
        companion object {
            fun load(): Settings {
                require(setting("payloadBytes", "").isEmpty() && setting("dataGiB", "").isEmpty()) {
                    "Record padding was removed. Use dataGB for decimal gigabytes; record fields are fixed."
                }
                val smoke = setting("smoke", "false").toBooleanStrict()
                val gb = setting("dataGB", "100").toLong()
                require(gb >= 100) { "dataGB must be at least 100; use smoke=true for a small validation run" }
                val operations = setting("operations", if (smoke) "2000" else "50000").toInt()
                val warmup = setting("warmupOperations", if (smoke) "200" else "5000").toInt()
                val rounds = setting("rounds", "4").toInt()
                require(operations > 0 && warmup >= 0 && rounds > 0)
                return Settings(Paths.get(setting("directory", "build/benchmarks/large-store")).toAbsolutePath(),
                    smoke, if (smoke) 8L * 1024 * 1024 else Math.multiplyExact(gb, GB),
                    operations, warmup, rounds, setting("seed", "20260917").toLong(),
                    setting("keepDatabases", "false").toBooleanStrict())
            }
        }
    }

    private companion object {
        const val GB = 1_000_000_000L
        const val SPACE_RESERVE = 10 * GB
        const val BASE_TIME = 1_704_067_200_000L

        fun setting(name: String, default: String): String {
            val environmentName = when (name) {
                "dataGB" -> "DATA_GB"
                "dataGiB" -> "DATA_GIB"
                else -> name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase(Locale.ROOT)
            }
            return System.getProperty("onyx.benchmark.largeStore.$name")
                ?: System.getenv("ONYX_LARGE_STORE_BENCHMARK_$environmentName") ?: default
        }
        fun format(value: Double) = "%.3f".format(Locale.ROOT, value)
    }
}
