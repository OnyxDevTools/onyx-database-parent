package com.onyx.extension.common

import java.io.Closeable
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Provides memory-mapped file access with caching and asynchronous flushing.
 *
 * This class manages a file using memory-mapped pages, employing a cache with both
 * strong (`Page`) and soft (`SoftReference<Page>`) references to manage memory usage.
 * Writes are debounced and flushed asynchronously to disk.
 *
 */
class MemoryMappedFile(path: Path) : Closeable {

    init {
        Runtime.getRuntime().addShutdownHook(Thread { close() })
    }

    /** The underlying file channel used for reading, writing, and mapping. */
    private val channel = FileChannel.open(path, READ, WRITE, CREATE)

    /** A unique identifier for this specific file instance, used for cache keys. */
    private val fileId = fileIdCounter.incrementAndGet()

    /**
     * Represents a single page of the memory-mapped file in the cache.
     *
     * @property index The zero-based index of this page within the file.
     * @property buf The ByteBuffer holding the page's data.
     * @property lastDirty The system nano time when this page was last marked dirty. Used for debouncing flushes.
     * @property flags Atomic integer holding the page's status flags (e.g., CLEAN, DIRTY, ENQ).
     * @property flushFn A function reference to flush this specific page to disk.
     */
    private class Page(
        val index: Long,
        val buf: ByteBuffer,
        @Volatile var lastDirty: Long = 0L,
        val flags: AtomicInteger = AtomicInteger(Flags.CLEAN),
        val flushFn: (Page) -> Unit
    )

    /**
     * Reads data from the file into the destination buffer.
     *
     * Reads start at the given file offset `off`. Data is read page by page
     * until the destination buffer `dst` is full.
     *
     * @param off The starting offset in the file to read from.
     * @param dst The ByteBuffer to read data into.
     */
    fun read(off: Long, dst: ByteBuffer) {
        var pos = off
        while (dst.hasRemaining()) {
            val pg = page(pos / PAGE_SIZE)
            val ix = (pos % PAGE_SIZE).toInt()
            val n = min(dst.remaining(), PAGE_SIZE - ix)
            dst.put(pg.buf.duplicate().apply { position(ix); limit(ix + n) })
            pos += n
        }
    }

    /**
     * Writes data from the source buffer to the file.
     *
     * Writes start at the given file offset `off`. Data is written page by page
     * from the source buffer `src`. Pages modified are marked dirty and enqueued
     * for asynchronous flushing. The file is truncated if the write extends beyond
     * its current size.
     *
     * @param off The starting offset in the file to write to.
     * @param src The ByteBuffer containing the data to write.
     * @return The original limit of the source buffer `src` (effectively the number of bytes intended to be written).
     */
    fun write(off: Long, src: ByteBuffer): Int {
        val origLimit = src.limit()
        var pos = off
        var maxWrite = pos

        while (src.hasRemaining()) {
            val pg = page(pos / PAGE_SIZE)
            val ix = (pos % PAGE_SIZE).toInt()
            val n = min(src.remaining(), PAGE_SIZE - ix)

            synchronized(pg) {
                pg.buf.position(ix)
                val oldLim = src.limit()
                src.limit(src.position() + n)
                pg.buf.put(src)
                src.limit(oldLim)
                pg.lastDirty = System.nanoTime()
            }

            val wasDirty = (pg.flags.getAndUpdate { it or Flags.DIRTY } and Flags.DIRTY) == 0
            if (wasDirty) promote(pg)

            val wasEnq = (pg.flags.getAndUpdate { it or Flags.ENQ } and Flags.ENQ) != 0
            if (!wasEnq) {
                if (writeQueue.size >= MAX_DIRTY_ENQUEUED) {
                    flush(writeQueue.take().page)
                }
                writeQueue.offer(DelayedPage(pg))
            }

            pos += n
            maxWrite = maxOf(maxWrite, pos)


        }

        val oldSize = channel.size()
        if (maxWrite > oldSize) channel.truncate(maxWrite)

        return origLimit
    }

