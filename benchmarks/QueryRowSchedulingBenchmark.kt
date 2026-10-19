package com.onyx.extension.common

import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Isolated, opt-in scheduling benchmark; see run-query-row-scheduling.py. */
object QueryRowSchedulingBenchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 6) { "mode rows concurrentQueries workIterations warmups rounds" }
        val mode = args[0]
        val count = args[1].toInt()
        val concurrent = args[2].toInt()
        val work = args[3].toInt()
        val warmups = args[4].toInt()
        val rounds = args[5].toInt()
        require(mode in setOf("legacy", "bounded-per-row", "chunked"))
        val cores = Runtime.getRuntime().availableProcessors()
        val rows = (0 until count).toCollection(LinkedHashSet())
        val expected = Result()
        rows.forEach { expected.collect(it, work) }
        val submitted = AtomicLong()
        val legacy = if (mode == "chunked") null else ForkJoinPool(if (mode == "legacy") cores * 200 else cores)
        val processor = if (mode == "chunked") ParallelRowProcessor(cores) else null
        val background = processor?.let {
            ParallelRowProcessor::class.java.getDeclaredField("executor").apply { isAccessible = true }.get(it) as ThreadPoolExecutor
        }
        val callers = Executors.newFixedThreadPool(concurrent)
        val threadBean = ManagementFactory.getThreadMXBean()
        val operatingSystem = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean

        fun collect(): Result {
            val result = Result()
            if (processor != null) {
                processor.forEach(rows) { result.collect(it, work) }
            } else {
                val futures = ArrayList<Future<Unit>>()
                rows.forEach { row ->
                    futures.add(legacy!!.submit(Callable { result.collect(row, work) }))
                    if (futures.size > 100) {
                        futures.forEach { it.get() }
                        futures.clear()
                    }
                }
                futures.forEach { it.get() }
                submitted.addAndGet(rows.size.toLong())
            }
            check(result == expected) { "Incorrect result: $result, expected $expected" }
            return result
        }

        fun runRound(): Long {
            val start = CountDownLatch(1)
            val tasks = (0 until concurrent).map {
                callers.submit(Callable {
                    start.await()
                    collect()
                })
            }
            val began = System.nanoTime()
            start.countDown()
            tasks.forEach { it.get(60, TimeUnit.SECONDS) }
            return System.nanoTime() - began
        }

        try {
            repeat(warmups) { runRound() }
            val tasksBefore = background?.taskCount ?: submitted.get()
            val threadsBefore = threadBean.totalStartedThreadCount
            val cpuBefore = operatingSystem.processCpuTime
            val elapsed = LongArray(rounds) { runRound() }
            val cpuMs = (operatingSystem.processCpuTime - cpuBefore) / 1e6
            val tasksAfter = background?.taskCount ?: submitted.get()
            val threadsAfter = threadBean.totalStartedThreadCount
            val wallMs = elapsed.sum() / 1e6
            elapsed.sort()
            val workerCount = legacy?.poolSize ?: background!!.poolSize
            val json = linkedMapOf<String, Any>(
                "mode" to mode, "cores" to cores, "rows" to count, "concurrent_queries" to concurrent,
                "work_iterations" to work, "warmups" to warmups, "rounds" to rounds,
                "wall_ms" to wallMs, "round_p50_ms" to elapsed[rounds / 2] / 1e6,
                "round_p95_ms" to elapsed[(rounds * 0.95).toInt().coerceAtMost(rounds - 1)] / 1e6,
                "process_cpu_ms" to cpuMs, "queries_per_second" to rounds * concurrent * 1000.0 / wallMs,
                "submitted_worker_tasks" to tasksAfter - tasksBefore,
                "tasks_per_query" to (tasksAfter - tasksBefore).toDouble() / (rounds * concurrent),
                "pool_workers_after" to workerCount, "new_platform_threads" to threadsAfter - threadsBefore,
                "verified_rows_per_query" to expected.count, "verified_checksum" to expected.checksum,
                "verified_top_one_score" to expected.maximum
            )
            println(json.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "\"$key\":" + if (value is String) "\"$value\"" else value.toString()
            })
        } finally {
            callers.shutdownNow()
            legacy?.shutdownNow()
            processor?.close()
        }
    }

    // A limited collector still computes every candidate and synchronizes count,
    // checksum and top-one retention. This isolates scheduling; it is not disk I/O
    // or a measurement of real ingestion throughput.
    private data class Result(var count: Int = 0, var checksum: Long = 0, var maximum: Long = Long.MIN_VALUE) {
        fun collect(row: Int, work: Int) {
            var score = row.toLong() + 1
            repeat(work) {
                score = score xor (score shl 13)
                score = score xor (score ushr 7)
                score = score xor (score shl 17)
            }
            synchronized(this) {
                count++
                checksum += score
                maximum = maxOf(maximum, score)
            }
        }
    }
}
