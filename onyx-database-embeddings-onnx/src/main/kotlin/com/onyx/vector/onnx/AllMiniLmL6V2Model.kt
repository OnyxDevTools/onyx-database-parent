package com.onyx.vector.onnx

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object AllMiniLmL6V2Model {
    const val MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2"
    const val REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"

    private val downloader = VerifiedModelDownloader(
        modelId = MODEL_ID,
        revision = REVISION,
        source = URI.create("https://huggingface.co/$MODEL_ID/resolve/$REVISION/"),
        artifacts = listOf(
            ModelArtifact(
                relativePath = "onnx/model.onnx",
                size = 90_405_214L,
                sha256 = "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452",
            ),
            ModelArtifact(
                relativePath = "tokenizer.json",
                size = 466_247L,
                sha256 = "be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037",
            ),
            ModelArtifact(
                relativePath = "tokenizer_config.json",
                size = 350L,
                sha256 = "acb92769e8195aabd29b7b2137a9e6d6e25c476a4f15aa4355c233426c61576b",
            ),
            ModelArtifact(
                relativePath = "sentence_bert_config.json",
                size = 53L,
                sha256 = "fc1993fde0a95c24ec6c022539d41cf6e2f7c9721e5415d6fb6897472a9cd4b7",
            ),
            ModelArtifact(
                relativePath = "1_Pooling/config.json",
                size = 190L,
                sha256 = "4be450dde3b0273bb9787637cfbd28fe04a7ba6ab9d36ac48e92b11e350ffc23",
            ),
        ),
    )

    fun ensureAvailable(modelDirectory: Path): Path = try {
        downloader.ensureAvailable(modelDirectory)
    } catch (failure: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IllegalStateException(
            "Interrupted while downloading $MODEL_ID revision $REVISION",
            failure,
        )
    } catch (failure: Exception) {
        throw IllegalStateException(
            "Could not install $MODEL_ID revision $REVISION in " +
                modelDirectory.toAbsolutePath().normalize(),
            failure,
        )
    }

    fun defaultModelDirectory(): Path {
        val cacheRoot = System.getenv("XDG_CACHE_HOME")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Path::of)
            ?: System.getProperty("user.home")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { Path.of(it, ".cache") }
            ?: throw IllegalStateException(
                "Cannot determine an embedding cache directory because XDG_CACHE_HOME and user.home are unset",
            )
        return cacheRoot
            .resolve("onyx/models")
            .resolve(MODEL_ID)
            .resolve(REVISION)
            .toAbsolutePath()
            .normalize()
    }
}

internal data class ModelArtifact(
    val relativePath: String,
    val size: Long,
    val sha256: String,
)

