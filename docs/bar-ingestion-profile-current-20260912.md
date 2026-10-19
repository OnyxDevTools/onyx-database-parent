Updated Bar ingestion profile — September 12, 2026

**First deploy the engine changes already implemented in the intended checkout.** The running preview contains the updated Bar cache and rollup code, but still packages the old query executor and WAL mapping behavior. Its query-worker pool consumed **2.52 CPU cores, 40.1% of accounted JVM thread CPU**, in a separate 60-second measurement. This includes useful query work and scheduling; it is not a measurement of scheduling overhead alone.

The new application targets are **repeated history traversal and SMA history loading**, followed by **analysis persistence** and the **previous-session fallback query**.

Capture: **19:29:22–19:33:26 MDT**, September 12 (01:29:22–01:33:26 UTC, September 13). Server PID **525997**, client PID **526867**. Used 120 JSON thread snapshots at randomized 1.5–2.5-second intervals, Linux process/thread counters, `jstat`, and existing request logs. No restart, deployment, source instrumentation, schema change, or ingestion pause. JFR was avoided because the earlier recording ended in a JVM recorder crash. Linux `perf` could not run because its kernel-specific executable is unavailable.

**What is actually running**

The API [composite build configuration](/home/tosborn/OnyxWorkspace/onyx-cloud/onyx-cloud-api/settings.gradle.kts:30) resolves its database dependency to `/home/tosborn/OnyxWorkspace/onyx-database-parent`. The improvements in this working directory are under `/home/tosborn/IdeaProjects/onyx-database-parent`. These are separate checkouts.

The open runtime JAR's inode matches the inspected file, which was built before the process started. Its concurrency, collector, WAL store, and WAL channel classes exactly match the **OnyxWorkspace** build. The JAR lacks `ParallelRowProcessor`; its collector still submits one Future per row to a `ForkJoinPool` configured with `availableProcessors() * 200`. Its WAL channel uses the old constructor without normal-segment preallocation. Bytecode and hashes are retained in the [deployment verification](/tmp/onyx-ingestion-profile-20260912-current/deployment-verification.md).

Reconcile the intended database changes into the checkout consumed by preview, or deliberately configure the composite build to use the updated checkout, then rebuild through the normal preview workflow. **Rebuilding preview alone currently keeps selecting the older engine checkout.** No deployment change was made during this investigation.

The live generated schema does contain cache **v5**, removal of the one-second expiry, retention of predecessor/derived caches between sequential minute saves on the same thread, and append-then-reverse rollup construction. These findings use the captured generated source, not a possibly different local schema file.

**Where ingestion requests spend time**

There were **596 Bar request-thread observations** across 74 distinct request IDs, plus one unrelated request observation. Each Bar observation is assigned once to its nearest operation/caller. Percentages measure **wall-clock stack residency**, not CPU shares, invocation counts, exact operation timings, or expected speedups. Asynchronous query workers are measured separately and cannot be assigned exactly to their originating resolver.

| Exclusive observed work | Observations | Share |
| --- | ---: | ---: |
| History traversal and calculation-cache access | 182 | 30.5% |
| Predecessor database lookup/hydration | 155 | 26.0% |
| Analysis, snapshot, and future-label saves | 148 | 24.8% |
| Session aggregate queries | 47 | 7.9% |
| Persisted recursive-indicator state reads | 14 | 2.3% |
| Other calculation, reads, and persistence | 50 | 8.4% |

The first four categories respectively occupy **32.3%, 25.9%, 22.9%, 8.1%** in the first half, and **28.8%, 26.1%, 26.8%, 7.7%** in the second. The ranking is reasonably stable within this capture.

