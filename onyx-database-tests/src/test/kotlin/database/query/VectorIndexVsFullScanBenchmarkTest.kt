package database.query

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.store.StoreType
import com.onyx.extension.reference
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.search
import com.onyx.vector.SemanticVectorSignature
import com.onyx.vector.VectorManagedConfiguration
import entities.VectorQueryBenchmarkEntity
import entities.VectorQueryBenchmarkState
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.SplittableRandom
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in benchmark comparing the vector index with an authoritative full-table scan.
 *
 * Both arms query the same physical records through [DefaultQueryInteractor], with independently
 * created but identical queries. The only execution difference is scanner selection: automatic
 * vector routing or a forced full-table scan. Scalar workloads use deterministic ID order; search
 * workloads retain the public API's implicit relevance order.
 *
 * A focused 100k-record scale run (the complete search matrix is intentionally expensive):
 *
 * ```text
 * ONYX_VECTOR_QUERY_BENCHMARK=true \
 * ONYX_VECTOR_QUERY_BENCHMARK_RECORD_COUNTS=100000 \
 * ONYX_VECTOR_QUERY_BENCHMARK_STORES=MEMORY_MAPPED_FILE \
 * ONYX_VECTOR_QUERY_BENCHMARK_WORKLOADS=int_equal_one_row,date_between_1pct,string_equal_0_1pct,compound_or_0_2pct,lexical_search_rare_1pct \
 * ./gradlew --no-daemon :onyx-database-tests:test \
 *   --tests database.query.VectorIndexVsFullScanBenchmarkTest --rerun-tasks
 * ```
 *
 * This test fails on result or routing differences, never on a timing threshold.
 */
class VectorIndexVsFullScanBenchmarkTest {

