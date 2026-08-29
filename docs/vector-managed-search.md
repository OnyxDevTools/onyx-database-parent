# Vector-managed search

`VectorManagedEntity` is Onyx's whole-record search model. It combines indexed structured predicates, normalized lexical features, semantic fingerprint routing, and an optional native HNSW candidate channel in one persistence model.

```text
persisted attributes ── automatic type encoding ── structured/text fingerprints ─┐
dense embedding ── frozen calibration ── product cells + semantic SimHash ───────┼─ internal vector index
                └── normalize + signed-int8 quantize ── persistent HNSW graph ────┘
```

The source full-precision embedding is never stored. HNSW-enabled records retain one normalized
signed-int8 value per dimension, while signature-only records retain no dense-vector payload.
Applications persist their normal fields; Onyx creates and maintains the hidden representation
and persistent graph.

## Define an entity

Extend `VectorManagedEntity` and use the normal persistence annotations. `entropy` selects the
fingerprint width; `@VectorAttribute` opts individual fields into additional feature families.

```kotlin
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.VectorFeatureFamily
import com.onyx.persistence.annotations.values.IdentifierGenerator
import java.util.Date

@Entity(fileName = "option-quotes/", entropy = 128)
class OptionQuote : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0

    @Partition
    @Attribute
    var market: String = ""

    @Attribute
    var symbol: String = ""

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.CATEGORICAL, VectorFeatureFamily.INTERVAL]
    )
    var price: Double = 0.0

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.INTERVAL]
    )
    var volume: Long = 0

    @Attribute
    var active: Boolean = true

    @Attribute
    var observedAt: Date = Date(0L)

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM, VectorFeatureFamily.TEXT_NGRAM]
    )
    var description: String = ""
}
```

Every persisted identifier, partition, and `@Attribute` is included automatically in `AUTO` mode.
That default emits only null/presence and categorical equality routes. Relationships are persisted
normally but are not flattened into the record's vector representation.

## Entropy

`@Entity(entropy = N)` supplies one positive requested bit width. Onyx rounds it up to a complete 64-bit word and bounds the result:

| Requested entropy | Resolved entropy |
|---:|---:|
| `1..64` | `64` bits |
| `65..128` | `128` bits |
| `129..192` | `192` bits |
| `193` or greater | `256` bits |

The default is `128`. The resolved value controls both sides of the representation:

* every structured and lexical feature fingerprint uses `entropy / 64` words;
* semantic SimHash uses the same number of bits;
* semantic routing always divides that SimHash into four equal bands;
* the representation codec and internal index validate the same width.

This single setting keeps document encoding, query encoding, persisted metadata, and index routing compatible. Changing the resolved entropy changes the entity's persisted configuration and rebuilds its internal vector index. Values that round to the same resolved width have the same entropy setting.

Entropy does not change application values or query truth. It controls representation width, storage, and candidate selectivity; the ordinary predicate evaluator remains authoritative.

## Per-attribute feature families

The default is intentionally small. `@VectorAttribute` controls the extra cost and capabilities of
each field:

| Mode | Emission |
|---|---|
| `AUTO` | Null/presence plus `CATEGORICAL` equality routes |
| `SELECTED` | Null/presence plus exactly the listed `families` |
| `UNIVERSAL` | Every available family |
| `IGNORE` | No routes for the field |

| Family | Purpose |
|---|---|
| `CATEGORICAL` | Stable full-value `EQUAL`, `IN`, and their negative forms |
| `INTERVAL` | Ordered comparisons, `BETWEEN`, and numeric equality when categorical routes are omitted |
| `TEXT_EXACT` | Java-compatible case-folded complete values for exact regex and tokenless/ignore-case textual predicates |
| `TEXT_TERM` | Per-field terms and whole-record `.search(...)` terms |
| `TEXT_PREFIX` | Length-preserving case-folded `STARTS_WITH` routes |
| `TEXT_NGRAM` | Length-preserving case-folded `CONTAINS` and safely extractable regular-expression literals |

