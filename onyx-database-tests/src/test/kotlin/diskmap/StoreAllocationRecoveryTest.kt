package diskmap

import com.onyx.diskmap.store.Store
import com.onyx.diskmap.store.impl.EncryptedFileChannelStore
import com.onyx.diskmap.store.impl.EncryptedMemoryMappedStore
import com.onyx.diskmap.store.impl.FileChannelStore
import com.onyx.diskmap.store.impl.InMemoryStore
import com.onyx.diskmap.store.impl.MemoryMappedStore
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.impl.DefaultSchemaContext
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Comparator
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StoreAllocationRecoveryTest {

    @Test
    fun embeddedBatchRangeStaysDenseAcrossAlignedPagesAndCommit() {
        withTempDirectory("onyx-aligned-slot-density") { directory ->
            val path = directory.resolve("records.db")
            var store = FileChannelStore(path.toString(), null, false)
            val slotSize = 6
            val pageSize = 4 * 1024
            val reusedSlotCount = 500
            val firstSlotBytes = ByteArray(slotSize) { (it + 1).toByte() }
            val lastSlotBytes = ByteArray(slotSize) { (it + 11).toByte() }
            var lastSlot = 0L

            try {
                val firstSlot = store.allocateSlot(slotSize)
                assertEquals(java.lang.Long.BYTES.toLong(), firstSlot)
                store.write(ByteBuffer.wrap(firstSlotBytes), firstSlot)

                val firstPage = store.allocateAligned(pageSize, pageSize)
                assertEquals(0L, firstPage % pageSize)
                store.write(ByteBuffer.wrap(ByteArray(pageSize) { 3 }), firstPage)
                val firstPageEnd = firstPage + pageSize
                assertEquals(firstPageEnd, store.getFileSize())

                store.commit()
                repeat(reusedSlotCount) { index ->
                    lastSlot = store.allocateSlot(slotSize)
                    assertEquals(firstSlot + slotSize.toLong() * (index + 1L), lastSlot)
                    val bytes = if (index == reusedSlotCount - 1) lastSlotBytes else ByteArray(slotSize) { 7 }
                    store.write(ByteBuffer.wrap(bytes), lastSlot)
                }
                assertEquals(firstPageEnd, store.getFileSize())

                val secondPage = store.allocateAligned(pageSize, pageSize)
                assertEquals(firstPageEnd, secondPage)
                store.write(ByteBuffer.wrap(ByteArray(pageSize) { 9 }), secondPage)
                val finalEnd = secondPage + pageSize
                assertEquals(finalEnd, store.getFileSize())
                store.commit()
                assertTrue(store.close())

                store = FileChannelStore(path.toString(), null, false)
                assertEquals(finalEnd, store.getFileSize())
                assertContentEquals(firstSlotBytes, readBytes(store, firstSlot, slotSize))
                assertContentEquals(lastSlotBytes, readBytes(store, lastSlot, slotSize))
            } finally {
                if (isChannelOpen(store)) store.close()
            }
        }
    }

    @Test
    fun crashRecoveryDoesNotReuseLostEmbeddedBatchRange() {
        withTempDirectory("onyx-embedded-range-crash") { directory ->
            val path = directory.resolve("records.db")
            var store = FileChannelStore(path.toString(), null, false)
            val slotSize = 6
            val pageSize = 4 * 1024
            val firstBytes = ByteArray(slotSize) { 1 }
            val secondBytes = ByteArray(slotSize) { 2 }

            try {
                val firstSlot = store.allocateSlot(slotSize)
                store.write(ByteBuffer.wrap(firstBytes), firstSlot)
                val page = store.allocateAligned(pageSize, pageSize)
                store.write(ByteBuffer.wrap(ByteArray(pageSize) { 3 }), page)

                val secondSlot = store.allocateSlot(slotSize)
                assertEquals(firstSlot + slotSize, secondSlot)
                store.write(ByteBuffer.wrap(secondBytes), secondSlot)
                val pageEnd = page + pageSize
                assertEquals(pageEnd, store.getFileSize())
                crashClose(store)

                store = FileChannelStore(path.toString(), null, false)
                assertEquals(pageEnd, store.getFileSize())
                assertContentEquals(firstBytes, readBytes(store, firstSlot, slotSize))
                assertContentEquals(secondBytes, readBytes(store, secondSlot, slotSize))
                assertEquals(pageEnd, store.allocateSlot(slotSize))
            } finally {
                if (isChannelOpen(store)) store.close()
            }
        }
    }

    @Test
    fun unalignedAllocationReclaimsTrailingBatchReservation() {
        withTempDirectory("onyx-unaligned-after-slot") { directory ->
            val store = FileChannelStore(directory.resolve("records.db").toString(), null, false)
            try {
                val slot = store.allocateSlot(6)
                assertEquals(java.lang.Long.BYTES.toLong(), slot)

                val direct = store.allocate(96)
                assertEquals(slot + 6, direct)
                assertEquals(direct + 96, store.getFileSize())
                assertEquals(direct + 96, store.allocateSlot(6))
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun interleavedObjectsAndSlotsShareOneRangeAndSurviveRepeatedCommits() {
        withTempDirectory("onyx-file-ranges") { directory ->
            val path = directory.resolve("records.db")
            var store = FileChannelStore(path.toString(), null, false)
            val objectPositions = ArrayList<Long>()
            try {
                objectPositions += store.writeObject("first")
                val afterFirst = store.getFileSize()
                val slot = store.allocateSlot(6)
                assertEquals(afterFirst, slot)
                store.write(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6)), slot)

                objectPositions += store.writeObject("second")
                assertEquals(slot + 6, objectPositions.last())

                repeat(3) { round ->
                    val expectedPosition = store.getFileSize()
                    store.commit()
                    assertEquals(expectedPosition, store.getFileSize())
                    objectPositions += store.writeObject("round-$round")
                    assertEquals(expectedPosition, objectPositions.last())
                }

                val committedEnd = store.getFileSize()
                store.commit()
                assertEquals(committedEnd, store.getFileSize())
                assertTrue(store.close())

                store = FileChannelStore(path.toString(), null, false)
                assertEquals(committedEnd, store.getFileSize())
                assertEquals("first", store.getObject(objectPositions.first()))
                assertEquals("round-2", store.getObject(objectPositions.last()))
                store.commit()
                val afterReopen = store.writeObject("after-reopen")
                assertEquals(committedEnd, afterReopen)
                val afterReopenEnd = store.getFileSize()
                store.commit()
                assertEquals(afterReopenEnd, store.getFileSize())
                assertEquals("after-reopen", store.getObject(afterReopen))
            } finally {
                if (isChannelOpen(store)) store.close()
            }
        }
    }

    @Test
    fun regularFileCrashRecoveryDiscardsOnlyTheUnwrittenReservationTail() {
        withTempDirectory("onyx-file-crash") { directory ->
            val path = directory.resolve("records.db")
            val first = FileChannelStore(path.toString(), null, false)
            val record = ByteArray(37) { it.toByte() }
            val position = first.allocateObject(record.size)
            first.write(ByteBuffer.wrap(record), position)
            val writtenEnd = first.getFileSize()

            val persistedReservation = readLong(path)
            assertTrue(persistedReservation - writtenEnd > 900_000)
            crashClose(first)

            val recovered = FileChannelStore(path.toString(), null, false)
            try {
                assertEquals(writtenEnd, recovered.getFileSize())
                val next = recovered.allocateSlot(6)
                assertEquals(writtenEnd, next)
                recovered.write(ByteBuffer.wrap(ByteArray(6) { 9 }), next)
                recovered.commit()
                assertEquals(next + 6, recovered.getFileSize())
                assertEquals(next + 6, readLong(path))
            } finally {
                recovered.close()
            }
        }
    }

    @Test
    fun regularFileCrashRecoveryCanReuseAnAllocatedButUnwrittenAddress() {
        withTempDirectory("onyx-file-unwritten") { directory ->
            val path = directory.resolve("records.db")
            val first = FileChannelStore(path.toString(), null, false)
            val uncommittedPosition = first.allocateObject(128)
            assertEquals(8L, uncommittedPosition)
            crashClose(first)

            val recovered = FileChannelStore(path.toString(), null, false)
            try {
                assertEquals(8L, recovered.getFileSize())
                assertEquals(uncommittedPosition, recovered.allocateObject(16))
            } finally {
                recovered.close()
            }
        }
    }

    @Test
    fun mappedOpenUsesPhysicalEndCapturedBeforeWarmMapping() {
        withTempDirectory("onyx-cross-mode-recovery") { directory ->
            val path = directory.resolve("records.db")
            val regular = FileChannelStore(path.toString(), null, false)
            val position = regular.writeObject("cross-mode")
            val writtenEnd = regular.getFileSize()
            assertTrue(readLong(path) > writtenEnd)
            crashClose(regular)

            val mapped = MemoryMappedStore(path.toString(), null, false)
            try {
                assertEquals(writtenEnd, mapped.getFileSize())
                assertEquals("cross-mode", mapped.getObject(position))
                assertTrue(mapped.close())
                assertEquals(writtenEnd, Files.size(path))
            } finally {
                if (isChannelOpen(mapped)) mapped.close()
            }
        }
    }

    @Test
    fun cleanFileCommitPreservesAnAllocatedButUnwrittenAddress() {
        withTempDirectory("onyx-file-clean-unwritten") { directory ->
            val path = directory.resolve("records.db")
            var store = FileChannelStore(path.toString(), null, false)
            try {
                val position = store.allocateObject(128)
                val logicalEnd = store.getFileSize()
                store.commit()

                assertEquals(position + 128, logicalEnd)
                assertEquals(logicalEnd, store.getFileSize())
                assertEquals(logicalEnd, readLong(path))
                assertTrue(Files.size(path) >= logicalEnd)
                assertTrue(store.close())

                store = FileChannelStore(path.toString(), null, false)
                assertEquals(logicalEnd, store.getFileSize())
                assertEquals(logicalEnd, store.allocateObject(16))
            } finally {
                if (isChannelOpen(store)) store.close()
            }
        }
    }

    @Test
    fun cleanRegularFileCloseTruncatesAResetPhysicalTail() {
        withTempDirectory("onyx-file-reset-tail") { directory ->
            val path = directory.resolve("records.db")
            val store = FileChannelStore(path.toString(), null, false)
            store.writeObject(ByteArray(16 * 1024) { 7 })
            store.commit()
            assertTrue(Files.size(path) > java.lang.Long.BYTES)

            store.reset()
            assertEquals(java.lang.Long.BYTES.toLong(), store.getFileSize())
            assertTrue(store.close())
            assertEquals(java.lang.Long.BYTES.toLong(), Files.size(path))
        }
    }

    @Test
    fun mappedStoreKeepsLogicalEndSeparateFromMappingAndCommitThenWriteIsStable() {
        withTempDirectory("onyx-mapped-ranges") { directory ->
            val path = directory.resolve("records.db")
            var store = MemoryMappedStore(path.toString(), null, false)
            val positions = ArrayList<Long>()
            try {
                positions += store.writeObject("mapped-0")
                var expectedEnd = store.getFileSize()
                assertTrue(Files.size(path) > expectedEnd)

                repeat(3) { round ->
                    store.commit()
                    assertEquals(expectedEnd, store.getFileSize())
                    positions += store.writeObject("mapped-${round + 1}")
                    assertEquals(expectedEnd, positions.last())
                    expectedEnd = store.getFileSize()
                }

                store.commit()
                assertEquals(expectedEnd, store.getFileSize())
                assertTrue(store.close())
                assertEquals(expectedEnd, Files.size(path))

                store = MemoryMappedStore(path.toString(), null, false)
                assertEquals(expectedEnd, store.getFileSize())
                assertEquals("mapped-0", store.getObject(positions.first()))
                assertEquals("mapped-3", store.getObject(positions.last()))
                store.commit()
                val afterReopen = store.writeObject("mapped-after-reopen")
                assertEquals(expectedEnd, afterReopen)
                expectedEnd = store.getFileSize()
                store.commit()
                assertEquals(expectedEnd, store.getFileSize())
                assertEquals("mapped-after-reopen", store.getObject(afterReopen))

                val unwritten = store.allocateObject(128)
                val unwrittenEnd = store.getFileSize()
                store.commit()
                assertEquals(unwritten + 128, unwrittenEnd)
                assertEquals(unwrittenEnd, store.getFileSize())
                assertEquals(unwrittenEnd, readLong(path))
                assertTrue(store.close())

                store = MemoryMappedStore(path.toString(), null, false)
                assertEquals(unwrittenEnd, store.getFileSize())
                assertEquals(unwrittenEnd, store.allocateObject(16))
            } finally {
                if (isChannelOpen(store)) store.close()
            }
        }
    }

    @Test
    fun inMemoryCommitDoesNotRegressWrittenAllocationCursor() {
        val store = InMemoryStore(null, "in-memory-commit-${System.nanoTime()}")
        try {
            val first = store.writeObject("first")
            val afterFirst = store.getFileSize()
            store.commit()
            assertEquals(afterFirst, store.getFileSize())

            val second = store.writeObject("second")
            assertEquals(afterFirst, second)
            val afterSecond = store.getFileSize()
            store.commit()
            assertEquals(afterSecond, store.getFileSize())
            assertEquals("first", store.getObject(first))
            assertEquals("second", store.getObject(second))

            val unwritten = store.allocateObject(128)
            val unwrittenEnd = store.getFileSize()
            store.commit()
            assertEquals(unwritten + 128, unwrittenEnd)
            assertEquals(unwrittenEnd, store.getFileSize())
            assertEquals(unwrittenEnd, store.allocateObject(16))
        } finally {
            store.close()
        }
    }

    @Test
    fun retiredObjectFramesAreReusableOnlyAfterPreparedCommitAndPublish() {
        withTempDirectory("onyx-object-reuse") { directory ->
            val store = FileChannelStore(directory.resolve("records.db").toString(), null, false)
            try {
                assertCommitGatedObjectReuse(store)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun encryptedFileAndMappedStoresUseCommitGatedObjectReuse() {
        withTempDirectory("onyx-encrypted-reuse") { directory ->
            val context = DefaultSchemaContext("encrypted-reuse-${System.nanoTime()}", directory.toString())
            try {
                val fileStore = EncryptedFileChannelStore(
                    directory.resolve("encrypted-file.db").toString(),
                    context,
                    false
                )
                try {
                    assertCommitGatedObjectReuse(fileStore)
                } finally {
                    fileStore.close()
                }

                val mappedStore = EncryptedMemoryMappedStore(
                    directory.resolve("encrypted-mapped.db").toString(),
                    context,
                    false
                )
                try {
                    assertCommitGatedObjectReuse(mappedStore)
                } finally {
                    mappedStore.close()
                }
            } finally {
                context.shutdown()
                Contexts.remove(context)
            }
        }
    }

    private fun assertCommitGatedObjectReuse(store: Store) {
        val original = ByteArray(128) { 1 }
        val originalPosition = store.writeObject(original)
        val latePosition = store.writeObject(ByteArray(256) { 2 })

        store.retireObject(originalPosition)
        // Publishing without a prepared, durable generation is inert.
        store.publishRetiredObjects()
        val pendingPosition = store.writeObject(ByteArray(16) { 3 })
        assertNotEquals(originalPosition, pendingPosition)

        store.prepareRetiredObjects()
        // This retirement arrived after the generation boundary and must wait.
        store.retireObject(latePosition)
        store.commit()
        val beforePublishPosition = store.writeObject(ByteArray(16) { 4 })
        assertNotEquals(originalPosition, beforePublishPosition)
        assertNotEquals(latePosition, beforePublishPosition)

        val endBeforeReuse = store.getFileSize()
        store.publishRetiredObjects()
        val reusedPosition = store.writeObject(ByteArray(16) { 5 })
        assertEquals(originalPosition, reusedPosition)
        assertEquals(endBeforeReuse, store.getFileSize())
        assertContentEquals(ByteArray(16) { 5 }, store.getObject(reusedPosition))

        // The original slot capacity remains known even though its new frame is
        // smaller. The later, larger slot is also published; best-fit must pick
        // the original slot for this medium-sized frame.
        store.retireObject(reusedPosition)
        store.prepareRetiredObjects()
        store.commit()
        store.publishRetiredObjects()
        val capacityPreservingReuse = store.writeObject(ByteArray(64) { 6 })
        assertEquals(originalPosition, capacityPreservingReuse)
        assertContentEquals(ByteArray(64) { 6 }, store.getObject(capacityPreservingReuse))
    }

    private fun readLong(path: Path): Long = FileChannel.open(path, StandardOpenOption.READ).use { channel ->
        val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
        while (buffer.hasRemaining()) channel.read(buffer)
        buffer.flip()
        buffer.long
    }

    private fun readBytes(store: Store, position: Long, size: Int): ByteArray {
        val buffer = ByteBuffer.allocate(size)
        store.read(buffer, position)
        buffer.flip()
        return ByteArray(size).also(buffer::get)
    }

    private fun crashClose(store: FileChannelStore) {
        val field = FileChannelStore::class.java.getDeclaredField("channel")
        field.isAccessible = true
        val channel = field.get(store) as FileChannel
        channel.force(true)
        channel.close()
    }

    private fun isChannelOpen(store: FileChannelStore): Boolean {
        val field = FileChannelStore::class.java.getDeclaredField("channel")
        field.isAccessible = true
        return (field.get(store) as? FileChannel)?.isOpen == true
    }

    private fun withTempDirectory(prefix: String, block: (Path) -> Unit) {
        val directory = Files.createTempDirectory(prefix)
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