    @Test
    fun compareVectorIndexWithFullTableScan() {
        assumeTrue(
            "Set $ENABLED_ENV=true to run the vector query benchmark",
            setting(ENABLED_PROPERTY, ENABLED_ENV, "false").toBoolean()
        )

        val settings = BenchmarkSettings.load()
        val benchmarkRoot = Paths.get("build", "benchmarks").toAbsolutePath()
        Files.createDirectories(benchmarkRoot)
        val runRoot = Files.createTempDirectory(benchmarkRoot, RUN_DIRECTORY_PREFIX)
        val reportFile = runRoot.resolve(REPORT_FILE_NAME)
        val csvFile = runRoot.resolve(CSV_FILE_NAME)

        val availableWorkloads = workloads()
        val unknownWorkloads = settings.workloads - availableWorkloads.mapTo(HashSet(), BenchmarkWorkload::name)
        require(unknownWorkloads.isEmpty()) {
            "Unknown vector query benchmark workloads: ${unknownWorkloads.sorted().joinToString()}"
        }
        val selectedWorkloads = availableWorkloads.filter { workload ->
            settings.workloads.isEmpty() || workload.name in settings.workloads
        }
        require(selectedWorkloads.isNotEmpty()) { "No vector query benchmark workloads were selected" }

        val scenarioResults = ArrayList<ScenarioResult>()
        settings.stores.forEach { storeType ->
            settings.recordCounts.forEach { recordCount ->
                scenarioResults += runScenario(
                    runRoot = runRoot,
                    settings = settings,
                    storeType = storeType,
                    recordCount = recordCount,
                    workloads = selectedWorkloads
                )
            }
        }

        val textReport = renderTextReport(settings, scenarioResults)
        val csvReport = renderCsvReport(settings, scenarioResults)
        Files.writeString(
            reportFile,
            textReport,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        Files.writeString(
            csvFile,
            csvReport,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        println(textReport)
        println("Vector query benchmark report: $reportFile")
        println("Vector query benchmark CSV: $csvFile")
    }

    private fun runScenario(
        runRoot: Path,
        settings: BenchmarkSettings,
        storeType: StoreType,
        recordCount: Int,
        workloads: List<BenchmarkWorkload>
    ): ScenarioResult {
        val databaseDirectory = runRoot.resolve(
            "database-${storeType.name.lowercase(Locale.ROOT)}-$recordCount.oxd"
        )
        prepareDatabase(databaseDirectory)

        val contextId = "vector-query-benchmark-${System.nanoTime()}-${storeType.name}-$recordCount"
        val trackingContext = VectorScannerTrackingSchemaContext(contextId, databaseDirectory.toString())

        val factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = databaseDirectory.toString(),
            instance = contextId,
            schemaContext = trackingContext,
            addShutdownHook = false
        ).apply {
            this.storeType = storeType
            maxCardinality = recordCount + 1
            setCredentials("admin", "admin")
        }

        var closed = false
        try {
            factory.initialize()
            val manager = factory.persistenceManager
            val context = manager.context
            val descriptor = context.getDescriptorForEntity(VectorQueryBenchmarkEntity::class.java, "")
            val indexedQueryInteractor = DefaultQueryInteractor(descriptor, manager, context)
            val fullScanQueryInteractor = DefaultQueryInteractorTestBridge.forceFullTable(
                descriptor,
                manager,
                context
            )

            val insertStarted = System.nanoTime()
            insertFixture(manager, recordCount, settings.seed, settings.batchSize)
            val insertNanos = System.nanoTime() - insertStarted

            val execution = BenchmarkExecution(
                manager = manager,
                descriptor = descriptor,
                indexedQueryInteractor = indexedQueryInteractor,
                fullScanQueryInteractor = fullScanQueryInteractor,
                trackingContext = trackingContext
            )
            val measurements = workloads.mapIndexed { workloadIndex, workload ->
                benchmarkWorkload(
                    execution = execution,
                    workload = workload,
                    recordCount = recordCount,
                    warmups = settings.warmups,
                    rounds = settings.rounds,
                    iterationsPerRound = settings.iterationsPerRound,
                    startIndexedFirst = workloadIndex % 2 == 0
                )
            }

            val entropy = VectorManagedConfiguration.forClass(VectorQueryBenchmarkEntity::class.java)
                .entropy.bitCount

            factory.close()
            closed = true
            val databaseBytes = if (storeType == StoreType.IN_MEMORY) {
                null
            } else {
                databaseDirectory.toFile().walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
            }

            return ScenarioResult(
                storeType = storeType,
                recordCount = recordCount,
                entropy = entropy,
                seed = settings.seed,
                insertNanos = insertNanos,
                databaseBytes = databaseBytes,
                measurements = measurements
            )
        } finally {
            if (!closed) factory.close()
            if (!settings.keepDatabase) {
                check(!databaseDirectory.toFile().exists() || databaseDirectory.toFile().deleteRecursively()) {
                    "Unable to remove vector benchmark database $databaseDirectory"
                }
            }
        }
    }

    private fun benchmarkWorkload(
        execution: BenchmarkExecution,
        workload: BenchmarkWorkload,
        recordCount: Int,
        warmups: Int,
        rounds: Int,
        iterationsPerRound: Int,
        startIndexedFirst: Boolean
    ): WorkloadMeasurement {
        execution.trackingContext.resetScannerUsage()
        val fullPreflight = execution.executeFullScan(workload.query(recordCount), captureDetails = true)
        assertTrue(execution.trackingContext.fullTableReads > 0, "${workload.name} did not execute FullTableScanner")
        assertEquals(0, execution.trackingContext.vectorScannerScans, "${workload.name} full baseline used vector scan")

        execution.trackingContext.resetScannerUsage()
        val indexedPreflight = execution.executeIndexed(workload.query(recordCount), captureDetails = true)
        assertTrue(execution.trackingContext.vectorScannerScans > 0, "${workload.name} did not execute VectorIndexScanner")
        assertTrue(
            execution.trackingContext.vectorFingerprintBranchReads > 0,
            "${workload.name} did not execute the fingerprint scan branch"
        )
        assertEquals(0, execution.trackingContext.fullTableReads, "${workload.name} automatic route used a full scan")
        if (workload.ordering == ResultOrdering.SEARCH_SCORE) {
            assertTrue(
                execution.trackingContext.fingerprintMatchAllCalls > 0,
                "${workload.name} did not execute FingerprintIndexInteractor.matchAll"
            )
        }
        execution.trackingContext.stopTracking()
        assertEquals(
            fullPreflight.orderedResults,
            indexedPreflight.orderedResults,
            "${workload.name} returned different ordered IDs/scores"
        )
        assertEquals(fullPreflight.signature, indexedPreflight.signature, "${workload.name} checksum")

        repeat(warmups) { warmup ->
            if ((warmup + if (startIndexedFirst) 0 else 1) % 2 == 0) {
                execution.executeIndexed(workload.query(recordCount), captureDetails = false)
                execution.executeFullScan(workload.query(recordCount), captureDetails = false)
            } else {
                execution.executeFullScan(workload.query(recordCount), captureDetails = false)
                execution.executeIndexed(workload.query(recordCount), captureDetails = false)
            }
        }

        val indexedSamples = ArrayList<Long>(rounds)
        val fullScanSamples = ArrayList<Long>(rounds)
        repeat(rounds) { round ->
            val indexedFirst = (round + if (startIndexedFirst) 0 else 1) % 2 == 0
            if (indexedFirst) {
                indexedSamples += measureNanosPerQuery(
                    iterationsPerRound,
                    fullPreflight.signature
                ) { execution.executeIndexed(workload.query(recordCount), captureDetails = false) }
                fullScanSamples += measureNanosPerQuery(
                    iterationsPerRound,
                    fullPreflight.signature
                ) { execution.executeFullScan(workload.query(recordCount), captureDetails = false) }
            } else {
                fullScanSamples += measureNanosPerQuery(
                    iterationsPerRound,
                    fullPreflight.signature
                ) { execution.executeFullScan(workload.query(recordCount), captureDetails = false) }
                indexedSamples += measureNanosPerQuery(
                    iterationsPerRound,
                    fullPreflight.signature
                ) { execution.executeIndexed(workload.query(recordCount), captureDetails = false) }
            }
        }

        val indexedMedian = indexedSamples.median()
        val fullScanMedian = fullScanSamples.median()
        return WorkloadMeasurement(
            workload = workload,
            matches = fullPreflight.signature.count,
            indexedSamples = indexedSamples,
            fullScanSamples = fullScanSamples,
            indexedMedianNanos = indexedMedian,
            indexedMinNanos = indexedSamples.min(),
            indexedMaxNanos = indexedSamples.max(),
            fullScanMedianNanos = fullScanMedian,
            fullScanMinNanos = fullScanSamples.min(),
            fullScanMaxNanos = fullScanSamples.max(),
            speedup = fullScanMedian.toDouble() / indexedMedian.toDouble()
        )
    }

    private fun measureNanosPerQuery(
        iterations: Int,
        expected: ResultSignature,
        block: () -> QueryRun
    ): Long {
        val observed = ArrayList<ResultSignature>(iterations)
        val startedAt = System.nanoTime()
        repeat(iterations) { observed += block().signature }
        val elapsed = System.nanoTime() - startedAt
        observed.forEach { signature ->
            check(signature == expected) {
                "Timed query result changed: expected $expected, observed $signature"
            }
            benchmarkSink = benchmarkSink xor signature.checksum
        }
        return elapsed / iterations
    }

    private fun insertFixture(
        manager: PersistenceManager,
        recordCount: Int,
        seed: Long,
        batchSize: Int
    ) {
        val ranks = shuffledRanks(recordCount, seed)
        val progressInterval = maxOf(batchSize, recordCount / 10)
        var inserted = 0
        while (inserted < recordCount) {
            val end = minOf(recordCount, inserted + batchSize)
            val batch = ArrayList<IManagedEntity>(end - inserted)
            while (inserted < end) {
                batch += entityForRank(ranks[inserted])
                inserted++
            }
            manager.saveEntities(batch)
            if (inserted == recordCount || inserted % progressInterval < batchSize) {
                println("Vector query benchmark: inserted %,d / %,d records".format(inserted, recordCount))
            }
        }
    }

    private fun entityForRank(rank: Int): VectorQueryBenchmarkEntity =
        VectorQueryBenchmarkEntity().apply {
            byteValue = ((rank % BYTE_CARDINALITY) - BYTE_OFFSET).toByte()
            shortValue = (rank % SHORT_CARDINALITY).toShort()
            intValue = rank
            longValue = longForRank(rank)
            floatValue = (rank % FLOAT_CARDINALITY) / 10.0f
            doubleValue = rank.toDouble() + (rank % 17).toDouble() / 17.0
            dateValue = dateForRank(rank)
            charValue = ('A'.code + rank % CHAR_CARDINALITY).toChar()
            booleanValue = rank % 2 == 0
            enumValue = VectorQueryBenchmarkState.entries[rank % VectorQueryBenchmarkState.entries.size]
            category = category(rank % CATEGORY_CARDINALITY)
            body = when {
                rank % RARE_TEXT_DIVISOR == 0 -> "rare amber comet needle-042 benchmark"
                rank % COMMON_TEXT_DIVISOR == 0 -> "common anchor route vector benchmark"
                else -> "ordinary vector record cohort-${rank % 37}"
            }
            nullableTag = if (rank % NULL_DIVISOR == 0) null else "tag-${rank % 211}"
            semanticSignature(semanticSignature(rank % SEMANTIC_CLUSTERS))
        }

    private fun shuffledRanks(recordCount: Int, seed: Long): IntArray {
        val ranks = IntArray(recordCount) { it }
        val random = SplittableRandom(seed xor recordCount.toLong())
        for (index in ranks.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = ranks[index]
            ranks[index] = ranks[swapIndex]
            ranks[swapIndex] = value
        }
        return ranks
    }

    private fun workloads(): List<BenchmarkWorkload> = listOf(
        workload("byte_equal_0_4pct", "Byte", "EQUAL") {
            QueryCriteria("byteValue", QueryCriteriaOperator.EQUAL, 42.toByte())
        },
        workload("short_in_0_05pct", "Short", "IN") {
            QueryCriteria(
                "shortValue",
                QueryCriteriaOperator.IN,
                listOf(7, 107, 207, 307, 407).map(Int::toShort)
            )
        },
        workload("int_equal_one_row", "Int", "EQUAL") { recordCount ->
            QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, recordCount / 2)
        },
        workload("long_greater_than_1pct", "Long", "GREATER_THAN") { recordCount ->
            val firstRank = recordCount - maxOf(1, recordCount / 100)
            QueryCriteria("longValue", QueryCriteriaOperator.GREATER_THAN, longForRank(firstRank - 1))
        },
        workload("float_between_10pct", "Float", "BETWEEN") {
            QueryCriteria("floatValue", QueryCriteriaOperator.BETWEEN, 45.0f to 54.9f)
        },
        workload("double_less_than_equal_50pct", "Double", "LESS_THAN_EQUAL") { recordCount ->
            QueryCriteria("doubleValue", QueryCriteriaOperator.LESS_THAN_EQUAL, recordCount / 2.0)
        },
        workload("date_between_1pct", "Date", "BETWEEN") { recordCount ->
            val firstRank = recordCount / 2
            val lastRank = firstRank + maxOf(1, recordCount / 100) - 1
            QueryCriteria("dateValue", QueryCriteriaOperator.BETWEEN, dateForRank(firstRank) to dateForRank(lastRank))
        },
        workload("char_equal_3_85pct", "Char", "EQUAL") {
            QueryCriteria("charValue", QueryCriteriaOperator.EQUAL, 'Q')
        },
        workload("boolean_equal_50pct", "Boolean", "EQUAL") {
            QueryCriteria("booleanValue", QueryCriteriaOperator.EQUAL, true)
        },
        workload("enum_equal_12_5pct", "Enum", "EQUAL") {
            QueryCriteria("enumValue", QueryCriteriaOperator.EQUAL, VectorQueryBenchmarkState.CHARLIE)
        },
        workload("string_equal_0_1pct", "String", "EQUAL") {
            QueryCriteria("category", QueryCriteriaOperator.EQUAL, category(42))
        },
        workload("string_between_shared_prefix_1pct", "String", "BETWEEN shared prefix") {
            QueryCriteria(
                "category",
                QueryCriteriaOperator.BETWEEN,
                category(40) to category(49)
            )
        },
        workload("is_null_1pct", "String?", "IS_NULL") {
            QueryCriteria("nullableTag", QueryCriteriaOperator.IS_NULL)
        },
        workload("date_starts_with_day", "Date text", "STARTS_WITH") { recordCount ->
            QueryCriteria(
                "dateValue",
                QueryCriteriaOperator.STARTS_WITH,
                DATE_DAY_FORMATTER.format(Instant.ofEpochMilli(dateForRank(recordCount / 2).time))
            )
        },
        workload("enum_like_12_5pct", "Enum text", "LIKE") {
            QueryCriteria("enumValue", QueryCriteriaOperator.LIKE, "STATE CHARLIE")
        },
        workload("string_contains_1pct", "String text", "CONTAINS") {
            QueryCriteria("body", QueryCriteriaOperator.CONTAINS, "amber comet")
        },
        workload("string_contains_two_char_1pct", "String text", "CONTAINS two-char gram") {
            QueryCriteria("body", QueryCriteriaOperator.CONTAINS, "am")
        },
        workload("string_like_1pct", "String text", "LIKE") {
            QueryCriteria("body", QueryCriteriaOperator.LIKE, "amber comet")
        },
        workload("string_matches_1pct", "String regex", "MATCHES") {
            QueryCriteria("body", QueryCriteriaOperator.MATCHES, ".*needle-042.*")
        },
        workload("int_not_equal_broad", "Int negative", "NOT_EQUAL") { recordCount ->
            QueryCriteria("intValue", QueryCriteriaOperator.NOT_EQUAL, recordCount / 2)
        },
        workload("body_not_contains_broad", "String negative", "NOT_CONTAINS") {
            QueryCriteria("body", QueryCriteriaOperator.NOT_CONTAINS, "common anchor")
        },
        workload("date_not_between_90pct", "Date negative", "NOT_BETWEEN") { recordCount ->
            val excludedWindow = maxOf(1, recordCount / 10)
            val firstRank = (recordCount - excludedWindow) / 2
            val lastRank = firstRank + excludedWindow - 1
            QueryCriteria(
                "dateValue",
                QueryCriteriaOperator.NOT_BETWEEN,
                dateForRank(firstRank) to dateForRank(lastRank)
            )
        },
        workload("compound_range_and_text", "Compound AND", "BETWEEN+CONTAINS") { recordCount ->
            val firstRank = recordCount / 2
            val lastRank = firstRank + maxOf(1, recordCount / 100) - 1
            QueryCriteria(
                "longValue",
                QueryCriteriaOperator.BETWEEN,
                longForRank(firstRank) to longForRank(lastRank)
            ).and(QueryCriteria("body", QueryCriteriaOperator.CONTAINS, "amber comet"))
        },
        workload("compound_negative_then_anchor", "Compound AND order", "NOT_EQUAL+EQUAL") {
            QueryCriteria("booleanValue", QueryCriteriaOperator.NOT_EQUAL, false)
                .and(QueryCriteria("category", QueryCriteriaOperator.EQUAL, category(42)))
        },
        workload("compound_anchor_then_negative", "Compound AND order", "EQUAL+NOT_EQUAL") {
            QueryCriteria("category", QueryCriteriaOperator.EQUAL, category(42))
                .and(QueryCriteria("booleanValue", QueryCriteriaOperator.NOT_EQUAL, false))
        },
        workload("compound_or_0_2pct", "Compound OR", "EQUAL+EQUAL") {
            QueryCriteria("category", QueryCriteriaOperator.EQUAL, category(42))
                .or(QueryCriteria("category", QueryCriteriaOperator.EQUAL, category(43)))
        },
        searchWorkload("lexical_search_rare_1pct", "Lexical search", "SEARCH all terms") { recordCount ->
            search(
                VectorSearchQuery(
                    text = "rare amber comet",
                    requireAllTerms = true,
                    maxCandidates = recordCount
                )
            )
        },
        searchWorkload("lexical_search_broad_99pct", "Lexical search", "SEARCH any term") { recordCount ->
            search(
                VectorSearchQuery(
                    text = "common ordinary",
                    requireAllTerms = false,
                    maxCandidates = recordCount
                )
            )
        },
        searchWorkload("lexical_search_and_category", "Search compound", "SEARCH+EQUAL") { recordCount ->
            search(
                VectorSearchQuery(
                    text = "rare amber comet",
                    requireAllTerms = true,
                    maxCandidates = recordCount
                )
            ).and(QueryCriteria("category", QueryCriteriaOperator.EQUAL, category(400)))
        },
        searchWorkload("semantic_search_exhaustive_1pct", "Semantic search", "SEARCH semantic exhaustive") { recordCount ->
            search(
                VectorSearchQuery(
                    semantic = semanticSignature(42),
                    minScore = 0.99f,
                    nearbyBucketRadius = 0,
                    maxCandidates = recordCount
                )
            )
        },
        searchWorkload(
            "semantic_search_top_25",
            "Semantic search",
            "SEARCH semantic bounded",
            maxResults = 25
        ) {
            search(
                VectorSearchQuery(
                    semantic = semanticSignature(42),
                    minScore = 0.99f,
                    nearbyBucketRadius = 0,
                    maxCandidates = 25
                )
            )
        },
        workload("equal_no_match", "String", "EQUAL no match") {
            QueryCriteria("category", QueryCriteriaOperator.EQUAL, "category-missing")
        }
    )

