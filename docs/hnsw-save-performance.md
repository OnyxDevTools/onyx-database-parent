# HNSW save performance: pruning and bounded LRU

This report records the earlier weak-key-to-LRU change. The current decoded-node cache uses
CLOCK approximate LRU; see the [CLOCK comparison](hnsw-clock-performance.md) for that replacement.

Measured September 9, 2026 on the preview host. The changes reduce time spent in the HNSW stage
of ordinary saves. The final 32,768-entry LRU reduced median save time by **20.4%** against weak
keys in the 24,000-node benchmark with pruning held constant. The 12,000-node mapped-storage
comparison measured **41.3% less save time for both changes together**.

## Implementation

- `selectForNode` skips diversity distances when the candidate list cannot exceed the node's
  degree limit. The original heuristic fills remaining slots from rejected candidates, so all
  eligible candidates would survive. Missing nodes, calibration mismatches, and incompatible
  levels are still filtered. Overflowing lists retain the existing pruning behavior.
- The decoded-node cache uses the existing `ConcurrentLinkedHashMap`, with strong keys and strong
  values, access-order eviction, and a mutex for individual cache operations. Its default maximum
  in that version was **32,768 entries per index**, configurable with `-Donyx.hnsw.nodeCacheCapacity=<positive count>`.
  The mutation working set and persistence ordering remain intact.

## Ordinary index-save measurements

Each number below is the median of three independent JVM forks' mean time per save. Each fork
measured the last 4,000 inserts, following an untimed prefix and a separate 2,000-insert warmup
graph. Every implementation used identical precomputed random 384-dimensional vectors, IDs,
insertion order, JVM settings, and storage implementation within its cohort.

The 12,000-node cohort used real Onyx memory-mapped `DiskMap`s. The LRU capacity in this cohort
was 16,384, sufficient for the whole graph.

| Implementation | ms/save | Individual fork means | Node reads/save | Allocated bytes/save |
|---|---:|---|---:|---:|
| Before either change, weak keys | 2.521 | 2.521, 2.547, 2.440 | 105.346 | 3,335,608 |
| Pruning shortcut, weak keys | 1.883 | 2.000, 1.660, 1.883 | 78.773 | 2,416,362 |
| Pruning shortcut + actual LRU | 1.481 | 1.481, 1.358, 1.540 | 1.000 | 1,943,650 |

Pruning alone reduced median save time by 25.3%. Changing only the cache then reduced it by a
further 21.4%; the combined reduction was 41.3%. All variants performed exactly **37.044 node-map
writes/save** in the measured interval. This improvement did not come from skipping persistence.
The remaining one node read/save with the LRU is the initial lookup of the new, absent record ID.

The larger 24,000-node cohort used memory-backed `DiskMap` fixtures to isolate index CPU,
allocation, and cache behavior. Both variants used the pruning shortcut. The **32,768-entry
bounded LRU** was supplied through the JVM capacity setting; this is the final default capacity.

| Implementation | ms/save | Individual fork means | Node reads/save | Allocated bytes/save |
|---|---:|---|---:|---:|
| Weak keys | 1.772 | 1.746, 1.772, 2.465 | 283.650 | 3,333,364 |
| Actual LRU, capacity 32,768 | 1.411 | 1.366, 1.411, 2.165 | 1.000 | 1,946,519 |

The LRU used **20.4% less time** by the median comparison and was faster in every matched fork
(12.2–21.8% less time). Median allocation fell 41.6%. The cache retained 24,000 nodes after saves.
The test used the production LRU implementation, including access-order updates and locking.
No unbounded strong `HashMap` was substituted.

## Capacity changes the result

A separate three-fork test deliberately limited the LRU to 16,384 entries for the same 24,000-node
memory workload. Both sides used the pruning shortcut:

| Implementation | ms/save | Node reads/save |
|---|---:|---:|
| Weak keys, matched control cohort | 1.855 | 306.171 |
| Actual LRU, capacity 16,384 | 2.070 | 466.798 |

