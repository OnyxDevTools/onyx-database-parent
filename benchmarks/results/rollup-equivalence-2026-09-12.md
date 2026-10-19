# Rollup list construction validation

The only edits made by the rollup task were, in each of 8 Bar rolling resolvers:

```diff
-        acc.add(0, cursor)
+        acc.add(cursor)
         cursor = cursor.previousBar as? Bar
     }
+    acc.reverse()
     return acc
```

Applied to Stock-Analyzer/schema.json and Stock-Trader/marketdata.schema.json.
The parser confirmed each immediate before/after snapshot differed only in the 8 intended resolver strings. Existing schema differences were retained.

Actual DefaultClassWriterKt.sanitize from the local cloud compiler generated all 32 resolver bodies from both schemas' before/after snapshots. Eight baseline/changed pairs were compiled with Kotlin and executed against deterministic in-memory Bar chains. All eight baseline bodies match the previously captured live Bar source ignoring whitespace.

PASS: 2,064 assertions across 168 actual-resolver comparisons. Metadata, OHLC, volume, bit-exact VWAP and predecessor-access order agree. Coverage includes every rollup interval, full 4,410-row weekly windows, short history, sequence gaps, missing/null predecessor, zero/null/saturating volume, negative volume, missing/invalid VWAP, VWAP fallback, and missing OHLC values.

Compilation ran with a 384MB heap and ActiveProcessorCount=2; the equivalence runner used 192MB and ActiveProcessorCount=2. No connection to production, process attach, or database was involved. This is a semantic equivalence check, not an ingestion speedup measurement.

An independent concurrent editor subsequently changed calculation cache handling to v4/activeDepth in the local schema files. Those changes were neither authored nor reverted by this task. The saved before/after schema copies isolate the rollup-only changes. Current local rollup bodies were rechecked and match the validated after snapshots.
