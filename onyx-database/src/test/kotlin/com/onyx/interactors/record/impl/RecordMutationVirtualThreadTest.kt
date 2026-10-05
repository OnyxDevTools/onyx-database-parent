package com.onyx.interactors.record.impl

import com.onyx.diskmap.impl.DiskBTreeMap
import com.onyx.diskmap.store.StoreType
import com.onyx.exception.EntityCallbackException
import com.onyx.interactors.record.RecordInteractor
import com.onyx.lang.concurrent.ClosureReadWriteLock
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.PreRemove
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordMutationVirtualThreadTest {
    @Test fun `contended saves allow the reader and unrelated virtual threads to run`() = runScenario("save")
    @Test fun `contended deletes allow the reader and unrelated virtual threads to run`() = runScenario("delete")
    @Test fun `contended deletes by id allow the reader and unrelated virtual threads to run`() = runScenario("delete-id")
    @Test fun `sequence allocation and save allow the reader and unrelated virtual threads to run`() = runScenario("sequence")
    @Test fun `maintenance excludes every mutation and releases its lock after failure`() = runScenario("coordination")
    @Test fun `callbacks can reenter mutations and failure releases the lock`() = runScenario("callback")

    private fun runScenario(operation: String) {
        // A fresh scheduler is essential: properties cannot resize one already used by other tests.
        // Isolate a regression's pinned carriers so a failure cannot hang the Gradle test worker.
        val directory = Files.createTempDirectory("onyx-record-virtual-threads-")
        val output = directory.resolve("result.log").toFile()
        val classpath = buildList {
            addAll(System.getProperty("java.class.path").split(File.pathSeparator))
            generateSequence(javaClass.classLoader) { it.parent }
                .filterIsInstance<URLClassLoader>()
                .flatMap { it.urLs.asSequence() }
                .forEach { add(File(it.toURI()).path) }
        }.distinct().joinToString(File.pathSeparator)
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path,
            "-Djdk.virtualThreadScheduler.parallelism=2",
            "-Djdk.virtualThreadScheduler.maxPoolSize=2",
            "-cp", classpath,
            RecordMutationVirtualThreadScenario::class.java.name,
            operation, directory.resolve("database").toString(),
        ).redirectErrorStream(true).redirectOutput(output).start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Mutation scenario did not exit: ${output.readText()}")
            assertEquals(0, process.exitValue(), output.readText())
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                check(process.waitFor(5, TimeUnit.SECONDS))
            }
            directory.toFile().deleteRecursively()
        }
    }
}

/** Runs only against a disposable database in the child JVM created above. */
object RecordMutationVirtualThreadScenario {
    @JvmStatic
    fun main(arguments: Array<String>) {
        try {
            exercise(arguments[0], arguments[1])
        } catch (failure: Throwable) {
            failure.printStackTrace()
            // A regressed JVM cannot run the reader's finally block or safely close this fixture.
            // Only this disposable child is stopped; the parent removes its temporary directory.
            Runtime.getRuntime().halt(1)
        }
    }

    private fun exercise(operation: String, directory: String) {
        val factory = EmbeddedPersistenceManagerFactory(directory, directory, addShutdownHook = false).apply {
            storeType = StoreType.IN_MEMORY
            initialize()
        }
        val manager = factory.persistenceManager
        val sequential = operation == "sequence"
        val documents = (1L..2L).map { id ->
            if (sequential) SequenceDocument().apply { this.id = id }
            else MutationDocument().apply { this.id = id }
        }
        documents.forEach { manager.saveEntity(it) }
        val descriptor = requireNotNull(manager.context.getBaseDescriptorForEntity(documents.first().javaClass))
        val interactor = manager.context.getRecordInteractor(descriptor)
        val records = manager.context.getDataFile(descriptor)
            .getHashMap<DiskBTreeMap<Long, IManagedEntity>>(Long::class.java, descriptor.entityClass.name)
        if (operation == "coordination" || operation == "callback") {
            if (operation == "coordination") verifyCoordination(interactor, records)
            else verifyCallback(interactor, documents[0])
            factory.close()
            println("PASS: $operation preserves mutation exclusion and reentrancy on ${Runtime.version()}")
            return
        }
        // Hold the real map's read lock without changing production visibility or adding test hooks.
        val mapLock = DiskBTreeMap::class.java.getDeclaredField("mapReadWriteLock").let {
            it.isAccessible = true
            it.get(records) as ClosureReadWriteLock
        }
        val readerReady = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val threads = mutableListOf<Thread>()
        fun start(name: String, action: () -> Unit): Thread = Thread.ofVirtual().name(name)
            .uncaughtExceptionHandler { _, error -> failure.compareAndSet(null, error) }
            .start(action).also(threads::add)

        start("record-reader") {
            mapLock.readLock {
                readerReady.countDown()
                releaseReader.await()
            }
        }
        check(readerReady.await(5, TimeUnit.SECONDS))
        val mutations = if (sequential) List(2) { SequenceDocument() } else documents
        fun mutate(entity: IManagedEntity) {
            when (operation) {
                "save", "sequence" -> interactor.save(entity)
                "delete" -> interactor.delete(entity)
                "delete-id" -> interactor.deleteWithId((entity as MutationDocument).id)
                else -> error("Unknown operation: $operation")
            }
        }
        val first = start("first-$operation") { mutate(mutations[0]) }
        awaitWaiting(first)
        check(first.stackTrace.any { it.className == "java.util.concurrent.locks.ReentrantReadWriteLock\$WriteLock" }) {
            "First mutation must be waiting for the database write lock"
        }
        val second = start("second-$operation") { mutate(mutations[1]) }
        awaitWaiting(second)
        val unrelated = CompletableFuture<Boolean>()
        start("unrelated-request") { unrelated.complete(true) }
        try {
            unrelated.get(3, TimeUnit.SECONDS)
        } catch (timeout: Exception) {
            throw AssertionError("Contended $operation pinned the carriers and starved an unrelated request on ${Runtime.version()}", timeout)
        }
        check(releaseReader.count == 1L) { "The reader must still hold its lock when the unrelated request completes" }
        releaseReader.countDown()
        threads.forEach { check(it.join(Duration.ofSeconds(5))) { "${it.name} did not finish after releasing the reader" } }
        failure.get()?.let { throw AssertionError("Database mutation failed", it) }
        when (operation) {
            "save" -> check(records.longSize() == 2L && (1L..2L).all { records[it] != null })
            "sequence" -> {
                val ids = mutations.map { (it as SequenceDocument).id }
                check(ids.toSet().size == 2 && ids.all { it > 2L && records[it] != null })
                check(records.longSize() == 4L)
            }
            else -> check(records.longSize() == 0L)
        }
        factory.close()
        println("PASS: $operation preserves virtual-thread progress and database contents on ${Runtime.version()}")
    }

