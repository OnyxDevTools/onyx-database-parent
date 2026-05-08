package com.onyx.diskmap.store.impl

import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

internal data class MappedFileSegmentKey(val fileId: Int, val idx: Int)

internal class MappedFileSegment(
    val buffer: ByteBuffer,
    private val closeAction: () -> Unit
) {
    private var closed: Boolean = false

    @Synchronized
    fun close() {
        if (closed) {
            return
        }
        closed = true
        runCatching {
            (buffer as? MappedByteBuffer)?.force()
        }
        runCatching {
            closeAction.invoke()
        }
    }
}

internal class MappedFileSegmentCache(defaultMaxChunks: Int) {

    private val lock = Any()
    private val mappingLock = Any()
    private val entries = LinkedHashMap<MappedFileSegmentKey, MappedFileSegment>(16, 0.75f, true)
    private var configuredMaxChunks = maxOf(1, defaultMaxChunks)

    var maxChunks: Int
        get() = synchronized(lock) { configuredMaxChunks }
        set(value) {
            val evicted = synchronized(lock) {
                configuredMaxChunks = maxOf(1, value)
                evictOverflowLocked()
            }
            evicted.closeAll()
        }

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun getBuffer(
        key: MappedFileSegmentKey,
        mapper: () -> MappedFileSegment
    ): ByteBuffer = getOrMapSegment(key, mapper).buffer.duplicate()

    fun removeFile(fileId: Int) {
        val removed = synchronized(lock) {
            val removed = ArrayList<MappedFileSegment>()
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.fileId == fileId) {
                    iterator.remove()
                    removed.add(entry.value)
                }
            }
            removed
        }
        removed.closeAll()
    }

    fun evictLeastRecentlyUsed(): Boolean {
        val evicted = synchronized(lock) {
            evictLeastRecentlyUsedLocked()
        }
        evicted?.close()
        return evicted != null
    }

    private fun getOrMapSegment(
        key: MappedFileSegmentKey,
        mapper: () -> MappedFileSegment
    ): MappedFileSegment {
        synchronized(lock) {
            entries[key]?.let { return it }
        }

        synchronized(lock) {
            evictToRoomForNewSegmentLocked()
        }.closeAll()

        val mapped = synchronized(mappingLock) {
            synchronized(lock) {
                entries[key]?.let { return it }
            }
            mapper.invoke()
        }
        var segment = mapped
        val duplicate = ArrayList<MappedFileSegment>(1)
        val overflow = synchronized(lock) {
            entries[key]?.let {
                segment = it
                duplicate.add(mapped)
                return@synchronized emptyList()
            }

            entries[key] = mapped
            evictOverflowLocked()
        }

        duplicate.closeAll()
        overflow.closeAll()

        return segment
    }

    private fun evictToRoomForNewSegmentLocked(): List<MappedFileSegment> {
        val evicted = ArrayList<MappedFileSegment>()
        while (entries.size >= configuredMaxChunks) {
            val segment = evictLeastRecentlyUsedLocked() ?: return evicted
            evicted.add(segment)
        }
        return evicted
    }

    private fun evictOverflowLocked(): List<MappedFileSegment> {
        val evicted = ArrayList<MappedFileSegment>()
        while (entries.size > configuredMaxChunks) {
            val segment = evictLeastRecentlyUsedLocked() ?: return evicted
            evicted.add(segment)
        }
        return evicted
    }

    private fun evictLeastRecentlyUsedLocked(): MappedFileSegment? {
        val iterator = entries.iterator()
        if (iterator.hasNext()) {
            val entry = iterator.next()
            iterator.remove()
            return entry.value
        }
        return null
    }
}

private fun List<MappedFileSegment>.closeAll() {
    forEach {
        it.close()
    }
}

internal object MappedFileSegmentFactory {
    private val mapper: SegmentMapper = ForeignMemorySegmentMapper.create() ?: MappedByteBufferSegmentMapper

    fun map(
        channel: FileChannel,
        offset: Long,
        size: Int,
        onMemoryPressure: () -> Unit
    ): MappedFileSegment {
        var attempts = 1L
        while (true) {
            try {
                return mapper.map(channel, offset, size)
            } catch (throwable: Throwable) {
                val actual = throwable.unwrapInvocationTarget()
                if (!actual.isDirectMemoryFailure()) {
                    throw actual
                }
                println("Direct Memory is critically low.  Attempting to release memory")
                onMemoryPressure.invoke()
                System.gc()
                try {
                    Thread.sleep(min(250L * attempts, 5_000L))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                }
                attempts += 1
            }
        }
    }
}

private interface SegmentMapper {
    fun map(channel: FileChannel, offset: Long, size: Int): MappedFileSegment
}

private object MappedByteBufferSegmentMapper : SegmentMapper {
    override fun map(channel: FileChannel, offset: Long, size: Int): MappedFileSegment {
        val buffer = channel.map(FileChannel.MapMode.READ_WRITE, offset, size.toLong())
        return MappedFileSegment(buffer) {
            UnsafeByteBufferCleaner.unmap(buffer)
        }
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
            return MappedFileSegment(buffer) {
                runCatching {
                    segmentForceMethod?.invoke(segment)
                }
                arenaCloseMethod.invoke(arena)
            }
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