    private fun workload(
        name: String,
        family: String,
        operator: String,
        criteria: (Int) -> QueryCriteria
    ): BenchmarkWorkload = BenchmarkWorkload(
        name = name,
        family = family,
        operator = operator,
        ordering = ResultOrdering.ID,
        maxResults = -1,
        criteria = criteria
    )

    private fun searchWorkload(
        name: String,
        family: String,
        operator: String,
        maxResults: Int = -1,
        criteria: (Int) -> QueryCriteria
    ): BenchmarkWorkload = BenchmarkWorkload(
        name = name,
        family = family,
        operator = operator,
        ordering = ResultOrdering.SEARCH_SCORE,
        maxResults = maxResults,
        criteria = criteria
    )

    private fun semanticSignature(cluster: Int): SemanticVectorSignature {
        val random = SplittableRandom(DEFAULT_SEED xor (cluster.toLong() * 1_000_003L))
        val fingerprint = longArrayOf(random.nextLong(), random.nextLong())
        return SemanticVectorSignature(
            calibrationId = SEMANTIC_CALIBRATION_ID,
            bucketId = cluster,
            cells = intArrayOf(cluster),
            cellCounts = intArrayOf(SEMANTIC_CLUSTERS),
            fingerprint = fingerprint,
            bands = SemanticVectorSignature.splitIntoFourBands(fingerprint),
            boundaryConfidence = 1.0f
        )
    }

