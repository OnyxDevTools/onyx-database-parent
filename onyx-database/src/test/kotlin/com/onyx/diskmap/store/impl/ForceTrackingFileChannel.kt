package com.onyx.diskmap.store.impl

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

/** Detects explicit writeback without depending on the OS page cache's visibility. */
internal class ForceTrackingFileChannel(
    private val delegate: FileChannel,
    private val rejectForce: Boolean = false,
    private val unmappedSegments: Boolean = false
) : FileChannel() {
    val forceRequests = ArrayList<Boolean>()

    override fun force(metaData: Boolean) {
        forceRequests += metaData
        check(!rejectForce) { "Unexpected channel force" }
        delegate.force(metaData)
    }

    override fun map(mode: MapMode, offset: Long, size: Long, arena: Arena): MemorySegment =
        // Native segments support the same copies as mapped segments, but
        // force() throws. This catches accidental mapped writeback requests.
        if (unmappedSegments) arena.allocate(size) else delegate.map(mode, offset, size, arena)

    override fun read(dst: ByteBuffer): Int = delegate.read(dst)
    override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.read(dsts, offset, length)
    override fun read(dst: ByteBuffer, position: Long): Int = delegate.read(dst, position)
    override fun write(src: ByteBuffer): Int = delegate.write(src)
    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.write(srcs, offset, length)
    override fun write(src: ByteBuffer, position: Long): Int = delegate.write(src, position)
    override fun position(): Long = delegate.position()
    override fun position(newPosition: Long): FileChannel = apply { delegate.position(newPosition) }
    override fun size(): Long = delegate.size()
    override fun truncate(size: Long): FileChannel = apply { delegate.truncate(size) }
    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long =
        delegate.transferTo(position, count, target)
    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long =
        delegate.transferFrom(src, position, count)
    override fun map(mode: MapMode, position: Long, size: Long) = delegate.map(mode, position, size)
    override fun lock(position: Long, size: Long, shared: Boolean) = delegate.lock(position, size, shared)
    override fun tryLock(position: Long, size: Long, shared: Boolean) = delegate.tryLock(position, size, shared)
    override fun implCloseChannel() = delegate.close()
}