1. **Remove repeated history work, including work that already hits the cache.** History traversal/calculation-cache access plus predecessor database reads occupy **56.5%** of Bar observations. The local category includes 176 predecessor-path observations and six other calculation-cache accesses. Existing caching is present and the earlier weekly-read dominance is no longer visible; its hit rate was not instrumented.

   Of 155 predecessor database-read observations, **70 originate in SMA200, 35 in SMA100, and 27 in SMA50**: together **85.2%**. WEEK accounts for only 11. The [SMA200 implementation](/tmp/onyx-ingestion-profile-20260912-current/Bar-live.kt:1381) builds and averages its historical window; the sampled work beneath the [primary-ID lookup](/tmp/onyx-ingestion-profile-20260912-current/Bar-live.kt:4445) includes B-tree comparisons, decoding, and field access. Prioritize sharing one validated history pass across SMA50/100/200, then maintaining incremental sums where practical. Preserve the current average-of-OHLC formula, completeness checks, and interval stride. Longer intervals step by `sequenceSize`; state must distinguish stride phases rather than averaging consecutive rollups. Incremental sums also need numerical-equivalence checks because floating-point accumulation order changes.

   WEEK construction still walks and then aggregates up to **4,410 minute Bars per new minute** in the [live loop](/tmp/onyx-ingestion-profile-20260912-current/Bar-live.kt:3985). There are **89 local predecessor/cache observations** inside that loop, versus 11 database reads. Entire weekly construction occupies **106/596 observations, 17.8%**. Cached traversal still builds IDs, hashes keys, retrieves calculation state, and validates each predecessor. Replace repeated window reconstruction with bounded history, rolling sums/VWAP, and suitable high/low structures, preserving the sequence-based 630-minute DAY and 4,410-minute WEEK definitions and missing-history behavior. A smaller intermediate change is to acquire typed calculation state once per traversal and share validated history between resolvers.

   The [current cache](/tmp/onyx-ingestion-profile-20260912-current/Bar-live.kt:5262) is ThreadLocal, while the API runs each request on a fresh [virtual task thread](/home/tosborn/OnyxWorkspace/onyx-cloud/onyx-cloud-api/src/main/kotlin/dev/onyx/cloud/extensions/Server.kt:302). Reuse within a batch therefore does not supply a shared cache to the next request. A bounded database/symbol history owner could avoid that warmup, but this capture does not quantify cross-request misses or capacity eviction. Preserve database isolation, symbol ordering, and correction/replay semantics in any shared state.

2. **Reassess WAL synchronization after the existing WAL fix is deployed.** Saving the current analysis at [line 5759](/tmp/onyx-ingestion-profile-20260912-current/Bar-live.kt:5759) accounts for **128/596 observations, 21.5%**; other saves from the same trigger add 20 observations. Across these saves, **106 observations** stop exactly at WAL monitor-entry source locations: 83 at `DefaultTransactionInteractor.writeTransaction:47`, 23 at `MemoryMappedTransactionStore.getTransactionFile:47`. Of those, 88 occur while materializing rollup analyses.

   This identifies persistence synchronization as an investigation target, but JSON snapshots lack thread states and **do not prove that every observation is blocked**. First include the already implemented preallocation/gathering-write changes. If this remains prominent, measure lock wait/hold time, force/rotation duration, and writes per input; then consider grouped appends or reduced redundant derived-record writes while preserving durability, trigger behavior, and label availability. Adding ingestion workers alone will not remove serialized WAL sections.

3. **Optimize the expensive previous-session fallback, preserving the existing accumulator.** Of 47 session-query observations, **44** are at [fallback line 876](/tmp/onyx-ingestion-profile-20260912-current/Bar-live.kt:870): partitioned `Bar`, `timestamp < dayStart AND interval = MINUTE`, timestamp descending, limit 1,000. It subsequently selects the first valid regular-session close. The remaining three are the day-bounded fast validation query.

   This fallback's historical search is unbounded below, and LIMIT does not establish early stopping in the running collector. A compact prior-session endpoint or descending partition-index traversal with appropriate residual filtering can avoid broad historical collection. The schema already has a shared session accumulator, and session-state calculation is MINUTE-only; there is no nine-interval multiplier for this query. Repeated snapshots can observe one slow invocation, so these counts do not demonstrate a cache miss on every minute. Preserve the 1,000-candidate and missing-history semantics unless intentionally changing the contract. See the [query analysis](/tmp/onyx-ingestion-profile-20260912-current/session-query-analysis.md).

**CPU, latency, and operational checks**

