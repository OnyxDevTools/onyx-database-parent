package com.onyx.diskmap.store.impl

import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.math.min

internal data class MappedFileSegmentKey(val fileId: Int, val idx: Int)
internal data class MappedBufferUse<T>(val value: T)

internal class MappedFileSegment(
    val buffer: ByteBuffer,
    private val forceAction: () -> Unit = {
        (buffer as? MappedByteBuffer)?.force()
    },
    private val closeAction: () -> Unit
) {
    private val lifecycleLock = ReentrantReadWriteLock()
    private val dirty = AtomicBoolean()
    private val externallyWritable = AtomicBoolean()
    private val dirtyListener = AtomicReference<(() -> Unit)?>(null)
    private val closeListener = AtomicReference<(() -> Unit)?>(null)
    private val closeListenerAssigned = AtomicBoolean()
    @Volatile
    private var closed = false

    val sizeBytes: Long = buffer.capacity().toLong()

    val requiresForce: Boolean
        get() = dirty.get() || externallyWritable.get()

    fun force() {
        lifecycleLock.writeLock().withLock {
            if (closed || !requiresForce) return
            forceAction.invoke()
            dirty.set(false)
        }
    }

    /** Keep eviction and unmapping outside the supplied buffer operation. */
    fun <T> useBuffer(markDirty: Boolean, action: (ByteBuffer) -> T): MappedBufferUse<T>? {
        val lock = if (markDirty) lifecycleLock.writeLock() else lifecycleLock.readLock()
        lock.withLock {
            if (closed) return null
            // A failed operation may still have changed part of the mapping.
            if (markDirty) recordDirty()
            return MappedBufferUse(action(buffer.duplicate()))
        }
    }

    /** Compatibility path for callers that retain a writable duplicate. */
    fun writableBuffer(): ByteBuffer? = lifecycleLock.readLock().withLock {
        if (closed) return null
        if (!externallyWritable.get()) externallyWritable.compareAndSet(false, true)
        recordDirty()
        buffer.duplicate()
    }

    fun close() {
        lifecycleLock.writeLock().withLock {
            // General cleanup remains best effort. Capacity eviction uses the
            // strict variant so failed writeback prevents a replacement mmap.
            runCatching { forceAndCloseLocked() }
        }
    }

    internal fun forceAndClose() {
        lifecycleLock.writeLock().withLock {
            forceAndCloseLocked()
        }
    }

    internal fun setDirtyListener(listener: (() -> Unit)?) {
        dirtyListener.set(listener)
    }

    internal fun setCloseListener(listener: () -> Unit) {
        check(closeListenerAssigned.compareAndSet(false, true)) {
            "Mapped segment already has a close listener"
        }
        closeListener.set(listener)
        // Normally listeners are installed before close is exposed. This closes
        // the small race for test/custom segments that are closed independently.
        if (closed && closeListener.compareAndSet(listener, null)) {
            listener.invoke()
        }
    }

    private fun recordDirty() {
        if (!dirty.get() && dirty.compareAndSet(false, true)) {
            dirtyListener.get()?.invoke()
        }
    }

    private fun closeWithoutForceLocked() {
        if (closed) return
        closed = true
        dirtyListener.set(null)
        var failure: Throwable? = null
        try {
            closeAction.invoke()
        } catch (current: Throwable) {
            failure = current
        }
        try {
            closeListener.getAndSet(null)?.invoke()
        } catch (current: Throwable) {
            if (failure == null) failure = current else failure.addSuppressed(current)
        }
        failure?.let { throw it }
    }

    private fun forceAndCloseLocked() {
        if (closed) return
        var failure: Throwable? = null
        if (requiresForce) {
            try {
                forceAction.invoke()
                dirty.set(false)
            } catch (current: Throwable) {
                failure = current
            }
        }
        try {
            closeWithoutForceLocked()
        } catch (current: Throwable) {
            if (failure == null) failure = current else failure.addSuppressed(current)
        }
        failure?.let { throw it }
    }
}

