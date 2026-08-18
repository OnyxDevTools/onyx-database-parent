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
    fun `failed final force still leaves the mapped store physically closed`() {
        val directory = Files.createTempDirectory("onyx-mapped-close-failure")
        val path = directory.resolve("data.db")
        val store = FailingFinalForceMemoryMappedStore()

        try {
            store.bufferSliceSize = 64
            assertTrue(store.open(path.toString()))
            store.allocate(java.lang.Long.BYTES)
            store.failFinalForce = true

            assertFalse(store.close())
            assertFailsWith<InitializationException> {
                store.write(ByteBuffer.wrap(byteArrayOf(1)), 0)
            }
        } finally {
            runCatching { store.close() }
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    private class FailingFinalForceMemoryMappedStore : MemoryMappedStore() {
        var failFinalForce = false

        override fun forceWrites() {
            if (failFinalForce) {
                throw IOException("simulated final force failure")
            }
            super.forceWrites()
        }
    }
}
