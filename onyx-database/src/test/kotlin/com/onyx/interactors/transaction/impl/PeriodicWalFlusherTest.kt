package com.onyx.interactors.transaction.impl

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PeriodicWalFlusherTest {

    @Test
    fun `registered targets are forced by one daemon thread`() {
        val forced = CountDownLatch(1)
        val forcingThread = AtomicReference<Thread>()
        val target = PeriodicWalForceTarget {
            forcingThread.compareAndSet(null, Thread.currentThread())
            forced.countDown()
        }

        try {
            PeriodicWalFlusher.register(target)

            assertTrue(forced.await(1, TimeUnit.SECONDS))
            val worker = assertNotNull(forcingThread.get())
            assertTrue(worker.isDaemon)
            assertEquals("onyx-wal-force", worker.name)
        } finally {
            PeriodicWalFlusher.unregister(target)
            assertTrue(waitUntil { PeriodicWalFlusher.currentWorker() == null })
        }
    }

    @Test
    fun `wal store registration follows its open and close lifecycle`() {
        val tempDirectory = Files.createTempDirectory("onyx-periodic-wal-lifecycle")
        val initialTargetCount = PeriodicWalFlusher.registeredTargetCount()
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())

        try {
            assertEquals(initialTargetCount, PeriodicWalFlusher.registeredTargetCount())

            transactionStore.getTransactionFile()

            assertEquals(initialTargetCount + 1, PeriodicWalFlusher.registeredTargetCount())
            assertTrue(PeriodicWalFlusher.currentWorker()?.isDaemon == true)

            transactionStore.close()

            assertEquals(initialTargetCount, PeriodicWalFlusher.registeredTargetCount())
            if (initialTargetCount == 0) {
                assertTrue(waitUntil { PeriodicWalFlusher.currentWorker() == null })
            }
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(1)
        }
        return condition()
    }

    private fun deleteDirectory(path: Path) {
        Files.walk(path).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
