# Quantized cosine performance and portability

Measured on September 12, 2026 with Temurin JDK 23.0.2 on an AMD Ryzen AI MAX+ 395 workstation.
These are warmed local benchmarks with other work active on the host, so compare medians and allow
for run-to-run variation. They are not production service latency measurements.

The change adds an optional Vector API dot product and squared-norm reduction, transferring fresh
quantization arrays directly to their owner. HNSW now retains each immutable prepared vector across
neighbor changes, retains the vector already prepared during decoding/insertion, and reuses candidate
scores during diversity selection. The byte encoding, quantization version, score normalization order,
search budgets, and persisted graph format remain unchanged.

## Real vector operations

Three alternating JVM forks per variant, 1,024 seeded candidates, two seconds of warmup per operation,
and seven measured batches per fork. Values below are medians across forks in nanoseconds per operation.
The baseline is the captured pre-change main classes; all variants use the same benchmark class and dependencies.

| Dimensions | Operation | Before | Current scalar | Current SIMD | SIMD speedup |
|---:|---|---:|---:|---:|---:|
| 688 | `cosineSimilarity` | 40.2 | 41.9 | 22.1 | 1.82x |
| 688 | `fromBytes` | 361.1 | 89.6 | 69.5 | 5.20x |
| 1536 | `cosineSimilarity` | 91.6 | 92.1 | 44.0 | 2.08x |
| 1536 | `fromBytes` | 785.3 | 117.8 | 93.8 | 8.37x |

All score and retained-construction checksums matched exactly. Scoring allocated zero bytes per operation.
`fromBytes` still owns one defensive byte-array copy; the faster construction comes from its integer norm
reduction. The original long scalar comparison loop is retained because simply narrowing its accumulator
to `Int`, or sharing the constructor helper, can regress HotSpot optimization. SIMD widens signed bytes
before multiplying; the maximum possible absolute reduction is only 2^28 at 16,384 dimensions.

## HNSW stage

Three alternating forks per variant, random 688-dimensional vectors, 4,000-node memory-backed graphs,
and the final 1,000 inserts measured. SIMD was enabled in both forks (the old implementation ignores it).
The runner verified identical graph bytes, query-result bits, and node-write counts in every fork.

| Metric | Before | Current |
|---|---:|---:|
| Milliseconds per insert | 1.494 | 1.130 |
| Queries/second, one thread | 10305.962 | 8952.270 |
| Queries/second, eight threads | 87518.730 | 86839.271 |
| Allocated MiB per insert | 1.944 | 2.304 |

Insert time fell about 24%, while allocation per insert increased about 18.5%. Search throughput
did not consistently improve: the one-thread median fell about 13%, and the eight-thread median
was nearly unchanged. These are measured tradeoffs; the isolated cosine speedup does not establish
an overall search throughput gain. A separate SIMD microbenchmark with uncompressed object references
still allocated effectively zero bytes per comparison, so the HNSW allocation increase is not explained
by a general allocation problem in the SIMD kernel.

These measurements cover graph operations, not embedding generation, HTTP, entity serialization,
WAL durability or disk fsync. The small graph fits the node cache; larger datasets and cache misses
can change the result. Allocation and multi-thread throughput are reported separately rather than
assuming the arithmetic speedup applies to the entire request.

## SIMD-disabled and Android behavior

Applications opt into SIMD with `--add-modules=jdk.incubator.vector`. Without that module, with
unavailable dependencies, or with preferred integer vector widths below 256 bits, scoring falls back
to the scalar loop. The availability probe uses `Class.forName`, not Java's module-system APIs.
Android therefore takes the scalar path without loading the SIMD implementation. The JAR includes
`META-INF/proguard/onyx-vector.pro` to suppress only missing optional Vector API types during shrinking.
The cloud API's existing `run_api.sh` already enables the module.

Validation on the final code:

- 32 vector/HNSW regression tests passed without the Vector API and again with it enabled.
- 23 HNSW/resolver integration tests passed.
- 26 focused tests passed with each of `-XX:MaxVectorSize=16` (scalar fallback) and `32` (256-bit SIMD).
- Isolated class-loader tests hide the Vector API and reject Java module-system access; exact scalar scores still pass.
- D8 and R8 9.4.17 passed against Android API 35 with minimum API 26, including minification,
  repackaging, and access modification. DEX inspection confined every Vector API type reference to
  the optional helper. The negative control without the bundled consumer rule failed as expected.
- R8-optimized JVM classfiles preserved exact legacy scores without the module enabled.

Android validation covers this vector implementation and its packaging. No Android device/emulator
was available, and the complete database was not validated on Android.

Raw results: [vector operations](../benchmarks/results/quantized-cosine-2026-09-12/cosine.json),
[HNSW operations](../benchmarks/results/quantized-cosine-2026-09-12/hnsw.json), and
[Android checks and tool provenance](../benchmarks/results/quantized-cosine-2026-09-12/android-validation.json).
See [benchmark instructions](../benchmarks/README.md#quantized-cosine-benchmark) to reproduce the runs.
