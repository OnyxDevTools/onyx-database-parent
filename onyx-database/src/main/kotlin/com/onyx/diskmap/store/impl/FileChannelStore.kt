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
import java.util.ArrayDeque
import java.util.TreeMap

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
    /**
     * Logical end of allocated data. The channel can be physically larger when a
     * [MemoryMappedStore] has mapped a complete slice; that physical reservation
     * must never be used as the reopen allocation cursor.
     */
    private var logicalSizeCounter: AtomicCounter = DefaultAtomicCounter(0)
    private val allocationLock = Any()
    private var reservationEnd = 0L
    private var allocationRange: AllocationRange? = null
    private val pendingRetiredObjects = ArrayList<ObjectSlot>()
    private val preparedRetiredObjects = ArrayList<ObjectSlot>()
    private val reusableObjectSlots = TreeMap<Int, ArrayDeque<Long>>()
    private val retiredObjectPositions = HashSet<Long>()
    private val reusedObjectCapacities = HashMap<Long, Int>()
    private var reusableObjectSlotCount = 0

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
    override fun getFileSize(): Long = logicalSizeCounter.get()

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
            // This is only a bootstrap value that permits determineSize() to
            // read the legacy eight-byte size header. A mapped file can be much
            // larger than the logical store.
            val physicalSize = this.channel!!.size()
            synchronized(allocationLock) {
                logicalSizeCounter.set(physicalSize)
                reservationEnd = physicalSize
                allocationRange = null
                clearObjectReclamationLocked()
            }

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
                val persistedReservationEnd = it.long
                val physicalEnd = channel?.size() ?: persistedReservationEnd
                val recoveredEnd = recoverLogicalEnd(persistedReservationEnd, physicalEnd)
                synchronized(allocationLock) {
                    logicalSizeCounter.set(recoveredEnd)
                    // Keep the actual persisted value so the next clean
                    // commit/close rewrites a reconciled regular-file tail.
                    reservationEnd = persistedReservationEnd
                    allocationRange = null
                }
            }
        }
    }

    /**
     * Reconcile the legacy persisted allocation end with bytes that reached a
     * regular file. Memory-mapped stores override the effective behavior by
     * persisting their exact allocated end on every clean commit/close; after a
     * hard crash their larger persisted reservation remains the safe fallback.
     */
    protected open fun recoverLogicalEnd(persistedReservationEnd: Long, physicalEnd: Long): Long =
        minOf(persistedReservationEnd, physicalEnd).coerceAtLeast(STORE_HEADER_SIZE.toLong())

    /**
     * Close the data file
     *
     * @return Whether the file was closed successfully.
     */
    override fun close(): Boolean = try {
        synchronized(allocationLock) {
            clearObjectReclamationLocked()
        }
        if (!deleteOnClose) {
            finishAllocationReservations()
            if (this !is MemoryMappedStore && this !is InMemoryStore) {
                channel?.truncate(logicalSizeCounter.get())
            }
            forceWrites()
        }
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
        finishAllocationReservations()
        forceWrites()
    }

    /** Flush data written by this store. Memory-mapped stores also force their mapped slices. */
    protected open fun forceWrites() {
        if (this.channel?.isOpen == true) {
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
        var current = position
        while (buffer.hasRemaining()) {
            val count = channel!!.write(buffer, current)
            check(count > 0) { "Unable to write store buffer at $current" }
            current += count
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
        var current = position
        while (buffer.hasRemaining()) {
            val count = channel!!.read(buffer, current)
            check(count >= 0) { "Unexpected end of store at $current" }
            if (count == 0) break
            current += count
        }
    }

    /**
     * Validate we are not going to read beyond the allocated file storage.  This would be bad
     * @param position Position to validate
     * @return whether the value you seek is in a valid position
     */
    protected open fun validateFileSize(position: Long): Boolean = position < logicalSizeCounter.get()

    /**
     * Allocates a spot in the file
     *
     * @param size Allocate space within the store.
     * @return position of started allocated bytes
     */
    override fun allocate(size: Int): Long = synchronized(allocationLock) {
        if (this !is InMemoryStore && !channel!!.isOpen)
            throw InitializationException(InitializationException.DATABASE_SHUTDOWN)
        allocateLocked(size, 1)
    }

    override fun allocateAligned(size: Int, alignment: Int): Long = synchronized(allocationLock) {
        require(alignment > 0 && alignment and (alignment - 1) == 0) {
            "Alignment must be a positive power of two: $alignment"
        }
        allocateLocked(size, alignment)
    }

    override fun allocateSlot(size: Int): Long = synchronized(allocationLock) {
        require(size > 0) { "Slot size must be positive: $size" }
        allocateBatchedLocked(size, SLOT_RESERVATION_SIZE)
    }

    override fun allocateObject(size: Int): Long = synchronized(allocationLock) {
        require(size >= 0) { "Object allocation size must not be negative: $size" }
        takeReusableObjectSlotLocked(size) ?: allocateBatchedLocked(size, OBJECT_RESERVATION_SIZE)
    }

    override fun retireObject(position: Long) = synchronized(allocationLock) {
        require(position >= STORE_HEADER_SIZE) { "Object position is outside the store: $position" }
        if (position in retiredObjectPositions) return@synchronized

        val reusedCapacity = reusedObjectCapacities.remove(position)
        if (reusedCapacity == null &&
            retiredObjectPositions.size + reusedObjectCapacities.size >= MAX_REUSABLE_OBJECT_SLOTS
        ) {
            // The frame is unreachable but intentionally leaked on disk to keep
            // all reclamation metadata globally bounded.
            return@synchronized
        }
        val capacity = reusedCapacity ?: readObjectSlotCapacityLocked(position)
        retiredObjectPositions.add(position)
        try {
            pendingRetiredObjects.add(ObjectSlot(position, capacity))
        } catch (throwable: Throwable) {
            retiredObjectPositions.remove(position)
            throw throwable
        }
        Unit
    }

    /** Freeze the current retirement generation before either store is forced. */
    override fun prepareRetiredObjects() = synchronized(allocationLock) {
        if (pendingRetiredObjects.isNotEmpty()) {
            preparedRetiredObjects.addAll(pendingRetiredObjects)
            pendingRetiredObjects.clear()
        }
    }

    /** Publish only the generation prepared before both durability barriers. */
    override fun publishRetiredObjects() = synchronized(allocationLock) {
        while (preparedRetiredObjects.isNotEmpty()) {
            // Consume first so a partial failure can never enqueue this slot a
            // second time when publish is retried.
            val slot = preparedRetiredObjects.removeAt(preparedRetiredObjects.lastIndex)
            if (reusableObjectSlotCount < MAX_REUSABLE_OBJECT_SLOTS) {
                try {
                    reusableObjectSlots.getOrPut(slot.capacity) { ArrayDeque() }.addLast(slot.position)
                    reusableObjectSlotCount++
                } catch (throwable: Throwable) {
                    retiredObjectPositions.remove(slot.position)
                    if (reusableObjectSlots[slot.capacity]?.isEmpty() == true) {
                        reusableObjectSlots.remove(slot.capacity)
                    }
                    throw throwable
                }
            } else {
                // Forget excess unreachable slots. Leaking their disk bytes is
                // safer than retaining unbounded reclamation metadata on heap.
                retiredObjectPositions.remove(slot.position)
            }
        }
    }

    private fun readObjectSlotCapacityLocked(position: Long): Int {
        val logicalEnd = logicalSizeCounter.get()
        require(position <= logicalEnd - Integer.BYTES) {
            "Object position is outside the logical store: $position"
        }
        val payloadSize = BufferPool.withIntBuffer {
            it.clear()
            read(it, position)
            it.flip()
            it.int
        }
        require(payloadSize >= 0) { "Invalid object payload size $payloadSize at $position" }
        val capacity = Math.addExact(payloadSize, Integer.BYTES)
        require(capacity.toLong() <= logicalEnd - position) {
            "Object frame at $position exceeds the logical store"
        }
        return capacity
    }

    private fun takeReusableObjectSlotLocked(size: Int): Long? {
        val entry = reusableObjectSlots.ceilingEntry(size) ?: return null
        val position = entry.value.removeFirst()
        if (entry.value.isEmpty()) reusableObjectSlots.remove(entry.key)
        reusableObjectSlotCount--
        retiredObjectPositions.remove(position)
        reusedObjectCapacities[position] = entry.key
        return position
    }

    /**
     * Slots and objects deliberately share one active range. Separate ranges
     * create an unrecoverable hole whenever their writes are interleaved.
     */
    private fun allocateBatchedLocked(size: Int, batchSize: Int): Long {
        var range = allocationRange
        if (range == null || size.toLong() > range.end - range.next) {
            // The unused tail was never handed to a caller, so a new reservation
            // can safely begin at the exact logical cursor.
            allocationRange = null
            val start = logicalSizeCounter.get()
            val reservedBytes = maxOf(batchSize.toLong(), size.toLong())
            val end = Math.addExact(start, reservedBytes)
            persistReservationEndLocked(end)
            range = AllocationRange(start, end)
            allocationRange = range
        }
        val position = range.next
        range.next = Math.addExact(position, size.toLong())
        logicalSizeCounter.set(range.next)
        return position
    }

    private fun allocateLocked(size: Int, alignment: Int): Long {
        require(size >= 0) { "Allocation size must not be negative: $size" }
        // Direct/aligned allocations close the shared range so its unused tail
        // is immediately available instead of becoming a hole before the page.
        allocationRange = null
        val current = logicalSizeCounter.get()
        val position = if (alignment == 1) current else (current + alignment - 1) and -alignment.toLong()
        val newFileSize = Math.addExact(position, size.toLong())
        logicalSizeCounter.set(newFileSize)
        persistReservationEndLocked(newFileSize)
        return position
    }

    /** Persist the reservation before returning any address within it. */
    private fun persistReservationEndLocked(end: Long) {
        reservationEnd = end
        // In-memory stores have no reopen state to persist.
        if (this !is InMemoryStore) {
            withLongBuffer {
                it.clear()
                it.putLong(end)
                it.flip()
                this.write(it, 0)
            }
        }
    }

    /**
     * End the current batch at the exact logical allocation cursor. A successful
     * clean commit preserves every address returned by allocate, even when its
     * caller has not written the bytes yet.
     */
    protected fun finishAllocationReservations() = synchronized(allocationLock) {
        val committedEnd = maxOf(STORE_HEADER_SIZE.toLong(), logicalSizeCounter.get())
        allocationRange = null
        ensurePhysicalEndLocked(committedEnd)
        if (reservationEnd != committedEnd) {
            persistReservationEndLocked(committedEnd)
        }
    }

    /** Ensure physical EOF makes the exact committed cursor recoverable by a regular file. */
    private fun ensurePhysicalEndLocked(logicalEnd: Long) {
        val fileChannel = channel ?: return
        if (this is InMemoryStore || fileChannel.size() >= logicalEnd) return
        val extension = physicalExtensionByte.get()
        extension.clear()
        extension.put(0)
        extension.flip()
        while (extension.hasRemaining()) {
            val written = fileChannel.write(extension, logicalEnd - 1)
            check(written > 0) { "Unable to extend store to $logicalEnd" }
        }
    }

    private fun clearObjectReclamationLocked() {
        pendingRetiredObjects.clear()
        preparedRetiredObjects.clear()
        reusableObjectSlots.clear()
        retiredObjectPositions.clear()
        reusedObjectCapacities.clear()
        reusableObjectSlotCount = 0
    }

    /**
     * Delete File
     */
    override fun delete() {
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
        synchronized(allocationLock) {
            allocationRange = null
            reservationEnd = 0L
            logicalSizeCounter.set(0)
            clearObjectReclamationLocked()
        }
        allocate(8)
    }

    private val localBuffer: ByteBuffer
        get() = threadLocalBuffer.get()

    companion object {
        private const val STORE_HEADER_SIZE = java.lang.Long.BYTES
        private const val SLOT_RESERVATION_SIZE = 64 * 1024
        private const val OBJECT_RESERVATION_SIZE = 1024 * 1024
        private const val MAX_REUSABLE_OBJECT_SLOTS = 65_536
        const val SMALL_FILE_SLICE_SIZE = 1024 * 128 // 128K
        var LARGE_FILE_SLICE_SIZE = 1024 * 1024 * 4 // 4MB

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

        private val physicalExtensionByte: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
            ByteBuffer.allocate(1)
        }
    }

    private data class AllocationRange(var next: Long, val end: Long)
    private data class ObjectSlot(val position: Long, val capacity: Int)

}
