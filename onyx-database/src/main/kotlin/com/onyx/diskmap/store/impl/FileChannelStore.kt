package com.onyx.diskmap.store.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferPool.withLongBuffer
import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.DEFAULT_DATA_FILE
import com.onyx.diskmap.store.Store
import com.onyx.exception.InitializationException
import com.onyx.extension.common.async
import com.onyx.extension.perform
import com.onyx.lang.concurrent.AtomicCounter
import com.onyx.lang.concurrent.impl.DefaultAtomicCounter
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * The default implementation of a store that includes the i/o of writing to a basic file channel.
 * This is recommended for larger data sets.
 *
 * This class also encapsulates the serialization of objects that are read and written to the store.
 *
 * @since 1.0.0
 */
open class FileChannelStore() : Store {

    protected var contextReference: WeakReference<SchemaContext>? = null

    override val context
        get() = contextReference?.get()

    final override var filePath: String = ""
    var deleteOnClose: Boolean = false // Whether to delete this file upon closing database or JVM
    var bufferSliceSize = if (isSmallDevice) SMALL_FILE_SLICE_SIZE else LARGE_FILE_SLICE_SIZE // Size of each slice

    internal var channel: FileChannel? = null
    protected var contextId: String? = null
    private var fileSizeCounter: AtomicCounter = DefaultAtomicCounter(0)
    private var physicalFileSize: Long = 0
    private val fileChannelPageSize = max(1, FILE_CHANNEL_PAGE_SIZE)
    private val maxFileChannelPageBuffers = max(1, MAX_FILE_CHANNEL_PAGE_BUFFERS)
    private val pageBufferLock = Any()
    private val pageBuffers = LinkedHashMap<Long, PageBuffer>(16, 0.75f, true)

    constructor(filePath: String = "", context: SchemaContext? = null, deleteOnClose: Boolean = false) : this() {
        this.bufferSliceSize = if (deleteOnClose || isSmallDevice) SMALL_FILE_SLICE_SIZE else LARGE_FILE_SLICE_SIZE
        this.deleteOnClose = deleteOnClose
        this.filePath = filePath
        this.contextId = context?.contextId
        this.contextReference = contextId?.let { WeakReference(Contexts.get(it)) }

        this.open(filePath = filePath)
        this.determineSize()
    }

    /**
     * Get the size of the file
     */
    override fun getFileSize(): Long = fileSizeCounter.get()

    /**
     * Open the data file
     *
     * @param filePath Path of the file to open
     * @return Whether the file was opened or not
     */
    open fun open(filePath: String): Boolean {
        val baseFile = File(filePath)
        val file = if (
            filePath.endsWith(File.separator) ||
            filePath.endsWith("/") ||
            baseFile.isDirectory
        ) {
            if (!baseFile.exists()) {
                baseFile.mkdirs()
            }
            val dataFile = File(baseFile, DEFAULT_DATA_FILE)
            this.filePath = dataFile.path
            dataFile
        } else {
            baseFile
        }
        try {
            // Create the data file if it does not exist
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            // Open the file channel
            this.channel = FileChannel.open(
                file.toPath(),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE
            )
            physicalFileSize = this.channel!!.size()
            this.fileSizeCounter.set(physicalFileSize)

        } catch (e: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            return false
        }

        return channel!!.isOpen
    }

    /**
     * Set the size after opening a file.  The first 8 bytes are reserved for the size.  The reason why we maintain the size
     * outside of relying of the fileChannel is because it may not be accurate.  In order to force it's accuracy
     * we have to configure the file channel to do so.  That causes the store to be severely slowed down.
     */
    protected open fun determineSize() {
        this.read(0, 8).perform {
            it?.byteBuffer?.rewind()
            if (it == null || channel?.size() == 0L) {
                this.allocate(8)
            } else {
                val fSize = it.long
                this.fileSizeCounter.set(fSize)
            }
        }
    }

    /**
     * Close the data file
     *
     * @return Whether the file was closed successfully.
     */
    override fun close(): Boolean = try {
        if (!deleteOnClose) {
            flushDirtyPages()
            this.channel!!.force(true)
        }
        clearPageBuffers()
        this.channel!!.close()
        async {
            if (deleteOnClose) {
                delete()
            }
        }
        !this.channel!!.isOpen
    } catch (e: IOException) {
        false
    }

