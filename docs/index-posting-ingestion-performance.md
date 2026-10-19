# Index posting performance during ingestion

Investigated September 12, 2026 against the running cloud preview ingestion, then deployed
to preview at 23:31:34 UTC after the user's approval. Live inspection used thread samples,
schema reads, ingestion checkpoints, and bounded timestamp-range queries.

## Confirmed query

Both active ingestion virtual threads were sampled inside `HashMap.put` from
`DefaultIndexInteractor.findAll`, called by `IndexScanner.scan(existingValues)` and the
generated `Bar.calculateSessionAggregateFast` resolver:

```kotlin
db.from("Bar")
    .inPartition(partitionId)
    .where(gte("timestamp", openAt))
    .and(lt("timestamp", closeAt))
    .and(eq("interval", "MINUTE"))
    .orderBy(desc("timestamp"))
    .limit(1)
    .firstOrNull<Bar>()
```

The bounds are 09:30 inclusive and 16:00 exclusive in America/New_York for the observed
market date. Each after-hours minute runs this query to verify that the cached session
aggregate reaches the persisted regular-session endpoint. Removing the verification would
change behavior for partial caches, late writes, and updates.

The live resolver matched `Stock-Analyzer/schema.json` at inspection time. The ingestion
checkpoints were approximately 598,000 AAPL minutes and 584,000 AMD minutes. Completed-session
timestamp queries returned exactly **3,510 records per symbol: 390 minutes × nine intervals**.
Rollups are persisted every minute, so the timestamp candidate domain is larger than 390.

The old scanner enumerated the entire `interval = MINUTE` posting list even though the
timestamp range had already supplied its candidate set. It then allocated a reference list
and a reference hash set before intersecting them with those candidates. `LIMIT 1` did not
bound this work.

## Changes

- `DiskIndexPostingMap` checks the upper tuple bound once per complete leaf and binary-searches
  the terminal leaf. It no longer compares or decodes the indexed value for every posting.
- Decoded object values now populate the existing token cache on reads. Previously, cold
  duplicate string postings could deserialize the same token repeatedly.
- `IndexPostingPage` reads and decodes occupied slots instead of full page capacity.
- `DefaultIndexInteractor.findAll` retains record IDs in a primitive `LongArray` snapshot with
  implicit null values, replacing boxed hash keys and per-entry hash nodes. The snapshot is
  detached, has immutable views, and uses binary search for key membership. Scalar snapshots
  bypass the scanner's search-score extraction loop.
- `IndexScanner.scan(existingValues)` probes scalar equality/IN membership directly when the
  incoming domain requires at most **4,096 probes**, including all IN values and partitions.
  The observed 3,510-candidate session qualifies. Larger domains and unsupported custom indexes
  retain the ordinary scan. Matches are staged before collection so fallback cannot partially
  collect results. Native normalization, nullable custom-index semantics, and partition identity
  remain intact.

The disk format is unchanged. Counts, ordering, and pagination still use the normal collector;
the full regular-session regression returns one newest minute while reporting 390 matches.

## Isolated measurements

JDK 23, `-XX:-UseCompressedOops`, on the preview host while other workloads continued running.
These are synthetic component measurements, not a before/after measurement of cloud requests
or ingestion throughput. The stages overlap and their speedups must not be multiplied.

| Measurement | Before | After |
|---|---:|---:|
| Warm traversal of 600,000 `MINUTE` postings | 2.429 ms | 0.379 ms |
| Cache-cold traversal of those postings | 48.300 ms | 8.735 ms |
| Filter 3,510 candidates down to 390 matches | 145.661 ms | 0.460 ms |
| Bytes allocated by that filtering stage | 250,864,776 | 341,064 |
| Build a 600,000-ID result snapshot, without traversal | 11.136 ms | 0.779 ms |
| Bytes allocated by snapshot construction | 55,177,424 | 16,777,416 |

The traversal/filter benchmark uses real native posting trees in isolated in-memory stores,
600,000 six-character `MINUTE` postings, and 3,120 other postings. Its original path builds
`HashMap<Long, Any?>`, `List<Reference>`, and `HashSet<Reference>` before intersection; the new
path issues 3,510 native `contains` probes. Both return the same 390 records. The benchmark is
single-threaded; it does not exercise concurrent in-memory entity hydration.

