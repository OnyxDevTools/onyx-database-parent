package com.onyx.lucene.interactors

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.FilterDirectory
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuceneLifecycleTest {

    @Test
    fun closeWaitsForCommitToFinishBeforeReleasingWriterLock() {
        val indexPath = Files.createTempDirectory("onyx-lucene-lifecycle")
        val syncStarted = CountDownLatch(1)
        val allowSync = CountDownLatch(1)
        val blockSync = AtomicBoolean(false)
        val directory = BlockingSyncDirectory(
            FSDirectory.open(indexPath),
            blockSync,
            syncStarted,
            allowSync
        )
        val writer = IndexWriter(
            directory,
            IndexWriterConfig(StandardAnalyzer()).apply {
                openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
            }
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            writer.addDocument(Document().apply {
                add(StringField("id", "1", Field.Store.YES))
            })
            blockSync.set(true)

            val commit = executor.submit {
                LuceneLifecycle.withIndexLock(indexPath.toString()) {
                    writer.commit()
                }
            }
            assertTrue(syncStarted.await(10, TimeUnit.SECONDS), "Commit never reached directory sync")

            val closeStarted = CountDownLatch(1)
            val closeAcquiredLifecycleLock = CountDownLatch(1)
            val close = executor.submit {
                closeStarted.countDown()
                LuceneLifecycle.withIndexLock(indexPath.toString()) {
                    closeAcquiredLifecycleLock.countDown()
                    writer.close()
                }
            }

            assertTrue(closeStarted.await(10, TimeUnit.SECONDS), "Close task never started")
            assertFalse(
                closeAcquiredLifecycleLock.await(250, TimeUnit.MILLISECONDS),
                "Close acquired the lifecycle lock while commit was still pending"
            )

            allowSync.countDown()
            commit.get(10, TimeUnit.SECONDS)
            close.get(10, TimeUnit.SECONDS)
            assertFalse(writer.isOpen)

            // A second writer can open immediately only if the first writer released write.lock.
            FSDirectory.open(indexPath).use { reopenedDirectory ->
                IndexWriter(reopenedDirectory, IndexWriterConfig(StandardAnalyzer())).use { }
            }
        } finally {
            allowSync.countDown()
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
            if (writer.isOpen) runCatching { writer.rollback() }
            runCatching { directory.close() }
            indexPath.toFile().deleteRecursively()
        }
    }

    private class BlockingSyncDirectory(
        delegate: Directory,
        private val blockSync: AtomicBoolean,
        private val syncStarted: CountDownLatch,
        private val allowSync: CountDownLatch
    ) : FilterDirectory(delegate) {

        override fun sync(names: MutableCollection<String>) {
            if (blockSync.compareAndSet(true, false)) {
                syncStarted.countDown()
                check(allowSync.await(10, TimeUnit.SECONDS)) { "Timed out waiting to finish Lucene sync" }
            }
            super.sync(names)
        }
    }
}
