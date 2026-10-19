Ingestion execution changes — September 12, 2026

Implemented the requested query scheduling improvement, rolling-window list construction (#2), and WAL append improvement (#4). Changes are local; this implementation and its benchmarks did not attach to, restart, or deploy to the running ingestion. The earlier rollback captures and report were removed during profiling cleanup. The [latest live profile](bar-ingestion-profile-current-20260912.md) records the current workload and deployment mismatch; the measurements below use isolated test processes and temporary files.

**Query row collection now uses a bounded pool and chunks of 256 rows.** [ParallelRowProcessor](../onyx-database/src/main/kotlin/com/onyx/extension/common/ParallelRowProcessor.kt) replaces one Future per candidate with at most one worker task per available processor per traversal. A global nonblocking permit limit bounds running plus pending background row tasks across concurrent queries. The caller also processes rows, so the background-worker bound is not a bound on all participating application threads. The existing collector threshold of more than 1,000 candidates is retained, including queries that return only one result.

Directly nested traversals run inline. A traversal unable to reserve a worker processes its own rows without waiting for a permit. Separately, [the general async executor](../onyx-database/src/main/kotlin/com/onyx/extension/common/Any$Concurrency.kt) now uses cached daemon platform threads: blocked partition scans, sequence producers, and listener callbacks must still permit nested async work to start. That executor remains elastic. Keeping these blocking tasks separate avoids a fixed shared pool deadlocking when every worker waits for work submitted to the same pool. Virtual threads were not selected because listener callbacks hold Java monitors on the current JDK 23 runtime.

On callback failure or interruption, row collection stops taking new work and drains started callbacks before returning or throwing. This prevents a failed query leaving background callbacks mutating its collector. Tests cover direct nesting, saturated async/row nesting, worker limits across simultaneous queries, exception propagation, and interruption while callbacks are active. These exercise known internal nesting patterns; they cannot guarantee progress for arbitrary application callbacks that wait for every row to start simultaneously.

The benchmark compares the original `ForkJoinPool(cores * 200)` and per-row Future algorithm, a core-sized ForkJoinPool retaining that algorithm, and the actual new row processor. Each variant ran in three separate JVMs with four CPUs enforced by Linux affinity, eight warmup rounds, and twenty measured rounds. Each query traversed a linked set, performed 128 deterministic computation iterations per candidate, and retained a synchronized count, checksum, and top-one score. Every result was checked. Values are medians across the three fork-level median round times; a round completes all concurrent queries.

| Candidates/query | Concurrent queries | Original, ms | Core-sized pool, per-row Futures, ms | Chunked, ms |
| ---: | ---: | ---: | ---: | ---: |
| 1,200 | 1 | 1.318 | 0.799 | 0.272 |
| 1,200 | 4 | 4.415 | 1.410 | 0.772 |
| 20,000 | 1 | 12.685 | 3.042 | 1.443 |
| 20,000 | 4 | 63.857 | 10.128 | 4.932 |

For the last case, median process CPU over twenty rounds fell from 5,090 to 710 to 270 ms. The chunked pool had four background workers; the original had 248–517 at measurement end. A single 20,000-candidate query submitted four background tasks instead of 20,000. Concurrent queries share that allowance and may submit fewer.

A heavier case, with 2,048 computation iterations per candidate, 20,000 candidates, four concurrent queries, and ten measured rounds, produced **115.396 → 87.969 → 69.598 ms**. The smaller improvement when useful row work dominates is an important limit: these are synthetic collection-stage measurements, not an ingestion-throughput prediction. They exclude actual resolver hydration, disk access, and general async workloads. The earlier 55.1% scheduler sample share is not an achievable-speedup estimate.

Reproduce with JDK 23 selected and the project's Kotlin dependencies already cached:

```bash
python3 benchmarks/run-query-row-scheduling.py --cores 4 --work 128 \
  --output /tmp/onyx-query-row-scheduling.jsonl
python3 benchmarks/run-query-row-scheduling.py --cores 4 --rows 20000 \
  --concurrent 4 --work 2048 --rounds 10 \
  --output /tmp/onyx-query-row-scheduling-heavy.jsonl
```

Raw results: [main matrix](../benchmarks/results/query-row-scheduling-2026-09-12.jsonl), [heavier computation](../benchmarks/results/query-row-scheduling-heavy-2026-09-12.jsonl). Public JVM method signatures remain unchanged. An external consumer that casts `defaultPool` to `ForkJoinPool` must adjust that assumption. Previously compiled external consumers with an inlined copy of `parallelForEach` must rebuild to obtain chunking; the rebuilt database collector uses it directly.

**Rolling-window construction now appends and reverses once.** Eight Bar resolvers in each of [Stock-Analyzer/schema.json](/home/tosborn/IdeaProjects/Stock-Analyzer/schema.json) and [Stock-Trader/marketdata.schema.json](/home/tosborn/IdeaProjects/Stock-Trader/marketdata.schema.json) use `acc.add(cursor)` followed by `acc.reverse()`. This removes quadratic array shifting while preserving chronological order, predecessor traversal, and floating-point accumulation order. It does not reduce predecessor reads.

The actual sanitized resolver bodies were compiled and compared against their original versions: **2,064 assertions across 168 comparisons passed**, covering every interval, complete 4,410-minute weekly history, incomplete history, gaps, missing predecessors, volume edge cases, and bit-exact VWAP. [Validation details](../benchmarks/results/rollup-equivalence-2026-09-12.md) record the isolated change. Concurrent edits to calculation caching in these schema files were preserved; they are not part of this implementation or its equivalence claim.

**The mapped WAL now reserves capacity for a normal segment before appending.** [MemoryMappedTransactionStore](../onyx-database/src/main/kotlin/com/onyx/interactors/transaction/impl/MemoryMappedTransactionStore.kt) reserves the 20 MiB rotation threshold plus one growth quantum: 24 MiB on the desktop configuration. This avoids intermediate remapping and the associated data/metadata forces while appenders hold their locks. Gathering writes also avoid re-entering the same channel lock for each buffer and preserve position after a partial failure.

The transaction and channel locks, periodic force interval, frame format, and rotation based on logical length remain intact. The reservation is mapped file capacity, not Java heap. Small active WAL files have a larger apparent length before closing; a clean close truncates to logical bytes. An abrupt shutdown may leave more zero padding to inspect during recovery. Oversized final records can still require mapping growth.

The WAL benchmark uses the real `WholeFileMapping` implementation and temporary files, writing approximately 22 MiB across two WAL segments. It includes opening, appending, forcing, and closing; every persisted byte and final file length is verified. The table gives medians of three alternating measured repetitions after one warmup pair.

| Payload/frame | Initial mapping | Append time, ms | Open + append + close, ms | Mappings | Metadata forces |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 4 KiB | 4 MiB | 42.773 | 82.025 | 5 | 7 |
| 4 KiB | 24 MiB | 7.710 | 53.199 | 2 | 4 |
| 16 KiB | 4 MiB | 39.774 | 78.503 | 5 | 7 |
| 16 KiB | 24 MiB | 7.816 | 51.651 | 2 | 4 |

Total measured time fell approximately **34–35%**. Some work moves to close: median close time increased from about 27 to 31–32 ms. The benchmark performs a due 20 ms periodic force inline, not on the application's background flusher; the slower original run performed one periodic force and the faster reserved run completed its append burst before that deadline. Both include final forcing. These finite-burst results exclude serialization, compression, multiple writers, and real flusher contention. They do not establish an equivalent improvement to live WAL throughput or eliminate ordinary periodic-force contention.

```bash
python3 benchmarks/run-wal-preallocation.py --repetitions 3
```

[Raw WAL results](../benchmarks/results/wal-preallocation-2026-09-12.csv) include opening/closing times and force counts. Build the current database classes first; the runner uses those classes and JDK 23.

**Validation completed: 161 tests passed; four existing listener tests were skipped.** This includes nine row/async concurrency tests, 53 transaction/WAL tests, 97 query/relationship integration tests, and two periodic-flusher tests. The integration coverage includes ordering, pagination, count, subqueries, updates/deletes, lazy queries, cached references, and listeners. Added WAL regressions verify reservation across the rotation threshold, exact persisted bytes, and gathering-write failure recovery. [Suite results](../benchmarks/results/ingestion-execution-tests-2026-09-12.json) combine unique suites and use the final rerun where applicable. The full repository suite was not run.

**Correction to the earlier caching suggestions:** the captured rollback schema already caches predecessor Bars by partition and ID within database/thread calculation state, shares hydrated predecessors across resolver traversals, and reuses derived values with persisted-input validation. Minute saves intentionally clear the calculation cache; updates follow the same invalidation path. It also already maintains session accumulation and persisted recursive indicator state. The profile did not measure cache hit rate or actual one-second expiry events.

Recommendation #1 meant a separate, longer-lived minute-history structure for the raw rolling windows, with correction/replay invalidation—not enabling a cache that is missing. Recommendation #3 concerned the captured v3 getter's one-second expiry potentially replacing state during a still-active calculation. A 22-second batch does not prove any individual minute crossed that expiry. This remains an unmeasured hypothesis, not a demonstrated cause. The local schemas have since received separate v4/active-depth edits. No caching change is attributed to this task, and no remaining caching defect is asserted from the earlier profile.
