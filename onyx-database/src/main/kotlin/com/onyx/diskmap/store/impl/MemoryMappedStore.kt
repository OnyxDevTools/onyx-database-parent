package com.onyx.diskmap.store.impl

import com.onyx.diskmap.store.Store
import com.onyx.exception.InitializationException
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * A [Store] implementation that uses memory-mapped files for I/O operations.
 * This class extends [FileChannelStore] and provides methods to read, write,
 * and manage one whole-file memory mapping.
 */
open class MemoryMappedStore : FileChannelStore, Store {

    private val mappingLock = ReentrantReadWriteLock()
    private val mappingReadLock = mappingLock.readLock()
    private val mappingWriteLock = mappingLock.writeLock()
    private var mappingGrowthQuantum = WholeFileMapping.defaultGrowthQuantum(FileChannelStore.isSmallDevice)
    private var wholeFileMapping: WholeFileMapping? = null
    private var physicalEndBeforeMapping: Long? = null

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
        this.mappingGrowthQuantum = WholeFileMapping.defaultGrowthQuantum(
            deleteOnClose || FileChannelStore.isSmallDevice
        )
        this.deleteOnClose = deleteOnClose
        this.filePath = filePath
        this.contextId = context?.contextId
        this.contextReference = contextId?.let { WeakReference(Contexts.get(it)) }

        this.open(filePath = filePath)
        this.determineSize()
    }

    /**
     * Opens the file and creates one mapping that covers its complete physical extent.
     * @param filePath The path to the file to open.
     * @return True if the file was opened successfully, false otherwise.
     */
    override fun open(filePath: String): Boolean = mappingWriteLock.withLock {
        try {
            if (!super.open(filePath)) {
                return false
            }
            physicalEndBeforeMapping = channel?.size()
            wholeFileMapping = WholeFileMapping(
                channel = channel!!,
                growthQuantum = mappingGrowthQuantum,
                initialRequiredCapacity = physicalEndBeforeMapping ?: 0L
            )
            return true
        } catch (_: FileNotFoundException) {
            closeAfterFailedOpen()
            return false
        } catch (_: IOException) {
            closeAfterFailedOpen()
            return false
        }
    }

    /**
     * Writes data from the source buffer to the store at the specified position.
     * @param buffer The buffer containing the data to write.
     * @param position The position in the store to write to.
     * @return The number of bytes written.
     */
    override fun write(buffer: ByteBuffer, position: Long): Int = mappingWriteLock.withLock {
        currentMapping().write(buffer, position)
    }

    override fun recoverLogicalEnd(persistedReservationEnd: Long, physicalEnd: Long): Long =
        super.recoverLogicalEnd(
            persistedReservationEnd,
            physicalEndBeforeMapping ?: physicalEnd
        )

    /**
     * Reads data from the store at the specified position into the destination buffer.
     * @param buffer The buffer to read data into.
     * @param position The position in the store to read from.
     */
    override fun read(buffer: ByteBuffer, position: Long) {
        require(position >= 0L) { "File position cannot be negative: $position" }
        val endExclusive = Math.addExact(position, buffer.remaining().toLong())

        mappingReadLock.withLock {
            val current = currentMapping()
            if (endExclusive <= current.capacity) {
                current.read(buffer, position)
                return
            }
        }

        // Allocated-but-unwritten ranges can be beyond the current mapping.
        // Grow only on that uncommon path, then perform the read while the
        // exclusive lock keeps the arena stable.
        mappingWriteLock.withLock {
            currentMapping().let {
                it.ensureCapacity(endExclusive)
                it.read(buffer, position)
            }
        }
    }

    override fun allocate(size: Int): Long = mappingWriteLock.withLock {
        super<FileChannelStore>.allocate(size).also { ensureAllocatedCapacity() }
    }

    override fun allocateAligned(size: Int, alignment: Int): Long = mappingWriteLock.withLock {
        super<FileChannelStore>.allocateAligned(size, alignment).also { ensureAllocatedCapacity() }
    }

    override fun allocateSlot(size: Int): Long = mappingWriteLock.withLock {
        super<FileChannelStore>.allocateSlot(size).also { ensureAllocatedCapacity() }
    }

    override fun allocateObject(size: Int): Long = mappingWriteLock.withLock {
        super<FileChannelStore>.allocateObject(size).also { ensureAllocatedCapacity() }
    }

    override fun retireObject(position: Long) = mappingWriteLock.withLock {
        super<FileChannelStore>.retireObject(position)
    }

    override fun prepareRetiredObjects() = mappingWriteLock.withLock {
        super<FileChannelStore>.prepareRetiredObjects()
    }

    override fun publishRetiredObjects() = mappingWriteLock.withLock {
        super<FileChannelStore>.publishRetiredObjects()
    }

    override fun reset() = mappingWriteLock.withLock {
        super<FileChannelStore>.reset()
    }

    /**
     * Closes the store and releases its mapping arena.
     * @return True if the store was closed successfully, false otherwise.
     */
    override fun close(): Boolean = mappingWriteLock.withLock {
        if (channel == null) return true

        // Persist the exact allocated end while the mapping is live, then
        // unmap before truncating its physical growth reservation.
        var strictlyForced = true
        if (!deleteOnClose && channel?.isOpen == true) {
            try {
                finishAllocationReservations()
                wholeFileMapping?.force()
            } catch (_: Throwable) {
                // Still unmap and close, but do not truncate after a failed
                // strict durability barrier.
                strictlyForced = false
            }
        }

        val closingMapping = wholeFileMapping
        wholeFileMapping = null
        try {
            closingMapping?.close()
        } catch (_: Throwable) {
            strictlyForced = false
        }

        val truncated = deleteOnClose || channel?.isOpen != true || strictlyForced && try {
            channel?.truncate(getFileSize())
            true
        } catch (_: IOException) {
            false
        }
        var closeProtocolSucceeded = false
        val physicallyClosed = run {
            closeProtocolSucceeded = try {
                super.close()
            } catch (_: Throwable) {
                false
            }
            // FileChannelStore.close() reports durability failure without
            // closing the channel. The store must still become unusable.
            if (channel?.isOpen == true) {
                runCatching { channel?.close() }
            }
            channel?.isOpen != true
        }
        return strictlyForced && truncated && closeProtocolSucceeded && physicallyClosed
    }

    /**
     * Commits any changes to the store.
     * This is a no-op if deleteOnClose is true.
     */
    override fun commit() = mappingWriteLock.withLock {
        if (!deleteOnClose) {
            super.commit()
        }
    }

    override fun forceWrites() = mappingWriteLock.withLock {
        wholeFileMapping?.force()
        super.forceWrites()
    }

    /**
     * Ensures that the file channel is open.
     * @throws InitializationException if the channel is not open.
     */
    protected open fun ensureOpen() {
        if (!channel!!.isOpen) throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
    }

    private fun currentMapping(): WholeFileMapping {
        ensureOpen()
        return wholeFileMapping
            ?: throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
    }

    private fun ensureAllocatedCapacity() {
        wholeFileMapping?.ensureCapacity(getFileSize())
    }

    private fun closeAfterFailedOpen() {
        runCatching { wholeFileMapping?.close() }
        wholeFileMapping = null
        runCatching { channel?.close() }
    }
}
