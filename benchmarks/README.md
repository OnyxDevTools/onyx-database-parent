# HNSW save/cache benchmark

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