    private fun longForRank(rank: Int): Long = rank.toLong() * 1_000_003L + 17L

    private fun dateForRank(rank: Int): Date = Date(BASE_TIMESTAMP_MILLIS + rank.toLong() * MILLIS_PER_MINUTE)

    private fun category(value: Int): String = "category-${value.toString().padStart(3, '0')}"

    private fun prepareDatabase(databaseDirectory: Path) {
        if (databaseDirectory.toFile().exists()) {
            check(databaseDirectory.toFile().deleteRecursively()) {
                "Unable to remove previous vector benchmark database $databaseDirectory"
            }
        }
    }

    private fun renderTextReport(
        settings: BenchmarkSettings,
        scenarios: List<ScenarioResult>
    ): String = buildString {
        appendLine("Vector index vs full-table-scan benchmark")
        appendLine("timestamp=${Instant.now()}")
        appendLine("recordCounts=${settings.recordCounts.joinToString()}")
        appendLine("stores=${settings.stores.joinToString()}")
        appendLine("warmupsPerArm=${settings.warmups}")
        appendLine("rounds=${settings.rounds}")
        appendLine("iterationsPerRound=${settings.iterationsPerRound}")
        appendLine("batchSize=${settings.batchSize}")
        appendLine("shuffleSeed=${settings.seed}")
        appendLine("syntheticSemanticSeed=$DEFAULT_SEED")
        appendLine("java=${System.getProperty("java.version")} ${System.getProperty("java.vm.name")}")
        appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("processors=${Runtime.getRuntime().availableProcessors()}")
        appendLine("maxHeapBytes=${Runtime.getRuntime().maxMemory()}")
        appendLine()
        appendLine("mode=warm-cache steady state (fixture insertion and parity preflight warm the store)")
        appendLine("Both arms use the same DefaultQueryInteractor pipeline; only scanner selection differs.")
        appendLine("Each timing includes planning, access, exact verification, materialization, and ID/relevance ordering.")
        appendLine("Semantic signatures are deterministic synthetic routing fixtures, not calibration-quality embeddings.")
        appendLine("Speedup greater than 1.0 means the vector-index median is lower; below 1.0 favors the full scan.")
        appendLine()

        scenarios.forEach { scenario ->
            val insertRate = scenario.recordCount.toDouble() * NANOS_PER_SECOND / scenario.insertNanos
            appendLine("store=${scenario.storeType} records=${scenario.recordCount} entropy=${scenario.entropy}")
            appendLine(
                "insertMs=${formatMillis(scenario.insertNanos)} " +
                    "insertRecordsPerSecond=${formatDecimal(insertRate)} " +
                    "physicalDatabaseBytes=${scenario.databaseBytes?.toString() ?: "N/A (in-memory)"}"
            )
            appendLine(
                "workload\tfamily\toperator\tordering\tmatches\tselectivityPct\t" +
                    "indexRoundMeanMinMs\tindexRoundMeanP50Ms\tindexRoundMeanMaxMs\t" +
                    "fullRoundMeanMinMs\tfullRoundMeanP50Ms\tfullRoundMeanMaxMs\t" +
                    "indexQps\tfullQps\tspeedup"
            )
            scenario.measurements.forEach { measurement ->
                appendLine(
                    listOf(
                        measurement.workload.name,
                        measurement.workload.family,
                        measurement.workload.operator,
                        measurement.workload.ordering,
                        measurement.matches,
                        formatDecimal(measurement.matches.toDouble() * 100.0 / scenario.recordCount),
                        formatMillis(measurement.indexedMinNanos),
                        formatMillis(measurement.indexedMedianNanos),
                        formatMillis(measurement.indexedMaxNanos),
                        formatMillis(measurement.fullScanMinNanos),
                        formatMillis(measurement.fullScanMedianNanos),
                        formatMillis(measurement.fullScanMaxNanos),
                        formatDecimal(NANOS_PER_SECOND / measurement.indexedMedianNanos),
                        formatDecimal(NANOS_PER_SECOND / measurement.fullScanMedianNanos),
                        formatDecimal(measurement.speedup)
                    ).joinToString("\t")
                )
                appendLine("  indexSamplesNanos=${measurement.indexedSamples.joinToString()}")
                appendLine("  fullSamplesNanos=${measurement.fullScanSamples.joinToString()}")
            }
            val indexWins = scenario.measurements.count { it.speedup > 1.0 }
            appendLine(
                "summary=indexMedianLower:$indexWins fullScanMedianLowerOrEqual:" +
                    "${scenario.measurements.size - indexWins} total:${scenario.measurements.size}"
            )
            appendLine()
        }
    }