That undersized LRU was **11.6% slower** by median save time, and its observed entry count stayed
at 16,384. An earlier exploratory 4,096-entry LRU also regressed on a 10,000-node graph. These
results are the reason for the larger, configurable default.

A fixed entry limit should be sized for the active working set and available memory. A larger
working set can produce the same eviction cost at 32,768 entries. Retained bytes depend on vector
dimensions and the number of active indexes; the limit applies separately to each index.

## Concurrent search and correctness

Concurrent search results matched exactly in every fork, including cases that evicted nodes.
Throughput results were mixed:

| Cohort | Weak-key median QPS, 8 threads | LRU median QPS, 8 threads |
|---|---:|---:|
| 12,000 nodes, mapped, LRU capacity 16,384 | 9,267 | 8,172 |
| 24,000 nodes, memory, LRU capacity 16,384 | 18,604 | 20,656 |
| 24,000 nodes, memory, LRU capacity 32,768 | 16,513 | 16,609 |

The first cohort used 512 timed searches/thread. The larger-graph cohorts increased this to
4,096 searches/thread after the short samples showed substantial variation. For the final
32,768-entry configuration, individual LRU forks ranged from 9,053 to 16,672 QPS; weak-key forks
ranged from 14,375 to 19,235 QPS. One matched LRU fork was 37% slower. The similar final medians
do not establish parallel-search performance parity. The LRU mutex adds contention, and this
shared, busy host also produced considerable timing variation. The measured save improvement
is clearer than the query-throughput result.

Verification includes:

- Identical encoded graph hashes, query-score hashes, and node-write counts across all variants
  in each cohort. The two 24,000-node capacity cohorts also produced identical hashes.
- A separate forced-GC probe of 256 recently read nodes: weak keys required 256 backing reads;
  the LRU required zero. This GC probe ran only in benchmark children, outside timing intervals.
- 36 focused tests covering graph compatibility, bounded degrees, missing/incompatible neighbors,
  actual LRU eviction/access order, mutation working sets, concurrent searches, normal entity
  saves, updates/deletes, persistence failures, mapped storage, reopen, and rebuild.

## Scope and reproduction

These timings cover actual `PersistentHnswIndex.upsert` calls, not complete HTTP/API saves.
They exclude embedding generation, entity serialization, WAL durability, and compression
orchestration. Mapped-storage measurements include ordinary index-map writes without a forced
fsync per save. The benchmark created temporary databases and did not access the running
preview database. The pre-change baseline already contained the working tree's coalesced writes
and fixed-size graph frames; only the two changes described above differ in this comparison.

Environment: AMD RYZEN AI MAX+ 395, Linux, Temurin 23.0.2+7. Child JVMs used
`-Xms512m -Xmx2g -XX:-UseCompressedOops -XX:ActiveProcessorCount=8`.
Forks ran serially with rotating implementation order. Other preview workloads continued running
on the host, so these measurements are not a prediction of end-to-end API latency.

See [benchmark instructions](../benchmarks/README.md) and the
[runner](../benchmarks/run-hnsw-save.py). Raw results preserve every fork:

- [12,000 nodes, mapped, three variants](../benchmarks/results/hnsw-save-2026-09-09/mapped-random-12000.json)
- [24,000 nodes, memory, capacity 16,384](../benchmarks/results/hnsw-save-2026-09-09/memory-random-24000.json)
- [24,000 nodes, memory, capacity 32,768](../benchmarks/results/hnsw-save-2026-09-09/memory-random-24000-cap32768.json)

The recorded source hash corresponds to both changes with a 16,384-entry constructor default;
the final cohort overrides it to 32,768. The final source makes that tested value the default.
The pre-change source SHA-256 is
`66b20772c024239288f533a7d7aa30f016b9873c03b9bdad02f959ab32bd3a9e`.