internal class VerifiedModelDownloader(
    private val modelId: String,
    private val revision: String,
    private val source: URI,
    private val artifacts: List<ModelArtifact>,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    init {
        require(artifacts.isNotEmpty()) { "At least one model artifact is required" }
        require(artifacts.map(ModelArtifact::relativePath).distinct().size == artifacts.size) {
            "Model artifact paths must be unique"
        }
        artifacts.forEach { artifact ->
            require(artifact.size > 0L) { "Invalid size for ${artifact.relativePath}" }
            require(SHA_256.matches(artifact.sha256)) {
                "Invalid SHA-256 for ${artifact.relativePath}"
            }
        }
    }

    fun ensureAvailable(requestedDirectory: Path): Path {
        val modelDirectory = requestedDirectory.toAbsolutePath().normalize()

        // A deployment may mount a pre-provisioned model cache read-only. Verify before creating
        // the lock file so a complete cache never requires write access or a network request.
        if (allArtifactsVerified(modelDirectory)) return modelDirectory

        Files.createDirectories(modelDirectory)
        require(Files.isDirectory(modelDirectory) && Files.isWritable(modelDirectory)) {
            "Model cache directory is not writable: $modelDirectory"
        }

        val directoryLock = acquireDirectoryLock(modelDirectory)
        try {
            directoryLock.lock.withLock {
                val lockFile = modelDirectory.resolve(DOWNLOAD_LOCK_FILE)
                require(!Files.isSymbolicLink(lockFile)) {
                    "Model cache lock file must not be a symbolic link: $lockFile"
                }
                FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS,
                ).use { lockChannel ->
                    lockChannel.lock().use {
                        artifacts.forEach { artifact ->
                            val target = resolveArtifact(modelDirectory, artifact)
                            if (!isVerified(target, artifact)) {
                                downloadAndInstall(modelDirectory, target, artifact)
                            }
                        }
                    }
                }
            }
        } finally {
            releaseDirectoryLock(modelDirectory, directoryLock)
        }
        return modelDirectory
    }

    private fun allArtifactsVerified(modelDirectory: Path): Boolean =
        artifacts.all { artifact ->
            runCatching {
                isVerified(resolveArtifact(modelDirectory, artifact), artifact)
            }.getOrDefault(false)
        }

    private fun resolveArtifact(modelDirectory: Path, artifact: ModelArtifact): Path {
        val target = modelDirectory.resolve(artifact.relativePath).normalize()
        require(target.startsWith(modelDirectory)) {
            "Model artifact escapes the cache directory: ${artifact.relativePath}"
        }
        var path = modelDirectory
        modelDirectory.relativize(target).forEach { component ->
            path = path.resolve(component)
            require(!Files.isSymbolicLink(path)) {
                "Model artifact path must not contain a symbolic link: $path"
            }
        }
        return target
    }

    private fun isVerified(target: Path, artifact: ModelArtifact): Boolean =
        Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(target) == artifact.size &&
            sha256(target) == artifact.sha256

    private fun downloadAndInstall(
        modelDirectory: Path,
        target: Path,
        artifact: ModelArtifact,
    ) {
        var lastFailure: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            try {
                downloadOnce(modelDirectory, target, artifact)
                return
            } catch (failure: InterruptedException) {
                throw failure
            } catch (failure: Exception) {
                lastFailure = failure
                if (!failure.isRetryableDownloadFailure() || attempt == MAX_DOWNLOAD_ATTEMPTS) {
                    throw failure
                }
                downloadLogger.log(
                    System.Logger.Level.WARNING,
                    "Download attempt {0} of {1} failed for {2}; retrying: {3}",
                    attempt,
                    MAX_DOWNLOAD_ATTEMPTS,
                    artifact.relativePath,
                    failure.message.orEmpty(),
                )
                Thread.sleep(RETRY_DELAY_MILLIS * attempt)
            }
        }
        throw checkNotNull(lastFailure)
    }

    private fun downloadOnce(
        modelDirectory: Path,
        target: Path,
        artifact: ModelArtifact,
    ) {
        // Recheck immediately before every filesystem mutation. The process and file locks
        // coordinate cooperating downloaders; this rejects a poisoned cache with symlinked
        // artifact directories instead of following it outside the selected root.
        resolveArtifact(modelDirectory, artifact)
        Files.createDirectories(target.parent)
        resolveArtifact(modelDirectory, artifact)
        val partial = target.resolveSibling("${target.fileName}$PARTIAL_SUFFIX")
        Files.deleteIfExists(partial)
        val artifactUri = source.resolve(artifact.relativePath)
        downloadLogger.log(
            System.Logger.Level.INFO,
            "Downloading {0} revision {1}: {2}",
            modelId,
            revision,
            artifact.relativePath,
        )

        try {
            val request = HttpRequest.newBuilder(artifactUri)
                .timeout(Duration.ofMinutes(15))
                .header("User-Agent", "onyx-database-embeddings-onnx/1")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { input ->
                if (response.statusCode() != 200) {
                    throw ModelDownloadHttpException(response.statusCode(), artifactUri)
                }
                if (artifactUri.scheme.equals("https", ignoreCase = true)) {
                    check(response.uri().scheme.equals("https", ignoreCase = true)) {
                        "Download redirected from HTTPS to an insecure URI: ${response.uri()}"
                    }
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                Files.newOutputStream(
                    partial,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS,
                ).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        check(downloaded <= artifact.size) {
                            "Downloaded ${artifact.relativePath} exceeds its expected size ${artifact.size}"
                        }
                    }
                }

                check(downloaded == artifact.size) {
                    "Downloaded ${artifact.relativePath} has size $downloaded; expected ${artifact.size}"
                }
                val actualHash = digest.digest().toHex()
                check(actualHash == artifact.sha256) {
                    "Downloaded ${artifact.relativePath} has SHA-256 $actualHash; expected ${artifact.sha256}"
                }
            }

            try {
                Files.move(
                    partial,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun acquireDirectoryLock(path: Path): DirectoryLock =
        checkNotNull(
            directoryLocks.compute(path) { _, existing ->
                (existing ?: DirectoryLock()).also { it.users += 1 }
            },
        )

    private fun releaseDirectoryLock(path: Path, released: DirectoryLock) {
        directoryLocks.computeIfPresent(path) { _, current ->
            if (current !== released) {
                current
            } else {
                current.users -= 1
                current.takeIf { it.users > 0 }
            }
        }
    }

    companion object {
        private const val DOWNLOAD_LOCK_FILE = ".download.lock"
        private const val PARTIAL_SUFFIX = ".download"
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MILLIS = 250L
        private val SHA_256 = Regex("[0-9a-f]{64}")
        private val directoryLocks = ConcurrentHashMap<Path, DirectoryLock>()
        private val downloadLogger = System.getLogger(VerifiedModelDownloader::class.java.name)
    }

    private class DirectoryLock(
        val lock: ReentrantLock = ReentrantLock(),
        var users: Int = 0,
    )
}

private class ModelDownloadHttpException(
    val statusCode: Int,
    uri: URI,
) : IOException("Download returned HTTP $statusCode for $uri")

private fun Exception.isRetryableDownloadFailure(): Boolean = when (this) {
    is ModelDownloadHttpException -> statusCode == 408 || statusCode == 429 || statusCode >= 500
    is IOException -> true
    is IllegalStateException -> true
    else -> false
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