Warm/filter timings are medians of 21 observations after warmup; cold traversal uses five
observations, clearing caches and requesting GC only in the isolated benchmark JVM before
timing. The JVM has a 256–768 MiB heap. Allocation-induced weak-cache turnover is included in
the original filter measurement and contributes to its cost. The separate snapshot benchmark
uses a 512 MiB heap, ten warmups, and 15 alternating observations.

The baseline is commit `c9781938ffd291ad365fbcb0f9f5bc29f9e5db9a`. Reproduce the representative
native/filter measurement with JDK 23 and locally cached Gradle dependencies:

```bash
./gradlew :onyx-database:classes
python3 benchmarks/run-index-posting-query.py
```

The [runner](../benchmarks/run-index-posting-query.py) compiles renamed baseline tree/page
classes beside the current implementation and executes the
[harness](../benchmarks/IndexPostingQueryFilterBenchmark.kt) in a child JVM. Generated files
and stores are separate from the running database.

## Verification and remaining opportunities

Tests cover inclusive/exclusive tuple bounds, cache eviction, duplicate object values, physical
visit limits, occupied page slots, snapshot contracts, normalized keys, and detached snapshots.
Query regressions cover the actual 3,510-candidate session, multiple partitions, duplicate IN
values, null/custom-index fallback, the probe budget, total counts, and eager/lazy/cached pages.
The final selected suite passed 215 tests with four skipped, including existing query, index,
update/delete, partition, listener, and pagination regressions; `git diff --check` passed.
The session integration fixture uses production memory-mapped storage. A larger exploratory
in-memory test exposed an independent shared-ByteBuffer concurrent-read problem in
`InMemoryStore`; that store was not changed by this patch.

The fallback session resolver also uses `timestamp < dayStart AND interval = MINUTE ORDER BY
timestamp DESC LIMIT 1000`. Its unbounded timestamp domain can exceed the probe budget and
still performs broad scans. Descending range traversal with residual filtering is the next
general improvement. Stopping at the page limit additionally requires an explicit first-match
or no-count contract, or an independent accurate count, because existing queries report the
complete result count. A persisted session endpoint with mutation-aware invalidation could
also avoid repeated after-hours checks. Neither behavior change is included here.

HNSW work and repeated predecessor hydration were also present in live samples; reducing scalar
posting work does not eliminate those ingestion costs.

## Preview deployment

The preview uses a separate core checkout under `/home/tosborn/OnyxWorkspace`. The six changed
production sources and their tests were copied there, and the same 215 tests passed with four
skipped. The staged cloud JAR passed another 21 focused tests with its own embedded dependencies.
Only the 28 compiled classes belonging to the patch were replaced; the other 87,219 JAR entries
were verified byte-identical. This avoided deploying unrelated cloud or HNSW working-tree edits.

The ingestion client was paused while its two current Bar requests completed successfully.
After shutdown, the artifact was replaced atomically and preview started under systemd as PID
430185. Health returned 200, and ingestion resumed. Live endpoint queries returned the expected
AAPL/AMD session endpoints and counts of 390. The resolved database, memory-mapped storage,
port, Java installation, and 96 GiB heap matched the previous process.

AMD's following checkpoint save encountered a stale connection and exited that symbol because
the original client does not retry checkpoint writes. AAPL continued. A dedicated recovery worker
(PID 432157) resumes AMD from its persisted 599,000 checkpoint, replaying deterministic IDs as
needed; details and worker status are recorded in the deployment manifest. The service helper also closed an idle validator
JVM whose main had already completed.

At 23:37:03 UTC, AMD advanced to checkpoint 600,000, AAPL reached 620,000, and AMZN reached
17,000. Health remained 200; the AMD recovery worker had no logged errors or retries.

The original artifact, source/class hashes, drained-request evidence, and live validation are
under `/tmp/onyx-index-posting-deploy-20260912/`; its `manifest.json` records the deployed artifact
SHA-256 and ingestion recovery status. The original artifact is `onyx-cloud-api-before.jar`.