    private fun verifyCoordination(interactor: RecordInteractor, records: DiskBTreeMap<Long, IManagedEntity>) {
        val failure = AtomicReference<Throwable>()
        val threads = mutableListOf<Thread>()
        val expectedFailure = IllegalStateException("maintenance failed")
        val mutations = listOf<() -> Unit>(
            { interactor.save(MutationDocument().apply { id = 3L }) },
            { interactor.delete(MutationDocument().apply { id = 1L }) },
            { interactor.deleteWithId(2L) },
        )
        val thrown = runCatching {
            interactor.withMutationLock {
                // Maintenance and callbacks must be able to reenter ordinary record mutations.
                interactor.withMutationLock {
                    interactor.save(MutationDocument().apply { id = 4L })
                    check(interactor.deleteWithId(4L) != null)
                }
                mutations.forEachIndexed { index, mutation ->
                    val thread = Thread.ofVirtual().name("excluded-mutation-$index")
                        .uncaughtExceptionHandler { _, error -> failure.compareAndSet(null, error) }
                        .start(mutation)
                    threads.add(thread)
                    awaitWaiting(thread)
                }
                check(records[1L] != null && records[2L] != null && records[3L] == null)
                throw expectedFailure
            }
        }.exceptionOrNull()
        check(thrown === expectedFailure) { "Maintenance did not retain mutual exclusion: $thrown" }
        threads.forEach { check(it.join(Duration.ofSeconds(5))) { "Mutation stayed blocked after maintenance failed" } }
        failure.get()?.let { throw AssertionError("Excluded mutation failed", it) }
        check(records.longSize() == 1L && records[3L] != null)
    }

    private fun verifyCallback(interactor: RecordInteractor, document: IManagedEntity) {
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            MutationDocument.beforeRemove = {
                interactor.save(MutationDocument().apply { id = 3L })
                error("callback failed after reentering save")
            }
            val failure = executor.submit<Throwable?> {
                runCatching { interactor.delete(document) }.exceptionOrNull()
            }.get(5, TimeUnit.SECONDS)
            check(failure is EntityCallbackException) { "Expected the reentrant callback to fail: $failure" }
            MutationDocument.beforeRemove = null
            executor.submit {
                check(interactor.getWithId(1L) != null) // A failed pre-remove must not remove the document.
                check(interactor.getWithId(3L) != null) // The nested save completed under the same lock.
                interactor.delete(document)
                check(interactor.getWithId(1L) == null)
            }.get(5, TimeUnit.SECONDS)
        } finally {
            MutationDocument.beforeRemove = null
            executor.shutdownNow()
        }
    }

    private fun awaitWaiting(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (thread.state !in setOf(Thread.State.WAITING, Thread.State.BLOCKED)) {
            check(thread.isAlive && System.nanoTime() < deadline) { "${thread.name} did not reach its lock: ${thread.state}" }
            Thread.sleep(1)
        }
    }
}

@Entity
class MutationDocument : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L

    @PreRemove
    fun onRemove() { beforeRemove?.invoke() }

    companion object {
        @Volatile var beforeRemove: (() -> Unit)? = null
    }
}

@Entity
class SequenceDocument : ManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE) @Attribute var id: Long = 0L
}
