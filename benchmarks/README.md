# Database benchmarks

## Large database: memory-mapped versus FILE

With JDK 23 installed, run from the repository root:

```bash
./gradlew :onyx-database-tests:largeStoreBenchmark --console=plain
```

This opt-in test inserts ordinary `entities.PerformanceEntity` rows until their stored
data reaches **100 GB: 100,000,000,000 bytes per database**. It uses the existing
numeric, Boolean, date, and short-string fields, with an 11-character symbol and the
existing `idValue` secondary index. There is no blob, padding, or record-size setting.
Increasing `dataGB` inserts more distinct primary keys; it never changes the record schema.
The actual number of rows and average data-file bytes per inserted row are reported after seeding.

The seed is built once through `saveEntity` using memory-mapped storage, closed, flushed,
and copied to an independent database for `StoreType.FILE`. Seeding and copying are outside
the comparison. Each arm checks that its data and index files use the requested store
implementation, verifies the database row count, and reads samples throughout the seeded
file. The minimum counts bytes added to the data store, including record frames and
serialized index values, excluding the initial system metadata, the separate index file,
and unused mapping reservations. After closing, the test verifies the
actual data-file size against its logical allocation header.

Four rounds each measure 50,000 random `findById` calls and 50,000 `saveEntity` updates,
with 5,000 warmup operations per phase. Both engines receive identical random keys and
values; their order alternates each round. Every updated record is checked after reopening,
and the actual row count must remain equal to the seed count. Expected revisions are kept
only for updated keys, so tracking memory scales with the workload rather than the corpus.

`results.csv` records throughput, p50/p95/p99 latency, checksums, GC, and separate write
finalization time. `storage.csv` records actual row counts and data/index/database sizes
in bytes after seeding and each round. `report.txt` contains settings and median throughput.
Reports are retained under a unique directory in `build/benchmarks/large-store/`.
Successful runs remove their scratch databases by default; failed runs retain them for
diagnosis. Normal `test` and `check` runs skip this benchmark.

These are single-threaded database operations, including serialization, primary and
secondary indexes, validation, allocation, and GC. Updates allocate replacement records,
so file size may grow while the live row count stays fixed. WAL journaling is disabled.
Finalization includes close/commit and `FileChannel.force(true)`; there is no fsync per
operation. The OS page cache is not cleared, and both engines share one JVM. Interpret
these as database-operation timings, not raw device IOPS or controlled cold-cache results.

A full run needs space for **two 100 GB data files plus both sets of indexes**. It checks
available space before starting, projects index overhead during seeding, and reserves
another 10 GB. Seeding hundreds of millions of small rows can take substantial time;
progress reports include the actual record count and data bytes written. The test
JVM uses a 2 GiB maximum heap.

```bash
# Small correctness check with the same schema; explicitly below the 100 GB minimum.
./gradlew :onyx-database-tests:largeStoreBenchmark -PlargeStoreBenchmark.smoke=true

# Select a filesystem and retain the completed databases.
./gradlew :onyx-database-tests:largeStoreBenchmark \
  -PlargeStoreBenchmark.dataGB=100 \
  -PlargeStoreBenchmark.directory=/path/to/benchmark-disk \
  -PlargeStoreBenchmark.keepDatabases=true
```

Other properties are `largeStoreBenchmark.operations`, `.warmupOperations`, `.rounds`,
and `.seed`. `dataGB` uses decimal gigabytes and must be at least 100 for normal runs.
Environment equivalents include `ONYX_LARGE_STORE_BENCHMARK_DATA_GB` and
`ONYX_LARGE_STORE_BENCHMARK_OPERATIONS`; Gradle properties take precedence. The old
`payloadBytes` and `dataGiB` options are rejected. Repeat on a quiet machine for independent
JVM runs; there are no timing assertions.

## HNSW save/cache

With `JAVA_HOME` pointing to JDK 23, run from the repository root:

```bash
python3 benchmarks/run-hnsw-save.py --forks 3 --storage mapped --kinds random
```

This builds the current database and an opt-in benchmark entry point in test sources. It runs
each case in a separate JVM, serially, rotating implementation order between forks. The default
comparison changes only the decoded-node cache: `production` uses the actual bounded CLOCK cache;
`lru` replaces it with the previous `ConcurrentLinkedHashMap`, at the same capacity, before any
operations. Both use the current pruning code. `--caches weak lru production` also includes the
older `OptimisticLockingMap(WeakHashMap())`. No unbounded strong `HashMap` is substituted.

Each child warms up 2,000 inserts in a separate graph, then builds an untimed prefix of the
measured graph. By default it measures the final 4,000 ordinary `PersistentHnswIndex.upsert` calls
in a 12,000-node graph. Vectors are prepared outside the timer using a fixed seed. The mapped case
uses real Onyx memory-mapped `DiskMap`s with the existing equal-size overwrite mode, in a temporary
directory that is deleted after the run. The memory case uses a `TreeMap` behind the same `DiskMap`
interface. Node storage operations are counted in both cases.

