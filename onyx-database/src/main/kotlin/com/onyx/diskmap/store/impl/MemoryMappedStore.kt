package com.onyx.diskmap.store.impl

import com.onyx.diskmap.store.Store
import com.onyx.exception.InitializationException
import com.onyx.extension.common.MemoryMappedFile
import com.onyx.persistence.context.SchemaContext
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Paths

/**
 * A [Store] implementation that uses memory-mapped files for I/O operations.
 * This class extends [FileChannelStore] and provides methods to read, write,
 * and manage memory-mapped buffers.
 */
open class MemoryMappedStore : FileChannelStore, Store {

    private lateinit var buffer: MemoryMappedFile

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
            super.open(filePath)
            buffer = MemoryMappedFile(Paths.get(filePath))
            return true
        } catch (_: FileNotFoundException) {
            return false
        } catch (_: IOException) {
            return false
        }
    }

    override fun write(buffer: ByteBuffer, position: Long): Int =
        this.buffer.write(position, buffer)

    override fun read(buffer: ByteBuffer, position: Long) {
        this.buffer.read(position, buffer)
    }


    /**
     * Closes the store, removing its associated buffers from the cache.
     * @return True if the store was closed successfully, false otherwise.
     */
    override fun close(): Boolean {
        this.buffer.close()
        return true
    }

    /**
     * Commits any changes to the store.
     * This is a no-op if deleteOnClose is true.
     */
    override fun commit() {
        this.buffer.flush()
    }

    /**
     * Ensures that the file channel is open.
     * @throws InitializationException if the channel is not open.
     */
    protected open fun ensureOpen() {
        if (!this.buffer.isOpen) throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
    }
}
