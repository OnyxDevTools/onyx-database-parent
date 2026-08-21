package com.onyx.diskmap.store.impl

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WholeFileMappingTest {

    @Test
    fun `growth replaces the mapping and preserves buffer semantics`() {
        val path = Files.createTempFile("onyx-whole-file-mapping", ".db")
        try {
            FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
            ).use { channel ->
                val mapping = WholeFileMapping(channel, growthQuantum = 64L, initialRequiredCapacity = 0L)
                try {
                    assertEquals(64L, mapping.capacity)

                    val sourceBytes = byteArrayOf(99, 98, 1, 2, 3, 4, 5, 6, 97)
                    val source = ByteBuffer.wrap(sourceBytes)
                    source.position(2)
                    source.limit(8)

                    assertEquals(6, mapping.write(source, 60L))
                    assertEquals(8, source.position())
                    assertEquals(128L, mapping.capacity)

                    val destinationBytes = ByteArray(12) { 42 }
                    val destination = ByteBuffer.wrap(destinationBytes)
                    destination.position(3)
                    destination.limit(9)
                    mapping.read(destination, 60L)

                    assertEquals(9, destination.position())
                    assertContentEquals(
                        byteArrayOf(42, 42, 42, 1, 2, 3, 4, 5, 6, 42, 42, 42),
                        destinationBytes
                    )
                    mapping.force()
                } finally {
                    mapping.close()
                }
            }

            assertEquals(128L, Files.size(path))
            FileChannel.open(path, StandardOpenOption.READ).use { channel ->
                val persisted = ByteBuffer.allocate(6)
                assertEquals(6, channel.read(persisted, 60L))
                assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), persisted.array())
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