internal class MappedFileSegmentCache(defaultMaxChunks: Int) {

    private class MappingGate {
        val monitor = Any()
        var users = 0
    }

    private val managementLock = ReentrantLock()
    private val capacityChanged = managementLock.newCondition()
    private val entries = LinkedHashMap<MappedFileSegmentKey, MappedFileSegment>(16, 0.75f, true)
    private val mappingGates = HashMap<MappedFileSegmentKey, MappingGate>()
    private val dirtySegmentsByFile =
        HashMap<Int, LinkedHashMap<MappedFileSegmentKey, MappedFileSegment>>()
    private val pendingSegmentsByFile = HashMap<Int, LinkedHashSet<MappedFileSegment>>()
    private val retiredFileIds = HashSet<Int>()
    private var pendingCloseBytes = 0L
    private var pendingCloseCount = 0
    private var mappingSlotsInUse = 0
    private var configuredMaxChunks = maxOf(1, defaultMaxChunks)

    var maxChunks: Int
        get() = managementLock.withLock { configuredMaxChunks }
        set(value) {
            managementLock.withLock {
                configuredMaxChunks = maxOf(1, value)
                capacityChanged.signalAll()
            }
            trimOverflow()
        }

    val size: Int
        get() = managementLock.withLock { entries.size }

    val pendingSizeBytes: Long
        get() = managementLock.withLock { pendingCloseBytes }

    val dirtySize: Int
        get() = managementLock.withLock { dirtySegmentsByFile.values.sumOf { it.size } }

    internal fun isFileRetired(fileId: Int): Boolean =
        managementLock.withLock { retiredFileIds.contains(fileId) }

    fun getBuffer(
        key: MappedFileSegmentKey,
        mapper: () -> MappedFileSegment
    ): ByteBuffer {
        while (true) {
            getOrMapSegment(key, mapper).writableBuffer()?.let { return it }
        }
    }

    fun <T> withBuffer(
        key: MappedFileSegmentKey,
        mapper: () -> MappedFileSegment,
        markDirty: Boolean = true,
        action: (ByteBuffer) -> T
    ): T {
        while (true) {
            getOrMapSegment(key, mapper).useBuffer(markDirty, action)?.let { return it.value }
        }
    }

    fun removeFile(fileId: Int) {
        detachFile(fileId).closeAll()
    }

    /** Retire a file id through physical close and reject a map already in flight for it. */
    fun retireFile(fileId: Int): List<MappedFileSegment> {
        return managementLock.withLock {
            retiredFileIds.add(fileId)
            capacityChanged.signalAll()
            while (mappingGates.keys.any { it.fileId == fileId }) {
                capacityChanged.awaitUninterruptibly()
            }
            detachFileLocked(fileId)
        }
    }

    /** Release a retired id after its channel has completed physical close. */
    fun releaseRetiredFile(fileId: Int) {
        managementLock.withLock {
            retiredFileIds.remove(fileId)
            capacityChanged.signalAll()
        }
    }

    /** Detach current mappings while permitting the same file id to map again. */
    fun detachFile(fileId: Int): List<MappedFileSegment> = managementLock.withLock {
        detachFileLocked(fileId)
    }