    private fun renderCsvReport(
        settings: BenchmarkSettings,
        scenarios: List<ScenarioResult>
    ): String = buildString {
        appendLine(
            "store,records,entropy,shuffle_seed,semantic_seed,warmups,rounds,iterations_per_round,batch_size," +
                "insert_ns,physical_database_bytes,java_version,os_name,os_version,os_arch," +
                "workload,family,operator,ordering,matches,selectivity_pct," +
                "index_round_mean_min_ns,index_round_mean_p50_ns,index_round_mean_max_ns," +
                "full_round_mean_min_ns,full_round_mean_p50_ns,full_round_mean_max_ns," +
                "index_qps,full_qps,speedup"
        )
        scenarios.forEach { scenario ->
            scenario.measurements.forEach { measurement ->
                appendLine(
                    listOf(
                        scenario.storeType,
                        scenario.recordCount,
                        scenario.entropy,
                        scenario.seed,
                        DEFAULT_SEED,
                        settings.warmups,
                        settings.rounds,
                        settings.iterationsPerRound,
                        settings.batchSize,
                        scenario.insertNanos,
                        scenario.databaseBytes ?: "",
                        csvEscape(System.getProperty("java.version")),
                        csvEscape(System.getProperty("os.name")),
                        csvEscape(System.getProperty("os.version")),
                        csvEscape(System.getProperty("os.arch")),
                        measurement.workload.name,
                        measurement.workload.family,
                        measurement.workload.operator,
                        measurement.workload.ordering,
                        measurement.matches,
                        formatDecimal(measurement.matches.toDouble() * 100.0 / scenario.recordCount),
                        measurement.indexedMinNanos,
                        measurement.indexedMedianNanos,
                        measurement.indexedMaxNanos,
                        measurement.fullScanMinNanos,
                        measurement.fullScanMedianNanos,
                        measurement.fullScanMaxNanos,
                        formatDecimal(NANOS_PER_SECOND / measurement.indexedMedianNanos),
                        formatDecimal(NANOS_PER_SECOND / measurement.fullScanMedianNanos),
                        formatDecimal(measurement.speedup)
                    ).joinToString(",")
                )
            }
        }
    }