The JVM used **1,724.93 CPU-seconds over 243.52 seconds**, averaging **7.08 CPU cores** across all server work. Independent Linux per-thread CPU counters over **60.01 seconds** gave:

| Thread group | CPU-seconds | Average cores | Share of accounted thread CPU |
| --- | ---: | ---: | ---: |
| Virtual-thread carriers | 212.07 | 3.53 | 56.3% |
| Query executor, 335 workers | 151.20 | 2.52 | 40.1% |
| Garbage collection | 9.42 | 0.16 | 2.5% |
| WAL flush/force workers | 3.53 | 0.06 | 0.9% |
| Other | 0.62 | 0.01 | 0.2% |

These are CPU measurements by platform-thread group, separate from the request-residency table. Carrier CPU cannot be assigned to individual virtual requests; query-pool CPU combines execution and scheduling. Low WAL-worker CPU does not imply low write latency, since request threads can wait on synchronization or I/O. Ten new endpoint threads had zero recorded CPU; no starting thread exited. Threads born and terminated between endpoints can be missed. Counts are subject to Linux CPU-tick resolution.

- **69 completed Bar PUT batches, all HTTP 200**: median **16.070 s**, p95 **25.815 s** (nearest rank), range **14.670–27.003 s**. All 69 LatestBar checkpoint saves succeeded. No error/exception log lines; four unrelated image requests returned 404.
- The client has **five active symbol workers**, with default chunks of **1,000 Bars** and possible smaller tails/retries. The observed rate is **0.283 completed batches/s**. Approximately **283 input Bars/s** would follow only if all completed batches were full and distinct; exact payload/unique-row throughput was not measured. Requests at window boundaries can begin before capture or finish afterward.
- Ten health checks returned **200**. Diagnostic-command duration averaged **104 ms**, maximum **140 ms**; this includes command startup and is not an application-pause or profiler-overhead measurement.
- Separate `jstat` sampling over nominally 240 seconds recorded **80 young collections, 1.871 s reported GC time, zero full collections**. Combined with 2.5% observed thread CPU, GC is a lower-priority target in this run.
- RSS increased from **23.20 to 24.40 GiB**; this includes mapped/resident pages and does not establish a heap leak. Kernel process write accounting increased **9.45 GB**, about **37 MiB/s**. These counters do not attribute physical device latency or prove storage saturation.

The previous rollback snapshot profile had predecessor reads in **66.0%** of Bar observations, with weekly callers alone contributing **43.2%**; here predecessor reads are **26.0%**, and weekly predecessor reads **1.8%**. The earlier median batch latency was **22.386 s**, versus **16.070 s** here. Different capture times, data positions, and code mean these are **not controlled before/after speedup measurements**. The useful conclusion is that the visible bottleneck has shifted toward SMA hydration, cached traversal, and writes.

**Cleanup and reproducibility**

Removed **38 confirmed old profiling paths totaling 354,847,406 bytes (338.4 MiB)**: both previous capture directories/reports, the failed JFR repository/crash files, and older index-posting diagnostics. Kept implementation reports, source changes, tests, reusable benchmarks/results, and deployment rollback backups. Exact deletions are recorded in [cleanup.json](/tmp/onyx-ingestion-profile-20260912-current/cleanup.json).

All current evidence is consolidated in [/tmp/onyx-ingestion-profile-20260912-current](/tmp/onyx-ingestion-profile-20260912-current), totaling approximately **3.35 MiB**: generated source copies/hashes, deployment bytecode checks, request logs/statistics, process counters, scripts, summaries, and compressed raw snapshots. All 120 archived snapshots were verified against SHA-256 hashes before removing the loose copies. The offline parsers count each thread ID once per snapshot and allocate one nearest operation per Bar observation. To reproduce stack analysis, extract `thread-snapshots.tar.gz` into that directory and run `python3 analyze_ingestion.py`; `summarize_process.py` and `summarize_workload.py` regenerate the other summaries. The capture scripts attach to the recorded PIDs and should not be rerun as offline analysis. At the final check, both server and ingestion client remained running and health returned 200; all profiling subprocesses had completed.
