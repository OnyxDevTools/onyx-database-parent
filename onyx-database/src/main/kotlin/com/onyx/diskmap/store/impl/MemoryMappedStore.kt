package com.onyx.diskmap.store.impl

import com.onyx.buffer.copy
import com.onyx.diskmap.store.Store
import com.onyx.exception.InitializationException
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [Store] implementation that uses memory-mapped files for I/O operations.
 * This class extends [FileChannelStore] and provides methods to read, write,
 * and manage memory-mapped buffers.
 */
open class MemoryMappedStore : FileChannelStore, Store {

    /**
     * Unique identifier for this file instance.
     */
    private var fileId: Int = 0
    private var physicalEndBeforeWarmMapping: Long? = null

    /**
     * Default constructor.
     */
    constructor()

    /**
     * Constructs a [MemoryMappedStore] with the given file path, schema context, and deleteOnClose flag.
     * @param filePath The path to the file.
     * @param context The schema context.
     * @param deleteOnClose True if the file should be deleted on close, false otherwise.
     */
    constructor(filePath: String, context: SchemaContext?, deleteOnClose: Boolean) : super() {
        this.bufferSliceSize = if (deleteOnClose || FileChannelStore.isSmallDevice) {
            FileChannelStore.SMALL_FILE_SLICE_SIZE
        } else {
            FileChannelStore.LARGE_FILE_SLICE_SIZE
        }
        this.deleteOnClose = deleteOnClose
        this.filePath = filePath
        this.contextId = context?.contextId
        this.contextReference = contextId?.let { WeakReference(Contexts.get(it)) }

        this.open(filePath = filePath)
        this.determineSize()
    }

    /**
     * Opens the file at the specified path and memory-maps the initial buffer slice.
     * @param filePath The path to the file to open.
     * @return True if the file was opened successfully, false otherwise.
     */
    override fun open(filePath: String): Boolean {
        try {
            fileId = nextMappedFileId()
            if (!super.open(filePath)) {
                return false
            }
            physicalEndBeforeWarmMapping = channel?.size()
            // Warm the first slice to preserve the previous open-time mapping behavior.
            getBuffer(0)
            return true
        } catch (_: FileNotFoundException) {
            return false
        } catch (_: IOException) {
            return false
        }
    }

    /**
     * Writes data from the source buffer to the store at the specified position.
     * @param buffer The buffer containing the data to write.
     * @param position The position in the store to write to.
     * @return The number of bytes written.
     */
    override fun write(buffer: ByteBuffer, position: Long): Int {
        var current = position
        while (buffer.hasRemaining()) {
            val destination = getBuffer(current)
            destination.position(getBufferLocation(current))
            current += copy(buffer, destination)
        }
        return (current - position).toInt()
    }

    override fun recoverLogicalEnd(persistedReservationEnd: Long, physicalEnd: Long): Long =
        super.recoverLogicalEnd(
            persistedReservationEnd,
            physicalEndBeforeWarmMapping ?: physicalEnd
        )

    /**
     * Reads data from the store at the specified position into the destination buffer.
     * @param buffer The buffer to read data into.
     * @param position The position in the store to read from.
     */
    override fun read(buffer: ByteBuffer, position: Long) {
        var current = position
        while (buffer.hasRemaining()) {
            val source = getBuffer(current)
            source.position(getBufferLocation(current))
            current += copy(source, buffer)
        }
    }

    /**
     * Retrieves or maps a [ByteBuffer] for the given file position.
     * This method manages a cache of memory-mapped buffers.
     * @param position The file position for which to get the buffer.
     * @return The [ByteBuffer] for the specified position.
     * @throws InitializationException if the store is not open.
     */
    open fun getBuffer(position: Long): ByteBuffer {
        ensureOpen()
        val key = keyForPosition(position)
        return mappedFileSegmentCache.getBuffer(key) {
            mapSegment(key)
        }
    }

    /**
     * Calculates the location within a buffer slice for a given absolute file position.
     * @param position The absolute file position.
     * @return The relative position within a buffer slice.
     */
    private fun getBufferLocation(position: Long) = (position % bufferSliceSize).toInt()

    private fun keyForPosition(position: Long): MappedFileSegmentKey =
        MappedFileSegmentKey(fileId, (position / bufferSliceSize).toInt())