    private fun List<Long>.median(): Long = sorted()[size / 2]

    private fun formatMillis(nanos: Long): String = formatDecimal(nanos.toDouble() / 1_000_000.0)

    private fun formatDecimal(value: Double): String = "%.3f".format(Locale.ROOT, value)

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

    private data class BenchmarkExecution(
        val manager: PersistenceManager,
        val descriptor: EntityDescriptor,
        val indexedQueryInteractor: DefaultQueryInteractor,
        val fullScanQueryInteractor: DefaultQueryInteractor,
        val trackingContext: VectorScannerTrackingSchemaContext
    ) {
        fun executeIndexed(query: Query, captureDetails: Boolean): QueryRun {
            val collector = indexedQueryInteractor.getReferencesForQuery<VectorQueryBenchmarkEntity>(query)
            return consume(collector.results, query, captureDetails)
        }

        fun executeFullScan(query: Query, captureDetails: Boolean): QueryRun {
            val collector = fullScanQueryInteractor.getReferencesForQuery<VectorQueryBenchmarkEntity>(query)
            return consume(collector.results, query, captureDetails)
        }

        private fun consume(results: Collection<*>, query: Query, captureDetails: Boolean): QueryRun {
            var orderedHash = RESULT_HASH_SEED
            val orderedResults = if (captureDetails) ArrayList<ScoredResult>(results.size) else null
            results.forEach { value ->
                val entity = value as VectorQueryBenchmarkEntity
                val scoreBits = if (query.queryOrders.isNullOrEmpty()) {
                    query.fullTextScores
                        ?.get(entity.reference(manager.context, descriptor))
                        ?.toRawBits()
                } else {
                    null
                }
                val token = mixIdentifier(entity.id) xor (scoreBits?.toLong()?.shl(32) ?: 0L)
                orderedHash = java.lang.Long.rotateLeft(orderedHash xor token, 27) * RESULT_HASH_MULTIPLIER
                orderedResults?.add(ScoredResult(entity.id, scoreBits))
            }
            return QueryRun(
                signature = ResultSignature(results.size, orderedHash),
                orderedResults = orderedResults
            )
        }
    }

