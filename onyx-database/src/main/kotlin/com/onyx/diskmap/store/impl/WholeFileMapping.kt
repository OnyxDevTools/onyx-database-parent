package com.onyx.diskmap.store.impl

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max

/**
 * A JDK 22 memory mapping that grows with the file.
 *
 * The owner must serialize [ensureCapacity], [write], and [close] against
 * other reads and writes because growth may replace the mapping. Reads and
 * [writeWithinCapacity] calls may run concurrently while the mapping is stable.
 * [force] may run concurrently with all operations.
 *
 * WAL mappings retain replaced arenas until close, so growth never waits for
 * an active force. Other mappings use operating-system writeback and release
 * replaced arenas immediately.
 */
internal class WholeFileMapping @JvmOverloads constructor(
    private val channel: FileChannel,
    private val growthQuantum: Long,
    initialRequiredCapacity: Long,
    private val forceEnabled: Boolean = false,
    private val mappedForce: (MemorySegment) -> Unit = { it.force() }
) : AutoCloseable {

    private data class Mapping(
        val arena: Arena,
        val memory: MemorySegment,
        val capacity: Long
    )

    @Volatile
    private var mapping = createMapping(capacityFor(0L, initialRequiredCapacity))

    /* Growth and close are serialized by the owning store. */
    private val retiredMappings = ArrayList<Mapping>()
    private val forceLock = Any()
    private var metadataForcedMapping: Mapping? = null
    private var closed = false

    val capacity: Long
        get() = mapping.capacity

    fun ensureCapacity(requiredEndExclusive: Long) {
        require(requiredEndExclusive >= 0L) {
            "Required mapping end cannot be negative: $requiredEndExclusive"
        }
        if (requiredEndExclusive <= mapping.capacity) return

        val replacement = createMapping(
            capacityFor(mapping.capacity, requiredEndExclusive)
        )
        val previous = mapping

        try {
            if (forceEnabled) retiredMappings.add(previous) else previous.arena.close()
            mapping = replacement
        } catch (failure: Throwable) {
            runCatching { replacement.arena.close() }
            throw failure
        }
    }

    fun read(destination: ByteBuffer, filePosition: Long) {
        require(filePosition >= 0L) {
            "File position cannot be negative: $filePosition"
        }

        val byteCount = destination.remaining()
        if (byteCount == 0) return

        val endExclusive = Math.addExact(filePosition, byteCount.toLong())
        val current = mapping
        require(endExclusive <= current.capacity) {
            "Read ending at $endExclusive exceeds mapped capacity ${current.capacity}"
        }

        MemorySegment.copy(
            current.memory,
            filePosition,
            MemorySegment.ofBuffer(destination),
            0L,
            byteCount.toLong()
        )
        destination.position(destination.position() + byteCount)
    }

    fun write(source: ByteBuffer, filePosition: Long): Int {
        val byteCount = source.remaining()
        if (byteCount == 0) {
            require(filePosition >= 0L) {
                "File position cannot be negative: $filePosition"
            }
            return 0
        }

        val endExclusive = writeEndExclusive(filePosition, byteCount)
        ensureCapacity(endExclusive)
        return writeWithinCapacity(source, filePosition)
    }

    /**
     * Writes without changing the mapping. The owner must keep the mapping
     * stable and verify that the write fits before calling this method.
     */
    fun writeWithinCapacity(source: ByteBuffer, filePosition: Long): Int {
        require(filePosition >= 0L) {
            "File position cannot be negative: $filePosition"
        }

        val byteCount = source.remaining()
        if (byteCount == 0) return 0

        val endExclusive = writeEndExclusive(filePosition, byteCount)
        val current = mapping
        require(endExclusive <= current.capacity) {
            "Write ending at $endExclusive exceeds mapped capacity ${current.capacity}"
        }

        MemorySegment.copy(
            MemorySegment.ofBuffer(source),
            0L,
            current.memory,
            filePosition,
            byteCount.toLong()
        )
        source.position(source.position() + byteCount)
        return byteCount
    }

    fun force() {
        if (!forceEnabled) return
        synchronized(forceLock) {
            if (!closed) forceCurrentMapping()
        }
    }

    override fun close(): Unit = synchronized(forceLock) {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        try {
            if (forceEnabled) forceCurrentMapping()
        } catch (current: Throwable) {
            failure = current
        }

        for (retired in retiredMappings + mapping) {
            try {
                retired.arena.close()
            } catch (current: Throwable) {
                if (failure == null) failure = current else failure.addSuppressed(current)
            }
        }
        retiredMappings.clear()

        failure?.let { throw it }
    }

    private fun forceCurrentMapping() {
        val current = mapping
        mappedForce(current.memory)
        if (metadataForcedMapping !== current) {
            channel.force(true)
            metadataForcedMapping = current
        }
    }

    private fun writeEndExclusive(filePosition: Long, byteCount: Int): Long {
        require(filePosition >= 0L) {
            "File position cannot be negative: $filePosition"
        }
        return Math.addExact(filePosition, byteCount.toLong())
    }

    private fun createMapping(capacity: Long): Mapping {
        val arena = Arena.ofShared()
        try {
            val memory = channel.map(
                FileChannel.MapMode.READ_WRITE,
                0L,
                capacity,
                arena
            )
            return Mapping(
                arena = arena,
                memory = memory,
                capacity = capacity
            )
        } catch (failure: Throwable) {
            runCatching { arena.close() }
            throw failure
        }
    }

    private fun capacityFor(currentCapacity: Long, requiredCapacity: Long): Long {
        require(growthQuantum > 0L) {
            "Growth quantum must be positive: $growthQuantum"
        }
        require(requiredCapacity >= 0L) {
            "Required mapping capacity cannot be negative: $requiredCapacity"
        }

        var result = max(currentCapacity, growthQuantum)
        while (result < requiredCapacity) {
            result = if (result > Long.MAX_VALUE / 2L) {
                requiredCapacity
            } else {
                max(result * 2L, requiredCapacity)
            }
        }
        return roundUp(result, growthQuantum)
    }

    private fun roundUp(value: Long, alignment: Long): Long {
        val remainder = value % alignment
        return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
    }

    companion object {
        private const val SMALL_GROWTH_QUANTUM = 128L * 1024
        private const val DEFAULT_GROWTH_QUANTUM = 4L * 1024 * 1024

        fun defaultGrowthQuantum(preferSmallMapping: Boolean): Long =
            if (preferSmallMapping) SMALL_GROWTH_QUANTUM else DEFAULT_GROWTH_QUANTUM
    }
}