    /**
     * Commit all file writes
     */
    override fun commit() {
        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
        if (this.channel?.isOpen == true) {
            flushDirtyPages()
            this.channel?.force(true)
        }
    }

    /**
     * Write an Object Buffer
     *
     * @param buffer Byte buffer to write
     * @param position Position within the volume to write to.
     * @return How many bytes were written
     */
    override fun write(buffer: ByteBuffer, position: Long): Int {
        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
        val written = buffer.remaining()
        synchronized(pageBufferLock) {
            var current = position
            while (buffer.hasRemaining()) {
                val page = pageBufferAt(current)
                val pageOffset = pageOffset(current)
                val bytesToWrite = min(buffer.remaining(), fileChannelPageSize - pageOffset)

                val destination = page.buffer.duplicate()
                destination.position(pageOffset)
                destination.limit(pageOffset + bytesToWrite)

                val originalLimit = buffer.limit()
                buffer.limit(buffer.position() + bytesToWrite)
                destination.put(buffer)
                buffer.limit(originalLimit)

                page.markDirty(pageOffset, pageOffset + bytesToWrite)
                current += bytesToWrite
            }
        }
        return written
    }

    /**
     * Write a serializable value to a volume.  This uses the BufferStream for serialization
     *
     * @param serializable Object
     * @param position Position to write to
     */
    override fun write(serializable: BufferStreamable, position: Long): Int = BufferStream().perform {
        serializable.write(it!!)
        it.flip()
        this.write(it.byteBuffer, position)
    }

    /**
     * Read a serializable value
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializable value to read into
     * @return same value instance that was sent in.
     */
    override fun read(position: Long, size: Int, serializable: BufferStreamable): Any? {
        if (!validateFileSize(position))
            return null

        return BufferPool.allocateAndLimit(size) {
            read(it, position)
            it.flip()
            serializable.read(BufferStream(it))
            return@allocateAndLimit serializable
        }
    }

    /**
     * Write a serializable value
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @return Object Buffer contains bytes read
     */
    override fun read(position: Long, size: Int): BufferStream? {
        if (!validateFileSize(position))
            return null

        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)

        val buffer = BufferPool.allocateAndLimit(size)
        this.read(buffer, position)
        buffer.flip()

