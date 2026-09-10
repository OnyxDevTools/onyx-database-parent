package com.onyx.diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DiskMapValueUpdateModeTest {
    @Test
    fun `name and header lookups preserve the same live map across a cache flush`() {
        val factory = DefaultDiskMapFactory("value-update-mode", StoreType.IN_MEMORY)
        try {
            val mode = ValueUpdateMode.OVERWRITE_SAME_SIZE
            val map = factory.getHashMap<DiskMap<Long, ByteArray>>(Long::class.java, "nodes", mode)
            assertSame(map, factory.getHashMap(Long::class.java, map.reference, mode))
            assertSame(map, factory.getHashMap(Long::class.java, map.reference))
            factory.flush()
            assertSame(map, factory.getHashMap(Long::class.java, "nodes", mode))
            assertFailsWith<IllegalArgumentException> {
                factory.getHashMap<DiskMap<Long, ByteArray>>(Long::class.java, "nodes", ValueUpdateMode.APPEND)
            }
            assertFailsWith<IllegalArgumentException> {
                factory.getHashMap<DiskMap<Long, ByteArray>>(Long::class.java, map.reference, ValueUpdateMode.APPEND)
            }
        } finally {
            factory.close()
        }
    }

    @Test
    fun `concurrent opens return one map with one write lock`() {
        val factory = DefaultDiskMapFactory("concurrent-value-update-mode", StoreType.IN_MEMORY)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val start = CountDownLatch(1)
            val opens = (0 until 8).map {
                executor.submit<DiskMap<Long, ByteArray>> {
                    start.await()
                    factory.getHashMap(Long::class.java, "nodes", ValueUpdateMode.OVERWRITE_SAME_SIZE)
                }
            }
            start.countDown()
            val first = opens.first().get(10, TimeUnit.SECONDS)
            opens.forEach { assertSame(first, it.get(10, TimeUnit.SECONDS)) }
        } finally {
            executor.shutdownNow()
            factory.close()
        }
    }

    @Test
    fun `cached header lookups do not wait for map creation`() {
        val factory = DefaultDiskMapFactory("cached-header-value-update-mode", StoreType.IN_MEMORY)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val mode = ValueUpdateMode.OVERWRITE_SAME_SIZE
            val map = factory.getHashMap<DiskMap<Long, ByteArray>>(Long::class.java, "nodes", mode)
            val creationLock = DefaultDiskMapFactory::class.java.getDeclaredField("mapCreationLock")
                .apply { isAccessible = true }.get(factory)

            synchronized(creationLock) {
                val implicit = executor.submit<DiskMap<Long, ByteArray>> {
                    factory.getHashMap(Long::class.java, map.reference)
                }
                val explicit = executor.submit<DiskMap<Long, ByteArray>> {
                    factory.getHashMap(Long::class.java, map.reference, mode)
                }
                assertSame(map, implicit.get(10, TimeUnit.SECONDS))
                assertSame(map, explicit.get(10, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
            factory.close()
        }
    }
}
