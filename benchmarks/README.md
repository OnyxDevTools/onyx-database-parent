# Query row scheduling and WAL reservation benchmarks

With JDK 23 selected and the project dependencies cached, compare the original oversized
ForkJoinPool, a core-sized pool with per-row Futures, and the current chunked row processor:

```bash
python3 benchmarks/run-query-row-scheduling.py --cores 4 --work 128 \
  --output /tmp/onyx-query-row-scheduling.jsonl
python3 benchmarks/run-query-row-scheduling.py --cores 4 --rows 20000 \
  --concurrent 4 --work 2048 --rounds 10 \
  --output /tmp/onyx-query-row-scheduling-heavy.jsonl
```

The runner compiles the current processor into temporary classes and runs isolated JVMs
serially. On Linux it enforces CPU affinity as well as the JVM processor count. Each query
checks count, checksum, and top-one retention. This measures collection scheduling and synthetic
row computation, excluding application disk access, resolvers, and general async work.

To compare 4 MiB and 24 MiB initial WAL mapping capacity, first build the current database
classes, then run:

```bash
python3 benchmarks/run-wal-preallocation.py --repetitions 3
```

This uses the actual `WholeFileMapping` implementation and disposable files. It verifies every
persisted byte and logical file length after writing across a 20 MiB rotation threshold.
Results include opening, appending, closing, and forcing. Its inline periodic force does not
model live flusher scheduling or concurrent writers. Neither benchmark connects to a running
database. See [the measurements, validation, and limitations](../docs/ingestion-execution-performance-20260912.md).
