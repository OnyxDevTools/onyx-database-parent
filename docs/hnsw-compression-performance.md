# HNSW compression optimization

Compression previously saved every copied searchable record through automatic embedding and
HNSW insertion. The live OnyxGemma profile showed graph traversal, node decoding, and eviction
from the 32,768-entry decoded-node cache on the compression workers.

For empty destination partitions without relationship cascades, compression now preserves the
stored vector representation, builds posting routes, and copies the existing HNSW graph while
remapping record references. Graph nodes, levels, vectors, and edges are retained. Each graph
node is written once, without neighbor searches or embedding/resolver execution. Ordinary
inserts and updates retain their existing behavior. Nonempty destinations and partitions with
relationships use the ordinary merge path.

The source must be offline and the destination unpublished. A reference map is retained for the
vector records in one partition at a time; entity payloads are streamed. Missing or duplicate
references, incompatible metadata, and copy failures propagate to compression rollback rather
than allowing a partial copy to be reported as successful.

## Measurement

Measured on September 10, 2026, using temporary memory-mapped stores, JDK 23.0.2, a 2 GiB maximum
heap, four JVM processors, and uncompressed object references. The 40,000-node case exceeds the
default graph cache capacity. Source construction is untimed; both measured paths include a
final store flush.

| Graph stage, 40,000 vectors × 384 dimensions | Reconstruct | Copy topology |
| --- | ---: | ---: |
| Elapsed time | 122.389 s | 0.194 s |
| Node writes | 1,478,740 | 40,000 |

This single-fork measurement is about 630× faster for the graph stage, with about 37× fewer
node writes. It excludes entity copying, posting construction, embedding inference, WAL work,
and deployment orchestration; it is not a measurement of total database compression time.

The benchmark validates graph invariants, remapped search results, one write per copied node,
absence of source neighbor lookups during copying, and reopening the graph. The 65 targeted
tests also cover both file store modes, partitioned cloning, stored-vector equality, resolver
and embedding preservation, normal updates, merge fallback, legacy graph nodes, and failures.

Reproduce with JDK 23 selected:

```bash
python3 benchmarks/run-hnsw-clone.py --rows 40000 --dimensions 384 --forks 1
```

Use multiple forks on a quiet machine for release comparisons. The runner records raw timings,
storage operation counts, JVM options, and the graph implementation's source hash in JSON.