    private data class BenchmarkSettings(
        val recordCounts: List<Int>,
        val stores: List<StoreType>,
        val warmups: Int,
        val rounds: Int,
        val iterationsPerRound: Int,
        val batchSize: Int,
        val seed: Long,
        val keepDatabase: Boolean,
        val workloads: Set<String>
    ) {
        companion object {
            fun load(): BenchmarkSettings {
                val recordCounts = setting(RECORD_COUNTS_PROPERTY, RECORD_COUNTS_ENV, "10000")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map(String::toInt)
                    .distinct()
                    .sorted()
                val stores = setting(STORES_PROPERTY, STORES_ENV, StoreType.MEMORY_MAPPED_FILE.name)
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map { StoreType.valueOf(it.uppercase(Locale.ROOT)) }
                    .distinct()
                val warmups = setting(WARMUPS_PROPERTY, WARMUPS_ENV, "1").toInt()
                val rounds = setting(ROUNDS_PROPERTY, ROUNDS_ENV, "5").toInt()
                val iterations = setting(ITERATIONS_PROPERTY, ITERATIONS_ENV, "1").toInt()
                val batchSize = setting(BATCH_SIZE_PROPERTY, BATCH_SIZE_ENV, "1000").toInt()
                val seed = setting(SEED_PROPERTY, SEED_ENV, DEFAULT_SEED.toString()).toLong()
                val workloads = setting(WORKLOADS_PROPERTY, WORKLOADS_ENV, "")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet()
                require(recordCounts.isNotEmpty() && recordCounts.all { it > 0 }) {
                    "Record counts must contain positive integers"
                }
                require(stores.isNotEmpty()) { "At least one StoreType is required" }
                require(warmups >= 0) { "Warmups must be non-negative" }
                require(rounds > 0) { "Rounds must be positive" }
                require(iterations > 0) { "Iterations per round must be positive" }
                require(batchSize > 0) { "Batch size must be positive" }
                require(recordCounts.max() < Int.MAX_VALUE) { "Record count is too large" }
                return BenchmarkSettings(
                    recordCounts = recordCounts,
                    stores = stores,
                    warmups = warmups,
                    rounds = rounds,
                    iterationsPerRound = iterations,
                    batchSize = batchSize,
                    seed = seed,
                    keepDatabase = setting(KEEP_DATABASE_PROPERTY, KEEP_DATABASE_ENV, "false").toBoolean(),
                    workloads = workloads
                )
            }
        }
    }

