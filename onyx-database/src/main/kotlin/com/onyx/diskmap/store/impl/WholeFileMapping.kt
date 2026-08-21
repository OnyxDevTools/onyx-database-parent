package com.onyx.diskmap.store.impl

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max

/**
 * A single JDK 22 memory mapping that grows with the file.
 *
 * The owner must serialize [ensureCapacity], [write], [force], and [close]
 * against all other operations. Reads may run concurrently while the mapping
 * is stable.
 */
internal class WholeFileMapping(
    private val channel: FileChannel,
    private val growthQuantum: Long,
    initialRequiredCapacity: Long
) : AutoCloseable {

    private data class Mapping(
        val arena: Arena,
        val memory: MemorySegment,
        val capacity: Long
    )

    private var mapping = createMapping(capacityFor(0L, initialRequiredCapacity))

    /* Writes are serialized by the owning store. */
    private var dirtyStart = Long.MAX_VALUE
    private var dirtyEndExclusive = 0L

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
            // Growth is rare. Establish a clear durability boundary before
            // invalidating every view backed by the previous arena.
            force()
            previous.arena.close()
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
        require(filePosition >= 0L) {
            "File position cannot be negative: $filePosition"
        }

        val byteCount = source.remaining()
        if (byteCount == 0) return 0

        val endExclusive = Math.addExact(filePosition, byteCount.toLong())
        ensureCapacity(endExclusive)

        // Record the complete range before copying so a partial copy is still
        // included in the next durability barrier.
        markDirty(filePosition, endExclusive)
        MemorySegment.copy(
            MemorySegment.ofBuffer(source),
            0L,
            mapping.memory,
            filePosition,
            byteCount.toLong()
        )
        source.position(source.position() + byteCount)
        return byteCount
    }

    fun force() {
        if (dirtyStart < dirtyEndExclusive) {
            mapping.memory
                .asSlice(dirtyStart, dirtyEndExclusive - dirtyStart)
                .force()
        }

        dirtyStart = Long.MAX_VALUE
        dirtyEndExclusive = 0L
    }

    override fun close() {
        var failure: Throwable? = null
        try {
            force()
        } catch (current: Throwable) {
            failure = current
        }

        try {
            mapping.arena.close()
        } catch (current: Throwable) {
            if (failure == null) failure = current else failure.addSuppressed(current)
        }

        failure?.let { throw it }
    }

    private fun markDirty(start: Long, endExclusive: Long) {
        if (start < dirtyStart) dirtyStart = start
        if (endExclusive > dirtyEndExclusive) dirtyEndExclusive = endExclusive
    }

    private fun createMapping(capacity: Long): Mapping {
        val arena = Arena.ofShared()
        try {
            return Mapping(
                arena = arena,
                memory = channel.map(
                    FileChannel.MapMode.READ_WRITE,
                    0L,
                    capacity,
                    arena
                ),
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