    private fun detachFileLocked(fileId: Int): List<MappedFileSegment> {
        val removed = ArrayList<MappedFileSegment>()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.fileId == fileId) {
                iterator.remove()
                removeDirtyKeyLocked(entry.key)
                markPendingCloseLocked(fileId, entry.value)
                removed.add(entry.value)
            }
        }
        return removed
    }

    fun forceFile(fileId: Int) {
        val segments = managementLock.withLock {
            buildList {
                dirtySegmentsByFile[fileId]?.values?.forEach { add(it) }
                pendingSegmentsByFile[fileId]?.forEach { segment ->
                    if (segment.requiresForce) add(segment)
                }
            }
        }
        segments.forEach { it.force() }
        managementLock.withLock {
            dirtySegmentsByFile[fileId]?.entries?.removeIf { (_, segment) ->
                !segment.requiresForce
            }
            if (dirtySegmentsByFile[fileId]?.isEmpty() == true) {
                dirtySegmentsByFile.remove(fileId)
            }
        }
    }

    fun evictLeastRecentlyUsed(): Boolean {
        val evicted = managementLock.withLock { detachLeastRecentlyUsedLocked() }
        evicted?.forceAndClose()
        return evicted != null
    }

    private fun getOrMapSegment(
        key: MappedFileSegmentKey,
        mapper: () -> MappedFileSegment
    ): MappedFileSegment {
        managementLock.withLock {
            if (retiredFileIds.contains(key.fileId)) throw ClosedChannelException()
            entries[key]?.let { return it }
        }

        val mappingGate = managementLock.withLock {
            if (retiredFileIds.contains(key.fileId)) throw ClosedChannelException()
            entries[key]?.let { return it }
            mappingGates.getOrPut(key) { MappingGate() }.also { it.users++ }
        }

        return try {
            synchronized(mappingGate.monitor) {
                val existing = managementLock.withLock {
                    if (retiredFileIds.contains(key.fileId)) throw ClosedChannelException()
                    entries[key]
                }
                if (existing != null) return@synchronized existing

                var mappingSlotHeld = false
                var mapped: MappedFileSegment? = null
                try {
                    acquireMappingSlot(key.fileId)
                    mappingSlotHeld = true
                    val created = mapper.invoke()
                    mapped = created
                    managementLock.withLock {
                        if (retiredFileIds.contains(key.fileId)) throw ClosedChannelException()
                        mappingSlotsInUse--
                        mappingSlotHeld = false
                        created.setDirtyListener { registerDirty(key, created) }
                        entries[key] = created
                        capacityChanged.signalAll()
                    }
                    created
                } catch (failure: Throwable) {
                    if (mappingSlotHeld) {
                        managementLock.withLock {
                            mappingSlotsInUse--
                            capacityChanged.signalAll()
                        }
                    }
                    mapped?.close()
                    throw failure
                }
            }
        } finally {
            managementLock.withLock {
                mappingGate.users--
                if (mappingGate.users == 0 && mappingGates[key] === mappingGate) {
                    mappingGates.remove(key)
                    capacityChanged.signalAll()
                }
            }
        }
    }

    /** Pending detached mappings still occupy a live mapping slot. */
    private fun acquireMappingSlot(fileId: Int) {
        while (true) {
            var evicted: MappedFileSegment? = null
            managementLock.withLock {
                if (retiredFileIds.contains(fileId)) throw ClosedChannelException()
                if (retainedCountLocked() < configuredMaxChunks.toLong()) {
                    mappingSlotsInUse++
                    return
                }
                evicted = detachLeastRecentlyUsedLocked()
                if (evicted == null) {
                    capacityChanged.awaitUninterruptibly()
                }
            }
            // This can wait for an active lease; never hold the management lock.
            evicted?.forceAndClose()
        }
    }

    private fun trimOverflow() {
        while (true) {
            val evicted = managementLock.withLock {
                if (retainedCountLocked() <= configuredMaxChunks.toLong()) {
                    return
                }
                detachLeastRecentlyUsedLocked()
            } ?: return
            evicted.forceAndClose()
        }
    }

    private fun retainedCountLocked(): Long =
        entries.size.toLong() + pendingCloseCount + mappingSlotsInUse

    private fun detachLeastRecentlyUsedLocked(): MappedFileSegment? {
        val iterator = entries.iterator()
        if (!iterator.hasNext()) return null
        val entry = iterator.next()
        iterator.remove()
        removeDirtyKeyLocked(entry.key)
        markPendingCloseLocked(entry.key.fileId, entry.value)
        return entry.value
    }

    private fun registerDirty(key: MappedFileSegmentKey, segment: MappedFileSegment) =
        managementLock.withLock {
            if (entries[key] === segment) {
                dirtySegmentsByFile
                    .getOrPut(key.fileId) { LinkedHashMap() }[key] = segment
            }
        }

    private fun markPendingCloseLocked(fileId: Int, segment: MappedFileSegment) {
        segment.setDirtyListener(null)
        val pending = pendingSegmentsByFile.getOrPut(fileId) { LinkedHashSet() }
        check(pending.add(segment)) { "Mapped segment is already pending close" }
        pendingCloseBytes += segment.sizeBytes
        pendingCloseCount++
        segment.setCloseListener { completePendingClose(fileId, segment) }
    }

    private fun completePendingClose(fileId: Int, segment: MappedFileSegment) =
        managementLock.withLock {
            val pending = pendingSegmentsByFile[fileId] ?: return@withLock
            if (!pending.remove(segment)) return@withLock
            if (pending.isEmpty()) pendingSegmentsByFile.remove(fileId)
            pendingCloseBytes -= segment.sizeBytes
            pendingCloseCount--
            capacityChanged.signalAll()
        }

    private fun removeDirtyKeyLocked(key: MappedFileSegmentKey) {
        dirtySegmentsByFile[key.fileId]?.let { dirtySegments ->
            dirtySegments.remove(key)
            if (dirtySegments.isEmpty()) dirtySegmentsByFile.remove(key.fileId)
        }
    }
}