The runner checks exact graph-byte hashes, exact query-result hashes, and identical node-write
counts across variants and forks. Each child also validates graph connectivity invariants and
checks query results during warmed one-thread and eight-thread searches. A separate post-GC probe
checks retention of 256 recently read nodes; explicit GC never occurs inside a timed workload.
Results include mean and percentile save times, allocation, GC time, backing node reads/writes,
and search throughput. A separate cache-hit benchmark uses up to 4,096 resident boxed keys,
400 ms of warmup per worker, and two seconds of timed hits at one and eight threads. It accesses
the actual node cache directly and excludes graph locks, vector scoring, and storage. The
benchmark records the concrete cache class and capacity and does not assert timing thresholds.

These are measurements of the HNSW stage of normal saves. They exclude embedding generation,
entity serialization, WAL durability, HTTP handling, and compression orchestration. Mapped runs
include index-map writes but do not force an fsync per save. The runner never opens the live database.

Useful variations:

```bash
# Compare CLOCK against exact LRU with wider, strongly clustered vectors.
python3 benchmarks/run-hnsw-save.py --forks 3 --storage mapped --kinds clustered --dimensions 688

# Reproduce the measured eviction case with a deliberately smaller cache.
python3 benchmarks/run-hnsw-save.py --forks 3 --storage memory --kinds random --rows 24000 --capacity 16384

# Test a different capacity without changing the application or its running service.
python3 benchmarks/run-hnsw-save.py --capacity 32768 --rows 24000 --storage memory --kinds random
```

`--output` selects the raw JSON result file (default `/tmp/onyx-hnsw-save-benchmark.json`).
`--baseline-classpath` optionally adds a third variant using a saved pre-change build's main
classes. The path is prepended only in baseline children; the same benchmark and dependencies
run in all forks. Save the classes **before** editing the index if measuring the combined change:

```bash
./gradlew :onyx-database:classes
cp -a onyx-database/build/classes /tmp/hnsw-before-classes
# Apply the index changes, then:
python3 benchmarks/run-hnsw-save.py --storage mapped --kinds random \
  --baseline-classpath /tmp/hnsw-before-classes/kotlin/main:/tmp/hnsw-before-classes/java/main
```

Run on a quiet machine for release comparisons. The defaults use a 512 MiB initial/2 GiB maximum
heap, eight JVM processors, and uncompressed object references; all JVM arguments and individual
fork results are recorded. Cache capacity is an entry limit per index, so retained memory depends
on dimensions and the number of active indexes. A bound below the working set can increase reads;
test the intended capacity and corpus size together.

The current default is 32,768 entries. See the [CLOCK comparison](../docs/hnsw-clock-performance.md)
and the [earlier LRU results and capacity tradeoffs](../docs/hnsw-save-performance.md).

## Quantized cosine benchmark

With `JAVA_HOME` pointing to JDK 23, compare the real cosine implementation and vector construction:

```bash
python3 benchmarks/run-quantized-cosine.py --forks 3 --dimensions 688 1536
```

Each serial JVM fork warms up and measures `QuantizedCosineVector.cosineSimilarity` and `fromBytes`
separately, using 1,024 seeded vectors. It reports median nanoseconds and allocated bytes per operation.
The runner alternates scalar and SIMD forks and checks exact score and construction checksums. Add
`--baseline-classpath` with saved pre-change main classes, as above, to measure the previous implementation
in the same harness. Results default to `/tmp/onyx-quantized-cosine-benchmark.json`.

SIMD uses the optional JDK Vector API with signed bytes widened to integer lanes. Enable it in applications
with `--add-modules=jdk.incubator.vector`. Without the module, or with preferred integer vectors narrower
than 256 bits, scoring uses the scalar implementation. Storage encoding and score rounding are unchanged.
Android also uses the scalar implementation, without accessing Java's module API or loading the SIMD helper.
The JAR bundles a consumer rule for Android shrinkers to ignore only the absent optional `jdk.incubator.vector`
classes; applications need no additional keep rules for this accelerator.
The ordinary `:onyx-database:test` task exercises the scalar path; `:onyx-database:vectorTest` runs the vector
and HNSW regression tests with the module enabled.

To measure the combined cosine and HNSW improvements, including reuse of prepared vectors during neighbor
updates, pass `--vector-api` to the save benchmark:

```bash
python3 benchmarks/run-hnsw-save.py --vector-api --caches production --dimensions 688 \
  --baseline-classpath /tmp/hnsw-before-classes/kotlin/main:/tmp/hnsw-before-classes/java/main
```

The isolated cosine benchmark does not predict total query or save throughput; use the HNSW benchmark for
that stage, and application measurements for serialization, storage durability, and request overhead.
See the [measured results and Android validation](../docs/quantized-cosine-performance.md).

## Compression graph-copy benchmark

With JDK 23 selected, compare reconstructing a graph from stored vectors against copying its
existing topology and remapping every record reference:

```bash
python3 benchmarks/run-hnsw-clone.py --rows 5000 --dimensions 384 --forks 3
# Exercise a graph larger than the default decoded-node cache.
python3 benchmarks/run-hnsw-clone.py --rows 40000 --dimensions 384 --forks 1
```

Each fork builds an untimed source graph, then times reconstruction and copying into separate
temporary memory-mapped stores, including a final flush. It checks one write per copied node,
zero source neighbor lookups during copying, graph invariants, remapped query results, and
reopening the copied graph. Results are written to `/tmp/onyx-hnsw-clone-benchmark.json` unless
`--output` specifies another path. This measures the graph stage only; entity copying, lexical
posting construction, embedding inference, and WAL work are excluded. The full compression
speedup depends on how much time those remaining stages take.