Collections and arrays apply the field's selected families to every non-null element. Maps apply
them to every non-null key and value. `SELECTED` with an empty family list is useful when only
`IS_NULL`/`NOT_NULL` routing is wanted.

Interval encoding remains type-aware: integers use exact signed coordinates; finite floating-point
values use ordered IEEE-754 coordinates; dates use epoch milliseconds; and strings use a
conservative UTF-16 prefix coordinate. Floating-point range routing requires both `INTERVAL` and
`CATEGORICAL`, because NaN and infinities have no interval coordinate.

Ordered values activate a root-to-leaf path in a binary interval tree. For an 8-bit value of 73, the conceptual path is:

```text
[0..255]
[0..127]
[64..127]
[64..95]
[72..79]
[72..75]
[72..73]
[73]
```

An inclusive query range from 70 through 85 is represented by a small disjoint cover:

```text
[70..71] [72..79] [80..83] [84..85]
```

Records inside the range share at least one cover node. `TEXT_TERM` applies NFKC normalization and
locale-independent lowercase tokenization on both stored and query text. The exact, prefix, and
n-gram families instead use Java-compatible simple case folding one Unicode code point at a time.
That fold is only a conservative routing key: it preserves raw prefix/substring implications while
the normal predicate evaluator remains authoritative for case and regex semantics. A comparison
literal containing malformed UTF-16 falls back to a table scan rather than risking a false negative.

## Indexed predicates

Vector-managed fields use the ordinary query API. The internal vector index supports the scalar and text operators, including compounds in either source order:

```kotlin
import com.onyx.persistence.query.*

val criteria = ("symbol" eq "QQQ")
    .and("price" between (295.0 to 305.0))
    .and("volume" gte 100_000L)
    .and("description" containsIgnoreCase "liquid")

val quotes = manager.from<OptionQuote>()
    .where(criteria)
    .list<OptionQuote>()
```

The supported families include:

* `EQUAL`, `NOT_EQUAL`, `IN`, and `NOT_IN`;
* `IS_NULL` and `NOT_NULL`;
* `LESS_THAN`, `LESS_THAN_EQUAL`, `GREATER_THAN`, and `GREATER_THAN_EQUAL`;
* `BETWEEN` and `NOT_BETWEEN`;
* `STARTS_WITH`, `CONTAINS`, `CONTAINS_IGNORE_CASE`, `LIKE`, and `MATCHES`, including their negative forms;
* nested `AND`, `OR`, and `NOT` groups.

All public operators remain available through the normal query API. An operator uses the vector
index only when its field selected the required family; otherwise scanner selection safely falls
back to the ordinary table path. A regular expression must also contain a literal that every match
necessarily includes.

The index returns a conservative candidate set. Onyx then evaluates the original `QueryCriteria` against the stored record before returning it. Hash collisions or broad text routes can add candidate work, but they cannot turn a non-match into a result.

Negative predicates use the indexed record domain and subtract verified positive matches. In a conjunction, a selective indexed sibling can bound that domain regardless of whether it appears before or after the negative predicate.

## Lexical search

`.search(...)` uses normalized whole-record terms emitted only by fields selecting `TEXT_TERM`, and
composes with the same structured criteria:

```kotlin
val results = manager.from<OptionQuote>()
    .search("highly liquid options")
    .and("symbol" eq "QQQ")
    .and("price" between (295.0 to 305.0))
    .list<OptionQuote>()
```

The explicit form can control term policy and candidate scoring:

```kotlin
val criterion = approximateSearch(
    VectorSearchQuery(
        text = "highly liquid options",
        minScore = 0.55f,
        maxCandidates = 1_000,
        requireAllTerms = true
    )
)
```