    /**
     * Flushes all dirty pages associated with *this* file instance to disk synchronously.
     * Iterates through the shared cache, finds pages belonging to this file,
     * dereferences them, and calls the private `flush` method on each dirty page.
     */
    fun flush() {
        for ((k, v) in cache) if (k.fileId == fileId) {
            deref(v)?.let(::flush)
        }
    }

    /** Checks if the underlying file channel is open. */
    val isOpen: Boolean get() = channel.isOpen

    /**
     * Flushes any remaining dirty pages for this file and closes the file channel.
     * Removes all cache entries associated with this file instance.
     */
    override fun close() {
        flush()
        cache.keys.removeIf { it.fileId == fileId }
        channel.close()
    }

    /**
     * Safely unwraps a potential `SoftReference` to get the underlying `Page` object.
     * Handles cases where the cache entry might be a direct `Page` (promoted) or
     * a `SoftReference<Page>`.
     *
     * @param e The cache entry object (potentially a Page or SoftReference<Page>).
     * @return The `Page` object if it exists and hasn't been garbage collected, null otherwise.
     */
    @Suppress("UNCHECKED_CAST")
    private fun deref(e: Any?): Page? = when (e) {
        is Page -> e
        is SoftReference<*> -> (e as SoftReference<Page>).get()
        else -> null
    }

    /**
     * Retrieves or loads a specific page from the cache or file.
     *
     * If the page exists in the cache (either as a strong or soft reference), it's returned.
     * If the page is not in the cache or the soft reference has been cleared, it attempts
     * to load the page from the file channel. If an OutOfMemoryError occurs during allocation,
     * it triggers garbage collection and memory release before retrying.
     *
     * @param idx The index of the page to retrieve or load.
     * @return The requested `Page` object.
     * @throws OutOfMemoryError if memory allocation fails repeatedly after GC attempts.
     */
    private fun page(idx: Long): Page {
        val key = Key(fileId, idx)
        val entry = cache.compute(key) { _, v ->
            while (true) {
                deref(v)?.let { return@compute v }
                try {
                    val pageBuf = ByteBuffer.allocate(PAGE_SIZE)
                    val page = Page(idx, pageBuf, flushFn = ::flush)

                    if (idx * PAGE_SIZE < channel.size()) {
                        channel.read(page.buf, idx * PAGE_SIZE)
                        page.buf.clear()
                    }
                    return@compute SoftReference(page)
                } catch (oom: OutOfMemoryError) {
                    println("Memory critically low – attempting GC")
                    releaseMemory()
                }
            }
        }
        return deref(entry)!!
    }

    /**
     * Promotes a page in the cache from a `SoftReference` to a strong reference (`Page`).
     * This is typically done when a page becomes dirty, preventing it from being
     * garbage-collected prematurely. Acquires a permit from the `dirtySemaphore`.
     *
     * @param pg The page to promote.
     */
    private fun promote(pg: Page) {
        dirtySemaphore.acquire()
        cache[Key(fileId, pg.index)] = pg
    }

    /**
     * Flushes the contents of a specific page to the disk.
     *
     * This operation is synchronized on the page object. It writes the page buffer
     * to the corresponding file offset. After flushing, it clears the DIRTY and ENQ flags.
     * If the page was successfully demoted back to a `SoftReference` in the cache,
     * it releases a permit back to the `dirtySemaphore`.
     *
     * @param page The page to flush.
     */
    private fun flush(page: Page) {

        synchronized(page) {
            val wasDirty = page.flags.getAndSet(Flags.CLEAN) and Flags.DIRTY != 0
            if (!wasDirty) return

            page.buf.rewind()
            channel.write(page.buf, page.index * PAGE_SIZE)
            page.buf.clear()
        }

        page.flags.getAndUpdate { it and Flags.ENQ.inv() }

        if (cache.replace(Key(fileId, page.index), page, SoftReference(page))) {
            dirtySemaphore.release()
        }
    }