    private fun mapSegment(key: MappedFileSegmentKey): MappedFileSegment =
        MappedFileSegmentFactory.map(
            channel = channel!!,
            offset = key.idx.toLong() * bufferSliceSize,
            size = bufferSliceSize
        ) {
            mappedFileSegmentCache.evictLeastRecentlyUsed()
        }

    /**
     * Closes the store, removing its associated buffers from the cache.
     * @return True if the store was closed successfully, false otherwise.
     */
    override fun close(): Boolean {
        // Persist the exact allocated end while the header mapping is still live,
        // then evict mappings before truncating their physical reservation.
        var strictlyForced = true
        if (!deleteOnClose && channel?.isOpen == true) {
            try {
                finishAllocationReservations()
                forceMappedFileSegments(fileId)
            } catch (_: Throwable) {
                // Still evict mappings and close the channel, but do not
                // truncate after a failed strict force.
                strictlyForced = false
            }
        }
        mappedFileSegmentCache.removeFile(fileId)
        val truncated = deleteOnClose || channel?.isOpen != true || strictlyForced && try {
            channel?.truncate(getFileSize())
            true
        } catch (_: IOException) {
            false
        }
        val closed = try {
            super.close()
        } catch (_: Throwable) {
            runCatching { channel?.close() }
            false
        }
        return strictlyForced && truncated && closed
    }

    /**
     * Commits any changes to the store.
     * This is a no-op if deleteOnClose is true.
     */
    override fun commit() {
        if (!deleteOnClose) {
            super.commit()
        }
    }

    override fun forceWrites() {
        forceMappedFileSegments(fileId)
        super.forceWrites()
    }

    /**
     * Ensures that the file channel is open.
     * @throws InitializationException if the channel is not open.
     */
    protected open fun ensureOpen() {
        if (!channel!!.isOpen) throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
    }

    /**
     * Companion object for [MemoryMappedStore].
     * Contains constants and shared resources.
     */
    companion object {
        /**
         * A counter to generate unique file IDs for different instances of [MemoryMappedStore].
         */
        private val fileIdCounter: AtomicInteger = AtomicInteger()

        /**
         * Default maximum cached chunks for regular JVM runtimes.
         */
        const val DEFAULT_JVM_MAX_CACHED_FILE_CHUNKS = 64 * 1024

        /**
         * Default maximum cached chunks for Android runtimes.
         */
        const val DEFAULT_ANDROID_MAX_CACHED_FILE_CHUNKS = 1024

        /**
         * Default maximum cached chunks for the current runtime.
         */
        @JvmStatic
        val defaultMaxCachedFileChunks: Int
            get() = if (FileChannelStore.isSmallDevice) {
                DEFAULT_ANDROID_MAX_CACHED_FILE_CHUNKS
            } else {
                DEFAULT_JVM_MAX_CACHED_FILE_CHUNKS
            }

        private val mappedFileSegmentCache = MappedFileSegmentCache(defaultMaxCachedFileChunks)

        internal fun nextMappedFileId(): Int = fileIdCounter.incrementAndGet()

        internal fun getMappedFileSegmentBuffer(
            key: MappedFileSegmentKey,
            mapper: () -> MappedFileSegment
        ): ByteBuffer = mappedFileSegmentCache.getBuffer(key, mapper)

        internal fun removeMappedFileSegments(fileId: Int) {
            mappedFileSegmentCache.removeFile(fileId)
        }

        internal fun forceMappedFileSegments(fileId: Int) {
            mappedFileSegmentCache.forceFile(fileId)
        }

        internal fun evictLeastRecentlyUsedMappedFileSegment(): Boolean =
            mappedFileSegmentCache.evictLeastRecentlyUsed()

        /**
         * Maximum number of memory-mapped file chunks retained globally across all stores.
         */
        @JvmStatic
        var maxCachedFileChunks: Int
            get() = mappedFileSegmentCache.maxChunks
            set(value) {
                mappedFileSegmentCache.maxChunks = value
            }

        /**
         * Current number of mapped file chunks retained by the global cache.
         */
        @JvmStatic
        val cachedFileChunkCount: Int
            get() = mappedFileSegmentCache.size
    }
}
