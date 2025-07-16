package com.onyx.diskmap.store.impl

import com.onyx.buffer.copy
import com.onyx.diskmap.store.Store
import com.onyx.exception.InitializationException
import com.onyx.extension.common.safeMemoryMap
import com.onyx.persistence.context.SchemaContext
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
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
    constructor(filePath: String, context: SchemaContext?, deleteOnClose: Boolean) : super(
        filePath,
        context,
        deleteOnClose
    )

    /**
     * Opens the file at the specified path and memory-maps the initial buffer slice.
     * @param filePath The path to the file to open.
     * @return True if the file was opened successfully, false otherwise.
     */
    override fun open(filePath: String): Boolean {
        try {
            fileId = fileIdCounter.incrementAndGet()
            super.open(filePath)
            val buf = channel!!.map(FileChannel.MapMode.READ_WRITE, 0, bufferSliceSize.toLong())
            cache[Key(fileId, 0)] = SoftReference(buf)
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
            val destination = getBuffer(current).duplicate()
            destination.position(getBufferLocation(current))
            current += copy(buffer, destination)
        }
        return (current - position).toInt()
    }

    /**
     * Reads data from the store at the specified position into the destination buffer.
     * @param buffer The buffer to read data into.
     * @param position The position in the store to read from.
     */
    override fun read(buffer: ByteBuffer, position: Long) {
        var current = position
        while (buffer.hasRemaining()) {
            val source = getBuffer(current).duplicate()
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
        val idx = (position / bufferSliceSize).toInt()
        val key = Key(fileId, idx)
        var slice: ByteBuffer? = null

        cache.compute(key) { k, existingSoftRef ->
            slice = existingSoftRef?.get()
            if (slice != null) {
                existingSoftRef
            } else {
                slice = channel!!.safeMemoryMap(k.idx.toLong() * bufferSliceSize, bufferSliceSize) {

                }
                SoftReference(slice)
            }
        }

        return slice!!
    }

    /**
     * Calculates the location within a buffer slice for a given absolute file position.
     * @param position The absolute file position.
     * @return The relative position within a buffer slice.
     */
    private fun getBufferLocation(position: Long) = (position % bufferSliceSize).toInt()

    /**
     * Closes the store, removing its associated buffers from the cache.
     * @return True if the store was closed successfully, false otherwise.
     */
    override fun close(): Boolean {
        val keysForThisInstance = cache.keys
            .filter { it.fileId == fileId }

        keysForThisInstance.forEach { keyToRemove ->
            cache.remove(keyToRemove)
        }

        return super.close()
    }

    /**
     * Commits any changes to the store.
     * This is a no-op if deleteOnClose is true.
     */
    override fun commit() {
        if (!deleteOnClose) super.commit()
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
         * Data class representing a key for the buffer cache.
         * It consists of a fileId and a slice index.
         * @property fileId The unique ID of the file.
         * @property idx The index of the buffer slice within the file.
         */
        private data class Key(val fileId: Int, val idx: Int)

        /**
         * A concurrent linked hash map used to cache [SoftReference]s to [ByteBuffer] slices.
         * This cache is shared among all [MemoryMappedStore] instances.
         * When an entry is evicted, if it's a [MappedByteBuffer], its contents are forced to disk.
         */
        private val cache = ConcurrentHashMap<Key, SoftReference<ByteBuffer>>()
    }
}