Lexical `search(...)`, `MATCHES`, and the legacy `FullTextQuery` remain exhaustive. Use the
dedicated `approximateSearch(...)` / `SEARCH_CANDIDATES` contract to share `maxCandidates` across
bounded term-posting visits, candidate hydration, and scoring. Approximate result totals describe
the admitted sample rather than the full matching population. `minScore` is a candidate-score
threshold, not a probability. The dedicated operator also makes older servers reject the request
instead of silently ignoring a new optional field and performing exhaustive work.

Both physical term-posting visits and admitted candidates are at most `maxCandidates`. Additional
`requireAllTerms` membership probes are point lookups under a separate bounded work allowance.

Schema-free Cloud/Python callers can opt in with the same typed `MATCHES` value:

```json
{
  "field": "__full_text__",
  "operator": "SEARCH_CANDIDATES",
  "value": {
    "text": "highly liquid options",
    "maxCandidates": 1000,
    "requireAllTerms": true
  }
}
```

`SEARCH_CANDIDATES` must be the sole, positive, read-only root criterion. It accepts text-only
`VectorSearchQuery` values; semantic and hybrid searches continue to use `MATCHES`. A partitioned
entity requires one concrete partition.

## Semantic routing

Semantic search starts with dense embeddings supplied by the application. Onyx does not create embeddings. Fit one deterministic `VectorCalibration` from representative embeddings and persist that shared calibration separately:

```kotlin
import com.onyx.vector.VectorCalibration
import com.onyx.vector.VectorCalibrationCodec

val calibration = VectorCalibration.fit(calibrationEmbeddings)
val calibrationBytes = VectorCalibrationCodec.encode(calibration)
val restoredCalibration = VectorCalibrationCodec.decode(calibrationBytes)
```

Attach both LSH routing metadata and an HNSW vector without retaining the full-precision input:

```kotlin
quote.semanticVector(documentEmbedding, restoredCalibration)
manager.saveEntity(quote)
```

`semanticVector` reads the entity's resolved entropy, produces a semantic signature with the same
width as its structured features, and retains a normalized int8 copy for HNSW. Saving regenerates
the structured portion while preserving compatible semantic and HNSW metadata. Call
`semanticSignature(...)` when only fingerprint routing is wanted, or call
`hnswVector(embedding, calibrationId)` when the application owns an independent embedding-space
identifier and does not use Onyx's `VectorCalibration`.

A query signature must use the same calibration and the entity configuration's entropy:

```kotlin
import com.onyx.vector.VectorManagedConfiguration

val configuration = VectorManagedConfiguration.forClass(OptionQuote::class.java)
val querySignature = restoredCalibration.encode(queryEmbedding, configuration.entropy)

val candidates = manager.from<OptionQuote>()
    .where(search(querySignature, minScore = 0.55f, nearbyBucketRadius = 1))
    .and("symbol" eq "QQQ")
    .list<OptionQuote>()
```

The compact LSH representation contains product-cell coordinates, a SimHash, four bands, boundary
confidence, and a stable calibration ID. It does not contain the full-precision embedding.
Candidate lookup can use matching cells, the product bucket and nearby buckets, and matching
SimHash bands. The independent HNSW representation uses one signed byte per normalized embedding
dimension and its own calibration ID.

Semantic routing is approximate. Applications that require exact embedding similarity can temporarily re-embed returned content, rerank the bounded candidate set, and discard those temporary vectors.

## Native HNSW candidates

HNSW is an explicit candidate-admission channel rather than an exact predicate. Ingest a vector
with a stable non-zero ID identifying the embedding model and vector space:

```kotlin
quote.hnswVector(documentEmbedding, embeddingSpaceId)
manager.saveEntity(quote)
```

Vectors with different calibration IDs are kept in separate graphs, and a dimension mismatch
inside one calibration fails instead of mixing incompatible embeddings. Each entity partition has
its own graph. Graph nodes store compact int8 vectors and bounded neighbor lists in dedicated
Onyx maps, so prompt-time traversal does not hydrate full entities and never rebuilds the graph.
Insert, update, delete, reopen, and explicit index rebuild all maintain the graph. Neighbor lists
are strictly reciprocal and capped at 32 edges on level zero and 16 on upper levels. Mutations are
serialized, while independent searches share a read lock and may traverse concurrently.

