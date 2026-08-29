# Approximate index candidates

`CANDIDATES` is an opt-in, read-only secondary-index admission operator for callers that need a
bounded sample from a hot equality or `IN` route. Ordinary `EQUAL` and `IN` queries are unchanged:
they still enumerate every matching posting and report an exact total.

## Public API

Kotlin core and Cloud clients expose the same shape:

```kotlin
db.from<EmbeddingBucket>()
    .inPartition("revision-7")
    .approximateCandidates(
        "bucketId",
        values = neighboringBuckets,
        maxCandidates = 32
    )
    .limit(20)
    .list<EmbeddingBucket>()
```

One route value is the approximate counterpart of `EQUAL`; multiple values are the approximate
counterpart of `IN`. Route values receive fair shares in request-order rounds, so one hot neighbor
cannot consume the entire budget before another is sampled; duplicate index-equivalent values are
skipped. A single `maxCandidates` budget is shared across all postings. It limits both
posting IDs visited and records admitted, rather than applying once per route.

The schema-free JSON request criterion is:

```json
{
  "field": "bucketId",
  "operator": "CANDIDATES",
  "value": {
    "values": [6, 7, 8],
    "maxCandidates": 32
  }
}
```

For partitioned entities, send one concrete `partition` request option. A candidate criterion must
be the sole root criterion; filter or rerank admitted records in the caller. It is not accepted for
updates, deletes, cached queries, or live-query listeners.

## Bounds and result semantics

- `maxCandidates` must be between 1 and 5,000.
- `values` must contain between 1 and 5,000 non-null route values.
- The number of routes may exceed `maxCandidates`. For example, 128 neighboring routes with a
  32-posting admission budget is valid; empty point lookups do not consume the posting budget.
- `totalResults` / core `Query.resultsCount` is the number of records admitted before the ordinary
  response `limit` and offset. It is not an exhaustive matching-row total.
- Explicit sorting, offset, and limiting apply only within the admitted sample.

The built-in posting map stops B+ tree traversal at the shared bound. A custom `IndexInteractor`
must implement `visitApproximateCandidates` with the same physical-stop guarantee; otherwise this
operator fails as unsupported rather than using the interactor's exhaustive exact-query methods.
