package com.onyx.vector.onnx

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifiedModelDownloaderTest {

    @Test
    fun `downloads verified artifacts once and reuses the cache offline`() {
        val files = mapOf(
            "onnx/model.onnx" to "small fake onnx".toByteArray(),
            "tokenizer.json" to "small fake tokenizer".toByteArray(),
        )
        val server = ArtifactServer(files)
        val cache = Files.createTempDirectory("onyx-model-download")
        try {
            val downloader = downloader(server.baseUri, files)
            assertEquals(cache.toAbsolutePath().normalize(), downloader.ensureAvailable(cache))
            files.forEach { (relativePath, content) ->
                assertContentEquals(content, Files.readAllBytes(cache.resolve(relativePath)))
                assertEquals(1, server.requests(relativePath))
            }

            server.close()
            downloader.ensureAvailable(cache)
            files.keys.forEach { relativePath ->
                assertEquals(1, server.requests(relativePath))
            }
        } finally {
            server.close()
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun `verified read-only cache needs neither a lock write nor network`() {
        if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) return

        val files = mapOf(
            "onnx/model.onnx" to "small fake onnx".toByteArray(),
            "tokenizer.json" to "small fake tokenizer".toByteArray(),
        )
        val server = ArtifactServer(files)
        val cache = Files.createTempDirectory("onyx-model-read-only")
        try {
            val downloader = downloader(server.baseUri, files)
            downloader.ensureAvailable(cache)
            server.close()
            Files.setPosixFilePermissions(
                cache,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE,
                ),
            )

            assertEquals(cache.toAbsolutePath().normalize(), downloader.ensureAvailable(cache))
            files.keys.forEach { relativePath ->
                assertEquals(1, server.requests(relativePath))
            }
        } finally {
            Files.setPosixFilePermissions(
                cache,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
            server.close()
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun `symlinked artifact directory cannot escape the cache`() {
        if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) return

        val relativePath = "onnx/model.onnx"
        val files = mapOf(relativePath to "verified model".toByteArray())
        ArtifactServer(files).use { server ->
            val cache = Files.createTempDirectory("onyx-model-symlink-cache")
            val outside = Files.createTempDirectory("onyx-model-symlink-outside")
            try {
                Files.createSymbolicLink(cache.resolve("onnx"), outside)

                val failure = assertFailsWith<IllegalArgumentException> {
                    downloader(server.baseUri, files).ensureAvailable(cache)
                }

                assertTrue(failure.message.orEmpty().contains("symbolic link"))
                assertFalse(Files.exists(outside.resolve("model.onnx")))
                assertEquals(0, server.requests(relativePath))
            } finally {
                Files.deleteIfExists(cache.resolve("onnx"))
                cache.toFile().deleteRecursively()
                outside.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `corrupt cached artifacts are repaired atomically`() {
        val relativePath = "onnx/model.onnx"
        val expected = "verified model".toByteArray()
        val files = mapOf(relativePath to expected)
        ArtifactServer(files).use { server ->
            val cache = Files.createTempDirectory("onyx-model-repair")
            try {
                val target = cache.resolve(relativePath)
                Files.createDirectories(target.parent)
                Files.writeString(target, "corrupt")

                downloader(server.baseUri, files).ensureAvailable(cache)

                assertContentEquals(expected, Files.readAllBytes(target))
                assertEquals(1, server.requests(relativePath))
                assertFalse(Files.exists(target.resolveSibling("model.onnx.download")))
            } finally {
                cache.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `failed integrity checks publish no partial artifact`() {
        val relativePath = "tokenizer.json"
        val expected = "expected bytes".toByteArray()
        val served = mapOf(relativePath to "x".toByteArray())
        ArtifactServer(served).use { server ->
            val cache = Files.createTempDirectory("onyx-model-integrity")
            try {
                val manifest = listOf(
                    ModelArtifact(relativePath, expected.size.toLong(), sha256(expected)),
                )
                val failure = assertFailsWith<IllegalStateException> {
                    VerifiedModelDownloader(
                        modelId = "test/model",
                        revision = "revision",
                        source = server.baseUri,
                        artifacts = manifest,
                        httpClient = HttpClient.newHttpClient(),
                    ).ensureAvailable(cache)
                }

                assertTrue(failure.message.orEmpty().contains("has size"))
                assertFalse(Files.exists(cache.resolve(relativePath)))
                assertFalse(Files.exists(cache.resolve("tokenizer.json.download")))
                assertEquals(3, server.requests(relativePath))
            } finally {
                cache.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `concurrent callers share one download`() {
        val relativePath = "model.onnx"
        val files = mapOf(relativePath to ByteArray(32_768) { (it % 251).toByte() })
        ArtifactServer(files).use { server ->
            val cache = Files.createTempDirectory("onyx-model-concurrent")
            val executor = Executors.newFixedThreadPool(8)
            try {
                val downloader = downloader(server.baseUri, files)
                val calls = List(8) { Callable { downloader.ensureAvailable(cache) } }
                val results = executor.invokeAll(calls).map { it.get() }

                assertTrue(results.all { it == cache.toAbsolutePath().normalize() })
                assertEquals(1, server.requests(relativePath))
            } finally {
                executor.shutdownNow()
                cache.toFile().deleteRecursively()
            }
        }
    }

    private fun downloader(source: URI, files: Map<String, ByteArray>): VerifiedModelDownloader =
        VerifiedModelDownloader(
            modelId = "test/model",
            revision = "revision",
            source = source,
            artifacts = files.map { (relativePath, content) ->
                ModelArtifact(relativePath, content.size.toLong(), sha256(content))
            },
            httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
        )

    private fun sha256(content: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private class ArtifactServer(
        private val files: Map<String, ByteArray>,
    ) : AutoCloseable {
        private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val serverExecutor = Executors.newCachedThreadPool()
        private val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        ).apply {
            createContext("/") { exchange -> respond(exchange) }
            executor = serverExecutor
            start()
        }

        val baseUri: URI = URI.create("http://127.0.0.1:${server.address.port}/")

        fun requests(relativePath: String): Int = requestCounts[relativePath]?.get() ?: 0

        private fun respond(exchange: HttpExchange) {
            val relativePath = exchange.requestURI.path.removePrefix("/")
            requestCounts.computeIfAbsent(relativePath) { AtomicInteger() }.incrementAndGet()
            val content = files[relativePath]
            if (content == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, content.size.toLong())
                exchange.responseBody.use { it.write(content) }
            }
            exchange.close()
        }

        override fun close() {
            server.stop(0)
            serverExecutor.shutdownNow()
        }
    }
}