        return BufferStream(buffer)
    }

    /**
     * Read the file channel and put it into a buffer at a position
     *
     * @param buffer   Buffer to put into
     * @param position position in store to read
     */
    override fun read(buffer: ByteBuffer, position: Long) {
        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
        synchronized(pageBufferLock) {
            var current = position
            while (buffer.hasRemaining()) {
                val page = pageBufferAt(current)
                val pageOffset = pageOffset(current)
                val bytesToRead = min(buffer.remaining(), fileChannelPageSize - pageOffset)

                val source = page.buffer.duplicate()
                source.position(pageOffset)
                source.limit(pageOffset + bytesToRead)
                buffer.put(source)

                current += bytesToRead
            }
        }
    }

    /**
     * Validate we are not going to read beyond the allocated file storage.  This would be bad
     * @param position Position to validate
     * @return whether the value you seek is in a valid position
     */
    protected open fun validateFileSize(position: Long): Boolean = position < fileSizeCounter.get()

    /**
     * Allocates a spot in the file
     *
     * @param size Allocate space within the store.
     * @return position of started allocated bytes
     */
    override fun allocate(size: Int): Long = withLongBuffer {
        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
        val newFileSize = fileSizeCounter.getAndAdd(size)
        it.putLong(newFileSize + size)
        it.rewind()
        this.write(it, 0)
        return@withLongBuffer newFileSize
    }

    /**
     * Delete File
     */
    override fun delete() {
        clearPageBuffers()
        val dataFile = File(filePath)
        dataFile.delete()
    }

    /**
     * Retrieve an object at position.  This will automatically determine its
     * size and de-serialize the object
     *
     * @param position Position in the store to retrieve object
     * @since 2.0.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> getObject(position: Long):T {
        val size = BufferPool.withIntBuffer {
            this.read(it, position)
            it.rewind()
            it.int
        }

        if (size == 0) return null as T

        val storeBuffer = localBuffer

        if(size <= storeBuffer.capacity()) {
            storeBuffer.clear()
            storeBuffer.limit(size)
            this.read(storeBuffer, position + Integer.BYTES)
            storeBuffer.rewind()
            @Suppress("UNCHECKED_CAST")
            return BufferStream(storeBuffer).getObject(context) as T
        } else {
            BufferPool.allocateAndLimit(size) {
                this.read(it, position + Integer.BYTES)
                it.rewind()
                @Suppress("UNCHECKED_CAST")
                return BufferStream(it).getObject(context) as T
            }
        }
    }

    /**
     *
     * Reset the storage so that it has a clean slate
     * and truncates all relative data.
     *
     * @since 1.3.0
     */
    override fun reset() {
        synchronized(pageBufferLock) {
            fileSizeCounter.set(0)
            physicalFileSize = 0
            pageBuffers.clear()
        }
        this.allocate(8)
    }

    private val localBuffer: ByteBuffer
        get() = threadLocalBuffer.get()

    private fun pageBufferAt(position: Long): PageBuffer {
        val pageIndex = position / fileChannelPageSize
        pageBuffers[pageIndex]?.let { return it }

        val page = PageBuffer(pageIndex, fileChannelPageSize)
        readPage(page)
        pageBuffers[pageIndex] = page
        evictPageBuffers()
        return page
    }

    private fun readPage(page: PageBuffer) {
        val pagePosition = page.index * fileChannelPageSize
        if (pagePosition >= physicalFileSize) {
            return
        }

        val bytesAvailable = min(fileChannelPageSize.toLong(), physicalFileSize - pagePosition).toInt()
        val destination = page.buffer.duplicate()
        destination.clear()
        destination.limit(bytesAvailable)

        var current = pagePosition
        while (destination.hasRemaining()) {
            val read = channel!!.read(destination, current)
            if (read <= 0) {
                break
            }
            current += read
        }
    }

    private fun evictPageBuffers() {
        while (pageBuffers.size > maxFileChannelPageBuffers) {
            val iterator = pageBuffers.entries.iterator()
            if (!iterator.hasNext()) {
                return
            }

            val page = iterator.next().value
            flushPage(page)
            iterator.remove()
        }
    }

    private fun flushDirtyPages() = synchronized(pageBufferLock) {
        pageBuffers.values.forEach { flushPage(it) }
    }

    private fun flushPage(page: PageBuffer) {
        if (!page.dirty) {
            return
        }

        val source = page.buffer.duplicate()
        source.position(page.dirtyStart)
        source.limit(page.dirtyEnd)

        var current = page.index * fileChannelPageSize + page.dirtyStart
        while (source.hasRemaining()) {
            val bytesWritten = channel!!.write(source, current)
            if (bytesWritten <= 0) {
                Thread.yield()
                continue
            }
            current += bytesWritten
        }

        physicalFileSize = max(physicalFileSize, current)
        page.clearDirty()
    }

    private fun clearPageBuffers() = synchronized(pageBufferLock) {
        pageBuffers.clear()
    }

    private fun pageOffset(position: Long): Int = (position % fileChannelPageSize).toInt()

    private class PageBuffer(val index: Long, private val pageSize: Int) {
        val buffer: ByteBuffer = ByteBuffer.allocate(pageSize)
        var dirty: Boolean = false
        var dirtyStart: Int = pageSize
        var dirtyEnd: Int = 0

        fun markDirty(start: Int, end: Int) {
            dirty = true
            dirtyStart = min(dirtyStart, start)
            dirtyEnd = max(dirtyEnd, end)
        }

        fun clearDirty() {
            dirty = false
            dirtyStart = pageSize
            dirtyEnd = 0
        }
    }

    companion object {
        const val SMALL_FILE_SLICE_SIZE = 1024 * 128 // 128K
        var LARGE_FILE_SLICE_SIZE = 1024 * 1024 * 4 // 4MB
        var FILE_CHANNEL_PAGE_SIZE = 1024 * 64 // 64KB
        var MAX_FILE_CHANNEL_PAGE_BUFFERS = 256

        val isSmallDevice:Boolean by lazy {
            try {
                Class.forName("android.app.Activity")
            } catch (e: ClassNotFoundException) {
                return@lazy false
            }
            return@lazy true
        }

        private val threadLocalBuffer: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
            ByteBuffer.allocate(20000)
        }
    }
}