    /**
     * Companion object holding shared state and constants for all MemoryMappedFile instances.
     */
    companion object {
        /** Maximum number of dirty pages allowed before writes block or force flushes. */
        private const val MAX_DIRTY_ENQUEUED = 16_384

        /** The size of each memory-mapped page in bytes (16 KiB). */
        private const val PAGE_SIZE = 16 * 1024

        /** The debounce delay in nanoseconds for flushing dirty pages (1 second). */
        private const val DEBOUNCE_NS = 1_000_000_000L

        /** Defines bit flags for page status. */
        private object Flags {
            /** Page is clean (not modified since last flush). */
            const val CLEAN = 0

            /** Page is dirty (modified and needs flushing). */
            const val DIRTY = 1

            /** Page is enqueued in the `writeQueue` for flushing. */
            const val ENQ = 1 shl 1
        }

        /** Limits the number of pages that can be simultaneously dirty (promoted to strong references). */
        private val dirtySemaphore = Semaphore(MAX_DIRTY_ENQUEUED)

        /** Counter to assign unique IDs to each MemoryMappedFile instance. */
        private val fileIdCounter = AtomicInteger()

        /** Shared cache mapping file/page keys to Page objects (either direct or via SoftReference). */
        private val cache = ConcurrentHashMap<Key, Any>()

        /** Delay queue holding dirty pages waiting to be flushed after a debounce period. */
        private val writeQueue = DelayQueue<DelayedPage>()

        /**
         * Represents the key used in the shared cache. Combines the file ID and page index.
         * @property fileId Unique ID of the MemoryMappedFile instance.
         * @property idx Zero-based index of the page within the file.
         */
        private data class Key(val fileId: Int, val idx: Long)

        /**
         * Wrapper for a `Page` used in the `DelayQueue`. Implements `Delayed`
         * based on the page's `lastDirty` time plus the `DEBOUNCE_NS`.
         *
         * @property page The dirty page to be flushed.
         */
        private class DelayedPage(val page: Page) : Delayed {
            /** The target time (in nanoseconds) when the page should be flushed. */
            private val targetNs = page.lastDirty + DEBOUNCE_NS

            /**
             * Calculates the remaining delay until the target flush time.
             * @param u The time unit for the returned delay.
             * @return The remaining delay in the specified time unit.
             */
            override fun getDelay(u: TimeUnit) =
                u.convert(targetNs - System.nanoTime(), TimeUnit.NANOSECONDS)

            /**
             * Compares this DelayedPage with another Delayed object based on their target flush times.
             * @param other The other Delayed object to compare against.
             * @return A negative integer, zero, or a positive integer as this object's delay
             * is less than, equal to, or greater than the specified object's delay.
             */
            @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
            override fun compareTo(other: Delayed): Int =
                when (other) {
                    is DelayedPage -> java.lang.Long.compare(targetNs, other.targetNs)
                    else -> getDelay(TimeUnit.NANOSECONDS)
                        .compareTo(other.getDelay(TimeUnit.NANOSECONDS))
                }
        }

        /**
         * Attempts to free up memory by removing soft references from the cache
         * and suggesting garbage collection. Should be called when an OutOfMemoryError
         * occurs.
         */
        fun releaseMemory() {
            cache.entries.removeIf { (_, v) -> v is SoftReference<*> }
            System.gc()
            Thread.sleep(200)
        }

        /**
         * Initializes and starts the background flush thread.
         * This daemon thread continuously takes pages from the `writeQueue` as they become
         * eligible (after their delay) and flushes them using their associated `flushFn`.
         */
        init {
            Thread({
                while (true) {
                    try {
                        val pg = writeQueue.take().page
                        pg.flushFn(pg)
                    } catch (e: Exception) {
                        println("Exception while trying to flush file ${e.message}")
                        Thread.sleep(100)
                    }
                }
            }, "mmf‑flush‑thread").apply { isDaemon = true }.start()
        }
    }
}
