# CLOCK node-cache benchmark

HNSW now uses `ConcurrentClockCache` in place of `ConcurrentLinkedHashMap`.
[CLOCK / second-chance eviction](https://web.stanford.edu/class/cs346/2014/Lecture_One.pdf)
approximates LRU using reference bits instead of maintaining exact access order.

In this implementation, a hit performs a `ConcurrentHashMap` lookup and sets the entry's volatile
reference bit only if it is clear. It never acquires the cache writer lock or changes ring links.
Insertions, replacements, removals, and eviction use one writer lock. Eviction scans the ring,
clearing reference bits and preferring unreferenced entries. A scan stops after at most one full
revolution even if concurrent readers keep marking entries. The capacity remains **32,768 entries
per index**, configurable through `-Donyx.hnsw.nodeCacheCapacity=<positive count>`.

The prior pruning shortcut, mutation working set, graph encoding, and persistence path are intact.
The existing `ConcurrentLinkedHashMap` utility remains available to its other callers and serves
as the benchmark control.

## Results at the default capacity

Three fresh JVM forks per implementation, serially executed in rotating order; identical random
384-dimensional vectors; a 12,000-node graph; real memory-mapped Onyx `DiskMap`s. The timer covers
the final 4,000 normal HNSW upserts, after a separate 2,000-insert warmup graph and an untimed
8,000-insert prefix. Both implementations use the same 32,768-entry limit and pruning code.
Numbers below are medians across forks.

| Measurement | Exact LRU | CLOCK | Comparison |
|---|---:|---:|---|
| Cache hits/sec, one thread | 30.8 million | 287.6 million | 9.3× throughput |
| Cache hits/sec, eight threads | 19.9 million | 1,513.2 million | 76.2× throughput |
| HNSW searches/sec, one thread | 12,214 | 13,533 | 10.8% higher throughput |
| HNSW searches/sec, eight threads | 12,616 | 92,787 | 7.4× throughput |
| Milliseconds/HNSW save | 1.762 | 1.693 | 3.9% less time |
| Backing node reads/save | 1.000 | 1.000 | Same |

The cache-hit measurement uses up to 4,096 resident boxed keys in the actual node cache, a
400 ms warmup per worker, and two timed seconds. It excludes graph locking, vector scoring,
storage, and key allocation. Eight-thread throughput is aggregate across all workers. It should
not be interpreted as complete HNSW or API throughput. HNSW search measurements do execute the
full traversal and score calculation, with 4,096 timed searches per worker and exact score checks.

The clear improvement is in cache access and concurrent traversal. Save times remain dominated
by other index work and vary on this shared host:

| Fork | Exact LRU ms/save | CLOCK ms/save |
|---|---:|---:|
| 1 | 1.470 | 1.693 |
| 2 | 1.762 | 1.570 |
| 3 | 1.961 | 1.704 |

The small median save improvement is not a consistent improvement in every fork.

## Eviction pressure

A second three-fork cohort deliberately reduces both caches to **4,096 entries** with the same
12,000-node graph, forcing frequent eviction. Both implementations stayed within that bound.

| Measurement | Exact LRU | CLOCK | Comparison |
|---|---:|---:|---|
| Milliseconds/HNSW save | 3.574 | 4.146 | CLOCK takes 16.0% longer |
| Backing node reads/save | 1,138.387 | 1,140.519 | CLOCK performs 0.2% more reads |
| HNSW searches/sec, eight threads | 7,703 | 13,629 | CLOCK has 76.9% higher throughput |
| Cache hits/sec, eight threads | 20.0 million | 1,270.4 million | 63.4× throughput |

CLOCK retained nearly the same cache effectiveness here. Eviction-heavy saves were slower
despite similar backing-read counts. The default capacity remains 32,768; size it for the active working set and the memory
available across indexes. The entry limit bounds count, not bytes.

## Correctness and scope

All twelve benchmark forks produced identical encoded graph and query-result hashes, including
across capacities. Node write counts were also identical. After an untimed GC, both caches served
all 256 recently read probe nodes without backing reads.

All **42 focused tests** passed. They cover second-chance eviction, strict capacity, replacement,
removal, clear/reuse, backed views, stale iterators, concurrent reads/writes/clears, graph
compatibility, cache eviction during mutations/searches, persistence failures, normal entity
saves, updates/deletes, reopen, and rebuild. A deterministic test pauses a writer while it holds
the cache lock and verifies that hits and misses still complete on another thread.

These are local measurements of cache operations and the HNSW stage of saves/searches. They
exclude embeddings, other entity processing, HTTP handling, WAL durability, and compression
orchestration. Temporary mapped databases include ordinary index-map writes without forcing
fsync per save. The running preview service and its database were not modified.

Environment: AMD RYZEN AI MAX+ 395, Linux, Temurin 23.0.2+7, with
`-Xms512m -Xmx2g -XX:-UseCompressedOops -XX:ActiveProcessorCount=8` in each child JVM.
Other preview workloads remained active, so individual timings have substantial variation.

## Reproduce

With `JAVA_HOME` pointing to JDK 23:

```bash
python3 benchmarks/run-hnsw-save.py --forks 3 --rows 12000 --measured 4000 \
  --kinds random --storage mapped --caches lru production

python3 benchmarks/run-hnsw-save.py --forks 3 --rows 12000 --measured 4000 \
  --kinds random --storage mapped --caches lru production --capacity 4096
```

The control installs the actual `ConcurrentLinkedHashMap` before any graph operation; production
keeps the actual `ConcurrentClockCache`. The runner records class names, capacities, source
hashes, all fork measurements, graph hashes, and query hashes. See the
[runner instructions](../benchmarks/README.md) and raw results:

- [32,768-entry capacity](../benchmarks/results/hnsw-clock-2026-09-09/mapped-random-12000-cap32768.json)
- [4,096-entry capacity](../benchmarks/results/hnsw-clock-2026-09-09/mapped-random-12000-cap4096.json)