private fun List<MappedFileSegment>.closeAll() {
    forEach {
        it.close()
    }
}

internal fun List<MappedFileSegment>.forceAndCloseAll() {
    var failure: Throwable? = null

    forEach { segment ->
        try {
            segment.forceAndClose()
        } catch (current: Throwable) {
            if (failure == null) {
                failure = current
            } else {
                failure!!.addSuppressed(current)
            }
        }
    }

    failure?.let { throw it }
}

internal object MappedFileSegmentFactory {
    private const val MAX_MAPPING_ATTEMPTS = 3
    private const val BASE_RETRY_DELAY_MILLIS = 25L
    private const val MAX_RETRY_DELAY_MILLIS = 250L
    private val mapper: SegmentMapper = ForeignMemorySegmentMapper.create() ?: MappedByteBufferSegmentMapper

    fun map(
        channel: FileChannel,
        offset: Long,
        size: Int,
        onMemoryPressure: () -> Unit
    ): MappedFileSegment = mapWithRetry(
        mappingAction = { mapper.map(channel, offset, size) },
        onMemoryPressure = onMemoryPressure,
        waitBeforeRetry = { delayMillis ->
            System.gc()
            try {
                Thread.sleep(delayMillis)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            }
        }
    )

    internal fun mapWithRetry(
        mappingAction: () -> MappedFileSegment,
        onMemoryPressure: () -> Unit,
        waitBeforeRetry: (Long) -> Unit
    ): MappedFileSegment {
        var attempt = 1
        while (attempt <= MAX_MAPPING_ATTEMPTS) {
            try {
                return mappingAction.invoke()
            } catch (throwable: Throwable) {
                val actual = throwable.unwrapInvocationTarget()
                if (!actual.isDirectMemoryFailure() || attempt == MAX_MAPPING_ATTEMPTS) {
                    throw actual
                }
                onMemoryPressure.invoke()
                waitBeforeRetry.invoke(
                    min(BASE_RETRY_DELAY_MILLIS * attempt, MAX_RETRY_DELAY_MILLIS)
                )
                attempt += 1
            }
        }
        error("Unreachable mapping retry state")
    }
}

private interface SegmentMapper {
    fun map(channel: FileChannel, offset: Long, size: Int): MappedFileSegment
}

