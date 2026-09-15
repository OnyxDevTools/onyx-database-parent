package com.onyx.extension.common

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClassMetadataConcurrencyTest {

    @Test
    fun `ready reflection cache entries do not wait for the mutation monitor`() {
        val metadata = ClassMetadata()
        val expectedConstructor = metadata.constructor(ReflectionFixture::class.java)
        val expectedClass = metadata.classForName(ReflectionFixture::class.java.name)
        val expectedFields = metadata.fields(ReflectionFixture::class.java)
        val expectedSerializationFields = metadata.serializationFields(ReflectionFixture::class.java)
        val mutationMonitor = ClassMetadata::class.java
            .getDeclaredField("cacheMutationLock")
            .apply { isAccessible = true }
            .get(metadata)

        val monitorHeld = CountDownLatch(1)
        val releaseMonitor = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val holder = thread(name = "reflection-cache-mutation-monitor-holder") {
            synchronized(mutationMonitor) {
                monitorHeld.countDown()
                releaseMonitor.await(5, TimeUnit.SECONDS)
            }
        }

        try {
            assertTrue(monitorHeld.await(1, TimeUnit.SECONDS), "The test did not acquire the mutation monitor")

            val reader = thread(name = "ready-reflection-cache-reader") {
                try {
                    assertSame(expectedConstructor, metadata.constructor(ReflectionFixture::class.java))
                    assertSame(expectedClass, metadata.classForName(ReflectionFixture::class.java.name))
                    assertSame(expectedFields, metadata.fields(ReflectionFixture::class.java))
                    assertSame(
                        expectedSerializationFields,
                        metadata.serializationFields(ReflectionFixture::class.java)
                    )
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                } finally {
                    completed.countDown()
                }
            }

            assertTrue(
                completed.await(1, TimeUnit.SECONDS),
                "Ready reflection cache lookups must not wait for cache mutation"
            )
            failure.get()?.let { throw it }
            reader.join(1000)
        } finally {
            releaseMonitor.countDown()
            holder.join(1000)
        }
    }

    private class ReflectionFixture {
        var number: Long = 0
        var text: String = ""
    }
}