    private data class BenchmarkWorkload(
        val name: String,
        val family: String,
        val operator: String,
        val ordering: ResultOrdering,
        val maxResults: Int,
        val criteria: (Int) -> QueryCriteria
    ) {
        fun query(recordCount: Int): Query = Query(
            VectorQueryBenchmarkEntity::class.java,
            criteria(recordCount)
        ).also { query ->
            if (ordering == ResultOrdering.ID) query.queryOrders = listOf(QueryOrder("id", true))
            query.maxResults = maxResults
        }
    }

    private enum class ResultOrdering { ID, SEARCH_SCORE }

    private data class ScoredResult(val id: Long, val scoreBits: Int?)

    private data class QueryRun(
        val signature: ResultSignature,
        val orderedResults: List<ScoredResult>?
    )

    private data class ResultSignature(
        val count: Int,
        val orderedHash: Long
    ) {
        val checksum: Long
            get() = orderedHash xor count.toLong()
    }

    private data class WorkloadMeasurement(
        val workload: BenchmarkWorkload,
        val matches: Int,
        val indexedSamples: List<Long>,
        val fullScanSamples: List<Long>,
        val indexedMedianNanos: Long,
        val indexedMinNanos: Long,
        val indexedMaxNanos: Long,
        val fullScanMedianNanos: Long,
        val fullScanMinNanos: Long,
        val fullScanMaxNanos: Long,
        val speedup: Double
    )

    private data class ScenarioResult(
        val storeType: StoreType,
        val recordCount: Int,
        val entropy: Int,
        val seed: Long,
        val insertNanos: Long,
        val databaseBytes: Long?,
        val measurements: List<WorkloadMeasurement>
    )

    private companion object {
        const val ENABLED_PROPERTY = "onyx.benchmark.vectorQuery.enabled"
        const val ENABLED_ENV = "ONYX_VECTOR_QUERY_BENCHMARK"
        const val RECORD_COUNTS_PROPERTY = "onyx.benchmark.vectorQuery.recordCounts"
        const val RECORD_COUNTS_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_RECORD_COUNTS"
        const val STORES_PROPERTY = "onyx.benchmark.vectorQuery.stores"
        const val STORES_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_STORES"
        const val WARMUPS_PROPERTY = "onyx.benchmark.vectorQuery.warmups"
        const val WARMUPS_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_WARMUPS"
        const val ROUNDS_PROPERTY = "onyx.benchmark.vectorQuery.rounds"
        const val ROUNDS_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_ROUNDS"
        const val ITERATIONS_PROPERTY = "onyx.benchmark.vectorQuery.iterations"
        const val ITERATIONS_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_ITERATIONS"
        const val BATCH_SIZE_PROPERTY = "onyx.benchmark.vectorQuery.batchSize"
        const val BATCH_SIZE_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_BATCH_SIZE"
        const val SEED_PROPERTY = "onyx.benchmark.vectorQuery.seed"
        const val SEED_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_SEED"
        const val KEEP_DATABASE_PROPERTY = "onyx.benchmark.vectorQuery.keepDatabase"
        const val KEEP_DATABASE_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_KEEP_DATABASE"
        const val WORKLOADS_PROPERTY = "onyx.benchmark.vectorQuery.workloads"
        const val WORKLOADS_ENV = "ONYX_VECTOR_QUERY_BENCHMARK_WORKLOADS"

        const val REPORT_FILE_NAME = "vector-index-vs-full-scan.txt"
        const val CSV_FILE_NAME = "vector-index-vs-full-scan.csv"
        const val RUN_DIRECTORY_PREFIX = "vector-index-vs-full-scan-"
        const val DEFAULT_SEED = 14_482_326L
        const val BYTE_CARDINALITY = 250
        const val BYTE_OFFSET = 125
        const val SHORT_CARDINALITY = 10_000
        const val FLOAT_CARDINALITY = 1_000
        const val CHAR_CARDINALITY = 26
        const val CATEGORY_CARDINALITY = 1_000
        const val RARE_TEXT_DIVISOR = 100
        const val COMMON_TEXT_DIVISOR = 5
        const val NULL_DIVISOR = 100
        const val SEMANTIC_CLUSTERS = 100
        const val SEMANTIC_CALIBRATION_ID = 7_314_159L
        const val BASE_TIMESTAMP_MILLIS = 1_704_067_200_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val RESULT_HASH_SEED = -7_046_029_254_386_353_131L
        const val RESULT_HASH_MULTIPLIER = -4_658_895_280_553_007_687L

        val DATE_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC)

        @Volatile
        var benchmarkSink: Long = 0L

        fun mixIdentifier(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 30)) * -4_658_895_280_553_007_687L
            mixed = (mixed xor (mixed ushr 27)) * -7_723_592_293_110_705_685L
            return mixed xor (mixed ushr 31)
        }

        fun setting(property: String, environment: String, default: String): String =
            System.getProperty(property) ?: System.getenv(environment) ?: default
    }
}
