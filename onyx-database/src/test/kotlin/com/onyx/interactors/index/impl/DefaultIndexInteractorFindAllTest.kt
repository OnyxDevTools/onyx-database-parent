package com.onyx.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.context.SchemaContext
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultIndexInteractorFindAllTest {
    @Test
    fun `exact matches remain detached from later writes and store shutdown`() {
        lateinit var snapshot: Map<Long, Any?>
        withIndex(String::class.java) { interactor ->
            for (reference in 2_000L downTo 1L) interactor.save("match", 0, reference)
            interactor.save("before", 0, 2_001)
            interactor.save("next", 0, 2_002)
            snapshot = interactor.findAll("match")

            assertEquals((1L..2_000L).toList(), snapshot.keys.toList())
            interactor.delete("match", 1)
            interactor.save("match", 0, 2_003)
            assertFalse(interactor.findAll("match").containsKey(1))
            assertTrue(interactor.findAll("match").containsKey(2_003))
            interactor.clear()
            assertTrue(interactor.findAll("match").isEmpty())
        }
        assertEquals((1L..2_000L).associateWith { null }, snapshot)
    }

    @Test
    fun `lookup normalizes query values and leaves null unindexed`() {
        withIndex(Long::class.java) { interactor ->
            interactor.save(7, 0, 41)
            interactor.save(8L, 0, 42)
            interactor.save(null, 0, 43)

            assertEquals(mapOf(41L to null), interactor.findAll("7"))
            assertEquals(mapOf(41L to null), interactor.findAll(7))
            assertTrue(interactor.findAll(null).isEmpty())
            assertTrue(interactor.findAll(6).isEmpty())
        }
    }

    @Test
    fun `ordering equivalent object values share one sorted snapshot`() {
        withIndex(ComparableValue::class.java) { interactor ->
            interactor.save(ComparableValue(10, 2), 0, 30)
            interactor.save(ComparableValue(10, 1), 0, 10)
            interactor.save(ComparableValue(10, 3), 0, 20)
            interactor.save(ComparableValue(9, 0), 0, 5)
            interactor.save(ComparableValue(11, 0), 0, 40)

            assertEquals(listOf(10L, 20L, 30L), interactor.findAll(ComparableValue(10, 0)).keys.toList())
        }
    }

    private fun withIndex(type: Class<*>, action: (DefaultIndexInteractor) -> Unit) {
        val factory = DefaultDiskMapFactory("find-all-snapshot", StoreType.IN_MEMORY)
        val context = Proxy.newProxyInstance(
            SchemaContext::class.java.classLoader,
            arrayOf(SchemaContext::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "getDataFile" -> factory
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FindAllSnapshotContext"
                else -> error("Unexpected SchemaContext call: ${method.name}")
            }
        } as SchemaContext
        try {
            action(DefaultIndexInteractor(
                EntityDescriptor(IndexedEntity::class.java),
                IndexDescriptor(name = "indexed", type = type),
                context
            ))
        } finally {
            // The interactor keeps only a weak reference; retain the fixture context through use.
            java.lang.ref.Reference.reachabilityFence(context)
            factory.close()
        }
    }

    @Entity
    private class IndexedEntity : ManagedEntity() {
        @Identifier
        var id: Long = 0
    }

    private data class ComparableValue(var order: Int = 0, var representation: Int = 0) : Comparable<ComparableValue> {
        override fun compareTo(other: ComparableValue): Int = order.compareTo(other.order)
    }
}
