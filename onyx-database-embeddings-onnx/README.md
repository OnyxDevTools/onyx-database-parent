# Onyx Database ONNX Embeddings

This JVM-server module implements the database `SearchEmbeddingProvider` contract with a local
SentenceTransformer ONNX export. Model files are not packaged in this library. The standard
factory downloads a checksum-pinned all-MiniLM-L6-v2 checkpoint on first use and reuses the verified
cache afterward:

```kotlin
val provider = OnnxSentenceTransformerEmbeddingProvider.allMiniLmL6V2()
manager.searchEmbeddingProvider = provider
```

The pinned model is published under the Apache-2.0 license.

The default cache is
`${XDG_CACHE_HOME:-~/.cache}/onyx/models/sentence-transformers/all-MiniLM-L6-v2/<revision>`.
Pass a directory to `allMiniLmL6V2(path)` to select a different cache target. Downloads use a
cross-process lock, temporary files, exact sizes, and SHA-256 verification. A complete verified
cache works without network access and may be mounted read-only.

The model directory must contain:

```text
tokenizer.json
tokenizer_config.json
sentence_bert_config.json
1_Pooling/config.json
onnx/model.onnx
```

The implementation matches OnyxGemma's SentenceTransformer path: it tokenizes with the configured
maximum sequence length, runs the transformer, attention-mask mean-pools `last_hidden_state`, and
L2-normalizes the result. The vector calibration ID is derived from the model and tokenizer artifact
bytes, so identical checkpoints use the same ID on every server and changed checkpoints cannot be
mixed accidentally.

```kotlin
import com.onyx.vector.onnx.OnnxSentenceTransformerEmbeddingProvider
import java.nio.file.Path

val provider = OnnxSentenceTransformerEmbeddingProvider(
    Path.of("/srv/onyx/models/all-MiniLM-L6-v2"),
)
manager.searchEmbeddingProvider = provider
```

Call `close()` during application shutdown. The provider is thread-safe and may be shared by
multiple persistence managers.

To run the optional real-checkpoint integration test:

```shell
ONYX_TEST_SENTENCE_TRANSFORMER_MODEL=/path/to/all-MiniLM-L6-v2 \
  ./gradlew :onyx-database-embeddings-onnx:test
```
