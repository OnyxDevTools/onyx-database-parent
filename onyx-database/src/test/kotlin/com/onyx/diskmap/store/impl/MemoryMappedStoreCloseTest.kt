package com.onyx.diskmap.store.impl

import com.onyx.exception.InitializationException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryMappedStoreCloseTest {

    @Test
    fun `failed final header write still leaves the mapped store physically closed`() {
        val directory = Files.createTempDirectory("onyx-mapped-close-failure")
        val path = directory.resolve("data.db")
        val store = FailingFinalWriteMemoryMappedStore()

        try {
            assertTrue(store.open(path.toString()))
            store.allocate(java.lang.Long.BYTES)
            store.allocateSlot(16)
            store.failFinalWrite = true

            assertFalse(store.close())
            store.failFinalWrite = false
            assertFailsWith<InitializationException> {
                store.write(ByteBuffer.wrap(byteArrayOf(1)), 0)
            }
        } finally {
            runCatching { store.close() }
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    private class FailingFinalWriteMemoryMappedStore : MemoryMappedStore() {
        var failFinalWrite = false

        override fun write(buffer: ByteBuffer, position: Long): Int {
            if (failFinalWrite && position == 0L) {
                throw IOException("simulated final header write failure")
            }
            return super.write(buffer, position)
        }
    }
}