Query with the dedicated sole-root operator:

```kotlin
val candidates = manager.from<OptionQuote>()
    .hnswCandidates(
        HnswSearchQuery(
            calibrationId = embeddingSpaceId,
            vector = queryEmbedding,
            maxCandidates = 100,
            efSearch = 400,
            minScore = 0.25f,
        )
    )
    .inPartition("US") // required for a partitioned entity
    .list<OptionQuote>()
```

The schema-free wire form is:

```json
{
  "field": "__full_text__",
  "operator": "HNSW_CANDIDATES",
  "value": {
    "formatVersion": 1,
    "calibrationId": "7640891576956012809",
    "vector": [0.125, -0.75, 0.33],
    "maxCandidates": 100,
    "efSearch": 400,
    "minScore": 0.25
  }
}
```

`maxCandidates` is bounded to `1..5000`; `efSearch` must be at least `maxCandidates`
and is bounded to `20000`. The vector must contain `1..16384` finite values and have a non-zero
norm. Level-zero distance evaluations cannot exceed `efSearch`; upper-level routing has a separate
fixed bound. Results are ordinary entities ordered by descending quantized cosine score with
record ID as the deterministic tie break. Result totals describe admitted candidates, not the
cardinality of an exact predicate.

`HNSW_CANDIDATES` must be positive, read-only, the sole root criterion, and target
`__full_text__`. Query caching and live listeners are rejected. Apply additional business filters
after admission, or isolate the corpus with the entity partition. This explicit contract makes an
older server reject the unknown operator rather than silently running another search path.

Representations written before codec v3 and rows populated only with `semanticSignature(...)`
remain valid for structured, lexical, and LSH search, but they are not HNSW-addressable. An index
rebuild cannot reconstruct an embedding from a fingerprint. To add those rows to HNSW, re-embed
the authoritative content and save each row with `hnswVector(...)` or `semanticVector(...)` before
rebuilding. This is a data reingestion requirement, not a query-time migration.

### Failure and recovery contract

HNSW validates a new calibration and dimension before overwriting the authoritative entity row or
removing its old graph node. A rejected update therefore leaves both the stored representation and
the searchable graph unchanged, and a corrected retry is safe.

The index persists a versioned `DIRTY` marker and forces it to storage before the first row or graph
mutation in an open session. It remains dirty on disk during ingestion so ordinary saves do not pay
two durability barriers apiece. On orderly shutdown, Onyx first flushes graph data and then
publishes `CLEAN`. A process crash, interrupted index save, or failed rebuild consequently causes
the next open to fail HNSW searches and HNSW-bearing saves closed with an explicit rebuild error.
It never repairs by scanning the entity table on a prompt query.

Recovery is an explicit streaming internal-index rebuild. The marker remains dirty throughout the
entire posting clear, entity walk, and graph construction, and becomes eligible for `CLEAN` only
after successful completion. Rebuild heap use is bounded; elapsed work is proportional to the live
records and emitted routes. A graph created before the reciprocal-edge state version also requires
this one-time rebuild. Rows already carrying codec-v3 int8 vectors need no re-embedding for that
upgrade; signature-only/codec-v2 rows still require the reingestion described above.

## Internal index ownership

`VectorManagedEntity` owns one inherited internal `IndexType.VECTOR` index. Application models must not annotate their own fields with `@Index(type = IndexType.VECTOR)` and must not declare a second vector representation.

The internal field is excluded from wildcard selections and entity maps. Applications interact with persisted business attributes, `semanticVector` or `semanticSignature`, and the normal query API.

## Configuration and rebuild behavior

Onyx derives a stable configuration signature from the resolved entropy and the names, types, and
canonically ordered feature families of included attributes. Schema startup compares that signature
with the stored internal-index metadata.

Plan these changes as index migrations:

* changing entropy to a different resolved width rebuilds the index;
* adding, removing, renaming, or changing the type of a persisted attribute rebuilds the index;
* changing a field mode or selected family rebuilds the index;
* a representation from a different configuration is rejected rather than mixed into the index;
* a newly fitted calibration has a different calibration ID, so document and query signatures or HNSW vectors must use the same frozen vector-space ID.

Rebuilding regenerates structured and lexical features from authoritative records. A family-only
change preserves existing semantic and HNSW metadata when compatible; semantic data must be
regenerated when its bit width or calibration changes. HNSW data requires re-embedding when its
model, dimension, or calibration ID changes. Rebuild walks and rewrites one
authoritative record at a time under the record map's write lock: heap use is bounded rather than
proportional to table cardinality, while elapsed work remains `O(records + emitted routes)`. Plan
the migration as an exclusive maintenance operation for large tables.

## Downstream model contract

An embedding or language-model service can use Onyx as a compact candidate store:

1. Persist ordinary structured and text fields on a `VectorManagedEntity`.
2. Fit and freeze one representative calibration for the embedding model.
3. Attach each record's semantic signature and/or int8 HNSW vector, then discard its full-precision embedding.
4. Translate exact filters into the normal query AST; keep bounded HNSW admission as its explicit sole-root channel.
5. Encode the query with the same calibration and entity entropy.
6. Request a bounded candidate set.
7. Fuse lexical, LSH, and HNSW candidate ranks and optionally rerank with temporary full-precision embeddings.

The database contract remains the normal query AST plus compact semantic metadata. It does not require a model-specific natural-language parser.

## Operational notes

* Entropy is capped at 256 bits.
* Higher entropy increases representation size and generally reduces structured hash collisions.
* Semantic band width also grows with entropy, so measure semantic recall and candidate counts for the application corpus.
* HNSW stores one byte per embedding dimension plus bounded graph edges; choose `efSearch` from held-out recall and latency measurements.
* Searches fail closed after an unclean HNSW session until an explicit streaming index rebuild succeeds; no entity scan occurs on the query path.
* Legacy/signature-only rows require re-embedding before they can enter the HNSW graph.
* Candidate records can be loaded for authoritative predicate verification and optional downstream reranking.
* Calibration must be representative, deterministic, and shared by document and query encoders.
* Relationships continue through their ordinary persistence and query behavior; they are not flattened automatically.

## Query benchmark

`VectorIndexVsFullScanBenchmarkTest` is an opt-in, deterministic comparison of the vector index and an authoritative `FullTableScanner` over the same physical records. Both arms execute the same `DefaultQueryInteractor` pipeline; an internal test seam changes only scanner selection. It covers every persisted scalar family, positive and negative predicates, selective and broad results, compound `AND`/`OR` predicates in both source orders, lexical search, exhaustive semantic search, and bounded semantic top-k search.

The two timing columns are separate forced executions. A row where the full-scan median is lower does not mean the indexed arm fell back to a table scan. The indexed arm must report vector-scanner and fingerprint-index events; the baseline must report a full-table scan and no vector-scanner events. An unroutable indexed predicate fails the preflight instead of being timed as an index lookup.

An index is not guaranteed to win every workload. A near-universal result, a broad negative predicate, or a deliberately lossy routing coordinate can make the indexed arm process most records while also paying posting and candidate-set overhead. Exact verification preserves query correctness in those cases, but a sequential full scan can have the lower constant cost. Compare candidate selectivity and both medians before drawing a conclusion from the reported speedup.

Before timing each workload, the benchmark compares the exact ordered IDs and search-score bits. Direct execution events prove that the automatic arm entered `VectorIndexScanner`, its fingerprint branch, and `FingerprintIndexInteractor.matchAll` for search, while the baseline entered `FullTableScanner` and never the vector scanner. Scalar workloads use deterministic ID ordering. Search workloads keep the normal relevance order.

A quick correctness and harness smoke run is:

```bash
ONYX_VECTOR_QUERY_BENCHMARK=true \
ONYX_VECTOR_QUERY_BENCHMARK_RECORD_COUNTS=1000 \
ONYX_VECTOR_QUERY_BENCHMARK_STORES=IN_MEMORY \
ONYX_VECTOR_QUERY_BENCHMARK_WARMUPS=0 \
ONYX_VECTOR_QUERY_BENCHMARK_ROUNDS=1 \
./gradlew :onyx-database-tests:test \
  --tests database.query.VectorIndexVsFullScanBenchmarkTest --rerun-tasks
```

The default complete matrix uses 10,000 shuffled records, one warmup per arm, and five measured rounds. The entity is deliberately wide and opts each field into every feature family required by its workloads, so a complete 100,000-row matrix—especially exact full-scan search—is expensive. Use an explicit workload subset for a persistent scale run:

```bash
ONYX_VECTOR_QUERY_BENCHMARK=true \
ONYX_VECTOR_QUERY_BENCHMARK_RECORD_COUNTS=100000 \
ONYX_VECTOR_QUERY_BENCHMARK_STORES=MEMORY_MAPPED_FILE \
ONYX_VECTOR_QUERY_BENCHMARK_WARMUPS=1 \
ONYX_VECTOR_QUERY_BENCHMARK_ROUNDS=5 \
ONYX_VECTOR_QUERY_BENCHMARK_WORKLOADS=int_equal_one_row,date_between_1pct,string_equal_0_1pct,string_between_shared_prefix_1pct,compound_or_0_2pct,lexical_search_rare_1pct,semantic_search_top_25 \
./gradlew --no-daemon :onyx-database-tests:test \
  --tests database.query.VectorIndexVsFullScanBenchmarkTest --rerun-tasks
```

Use comma-separated values to compare scales or stores, for example `RECORD_COUNTS=10000,100000` or `STORES=IN_MEMORY,MEMORY_MAPPED_FILE,FILE`. `ONYX_VECTOR_QUERY_BENCHMARK_WORKLOADS` selects comma-separated workload names and rejects misspellings. Batch size, iterations per round, shuffle seed, and database retention are configurable with the same prefix; the defaults are documented in the benchmark source. Do not launch overlapping invocations of the same Gradle `test` task because Gradle shares that task's binary result directory.

Each invocation uses an isolated directory and prints its exact paths:

* `onyx-database-tests/build/benchmarks/vector-index-vs-full-scan-*/vector-index-vs-full-scan.txt`
* `onyx-database-tests/build/benchmarks/vector-index-vs-full-scan-*/vector-index-vs-full-scan.csv`

The report includes insertion throughput, summed post-close database file lengths for persistent stores, result selectivity, the min/p50/max of per-round mean latency, queries per second, raw samples, and `full-scan p50 / index p50` speedup. A speedup above `1.0` favors the index. CSV rows include the run settings and runtime environment needed for comparison.

These are warm-cache, steady-state measurements: fixture insertion and untimed parity checks touch the data before measurement. The timing boundary includes query planning, scanner selection, route/table access, exact verification, result materialization, and deterministic ID or relevance ordering. Full-scan search deliberately regenerates sparse features from every scanned entity, matching authoritative query behavior. Semantic signatures in this fixture are deterministic synthetic routes, not embeddings produced by a representative calibration. The benchmark never fails on timing; performance gates should use historical medians from a stable dedicated machine.

## Migration checklist

1. Make the entity extend `VectorManagedEntity`.
2. Put the desired entropy on its existing `@Entity` annotation, or use the default of 128.
3. Remove application-declared vector index fields.
4. Add `@VectorAttribute` only where a field needs interval or lexical routes; use `UNIVERSAL` only when every family is intentionally required.
5. Keep identifiers, partitions, attributes, relationships, and existing query syntax unchanged.
6. Reopen the schema and allow the internal vector index to rebuild.
7. If semantic retrieval is used, encode document and query signatures with the same calibration and resolved entity entropy.
8. Verify structured result correctness separately from semantic recall and downstream reranking quality.
