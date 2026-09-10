package com.onyx.diskmap.store.impl

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataStoreWritebackTest {
    @Test
    fun `file store commits and closes without forcing its channel`() = checkStore { FileChannelStore(it, null, false) }

    @Test
    fun `mapped store commits and closes without forcing its channel`() = checkStore { MemoryMappedStore(it, null, false) }

    private fun checkStore(create: (String) -> FileChannelStore) {
        val path = Files.createTempFile("onyx-data-writeback", ".db")
        val store = create(path.toString())
        val channel = ForceTrackingFileChannel(store.channel!!, rejectForce = true)
        store.channel = channel
        try {
            // Cross the default mapping capacity, then finish a partial
            // allocation reservation during commit and another during close.
            val extentSize = 5 * 1024 * 1024
            val extent = store.allocateObject(extentSize)
            val payload = byteArrayOf(1, 2, 3, 4)
            val position = extent + extentSize - payload.size
            store.write(ByteBuffer.wrap(payload), position)
            store.allocateSlot(16)
            store.commit()
            store.allocateSlot(16)
            val logicalEnd = store.getFileSize()

            assertTrue(store.close())
            assertTrue(channel.forceRequests.isEmpty())
            assertEquals(logicalEnd, Files.size(path))
            FileChannel.open(path, StandardOpenOption.READ).use { reader ->
                val actual = ByteBuffer.allocate(payload.size)
                while (actual.hasRemaining()) {
                    check(reader.read(actual, position + actual.position()) > 0)
                }
                assertContentEquals(payload, actual.array())
                val header = ByteBuffer.allocate(Long.SIZE_BYTES)
                while (header.hasRemaining()) check(reader.read(header, header.position().toLong()) > 0)
                assertEquals(logicalEnd, header.flip().long)
            }
        } finally {
            if (channel.isOpen) store.close()
            Files.deleteIfExists(path)
        }
    }
}