private object MappedByteBufferSegmentMapper : SegmentMapper {
    override fun map(channel: FileChannel, offset: Long, size: Int): MappedFileSegment {
        val buffer = channel.map(FileChannel.MapMode.READ_WRITE, offset, size.toLong())
        return MappedFileSegment(
            buffer = buffer,
            closeAction = {
                UnsafeByteBufferCleaner.unmap(buffer)
            }
        )
    }
}

private class ForeignMemorySegmentMapper(
    private val ofSharedMethod: Method,
    private val arenaCloseMethod: Method,
    private val fileChannelMapMethod: Method,
    private val segmentAsByteBufferMethod: Method,
    private val segmentForceMethod: Method?
) : SegmentMapper {

    override fun map(channel: FileChannel, offset: Long, size: Int): MappedFileSegment {
        val arena = ofSharedMethod.invoke(null)
        try {
            val segment = fileChannelMapMethod.invoke(
                channel,
                FileChannel.MapMode.READ_WRITE,
                offset,
                size.toLong(),
                arena
            )
            val buffer = segmentAsByteBufferMethod.invoke(segment) as ByteBuffer
            return MappedFileSegment(
                buffer = buffer,
                forceAction = {
                    segmentForceMethod?.invoke(segment)
                },
                closeAction = {
                    arenaCloseMethod.invoke(arena)
                }
            )
        } catch (throwable: Throwable) {
            runCatching {
                arenaCloseMethod.invoke(arena)
            }
            throw throwable.unwrapInvocationTarget()
        }
    }

    companion object {
        fun create(): ForeignMemorySegmentMapper? {
            if (javaFeatureVersion() < 22) {
                return null
            }
            return runCatching {
                val arenaClass = Class.forName("java.lang.foreign.Arena")
                val memorySegmentClass = Class.forName("java.lang.foreign.MemorySegment")
                ForeignMemorySegmentMapper(
                    ofSharedMethod = arenaClass.getMethod("ofShared"),
                    arenaCloseMethod = arenaClass.getMethod("close"),
                    fileChannelMapMethod = FileChannel::class.java.getMethod(
                        "map",
                        FileChannel.MapMode::class.java,
                        java.lang.Long.TYPE,
                        java.lang.Long.TYPE,
                        arenaClass
                    ),
                    segmentAsByteBufferMethod = memorySegmentClass.getMethod("asByteBuffer"),
                    segmentForceMethod = memorySegmentClass.methods.firstOrNull {
                        it.name == "force" && it.parameterCount == 0
                    }
                )
            }.getOrNull()
        }
    }
}

private object UnsafeByteBufferCleaner {
    private val invokeCleaner = runCatching {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        UnsafeInvokeCleaner(
            unsafe = unsafeField.get(null),
            invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer::class.java)
        )
    }.getOrNull()

    fun unmap(buffer: ByteBuffer) {
        if (!buffer.isDirect) {
            return
        }
        invokeCleaner?.invoke(buffer)
    }

    private class UnsafeInvokeCleaner(
        private val unsafe: Any,
        private val invokeCleaner: Method
    ) {
        fun invoke(buffer: ByteBuffer) {
            runCatching {
                invokeCleaner.invoke(unsafe, buffer)
            }
        }
    }
}

private fun Throwable.unwrapInvocationTarget(): Throwable =
    if (this is InvocationTargetException && targetException != null) targetException else this

private fun Throwable.isDirectMemoryFailure(): Boolean =
    this is OutOfMemoryError || this is IOException && cause is OutOfMemoryError

private fun javaFeatureVersion(): Int {
    val specificationVersion = System.getProperty("java.specification.version") ?: return 8
    val normalized = if (specificationVersion.startsWith("1.")) {
        specificationVersion.substringAfter("1.")
    } else {
        specificationVersion.substringBefore(".")
    }
    return normalized.toIntOrNull() ?: 8
}
