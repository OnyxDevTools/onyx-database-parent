package com.onyx.lang.map

import java.util.AbstractMap.SimpleImmutableEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SortedLongNullMapTest {
    @Test
    fun `snapshot preserves map semantics across growth and long boundaries`() {
        val builder = SortedLongNullMap.Builder()
        val ids = listOf(Long.MIN_VALUE) + (1L..10_000L).map { it * 97 } + Long.MAX_VALUE
        ids.forEach(builder::add)
        val snapshot = builder.build()
        val expected = ids.associateWith { null }

        assertEquals(expected, snapshot)
        assertEquals(snapshot, expected)
        assertEquals(expected.hashCode(), snapshot.hashCode())
        assertEquals(ids, snapshot.keys.toList())
        assertEquals(expected.keys.hashCode(), snapshot.keys.hashCode())
        assertEquals(expected.entries, snapshot.entries)
        assertEquals(expected.entries.hashCode(), snapshot.entries.hashCode())
        assertEquals(List(ids.size) { null }, snapshot.values.toList())
        assertTrue(snapshot.containsValue(null))
        assertFalse(snapshot.containsValue("not null"))
        ids.forEach { assertTrue(snapshot.containsKey(it)) }
        assertFalse(snapshot.containsKey(0))
        assertFalse(snapshot.containsKey(Long.MAX_VALUE - 1))
        assertNull(snapshot[0])
        assertNull(snapshot[Long.MAX_VALUE])
        assertTrue(snapshot.entries.contains(SimpleImmutableEntry(97L, null)))
        assertFalse(snapshot.entries.contains(SimpleImmutableEntry(97L, "not null")))
        assertFalse(snapshot.entries.contains(SimpleImmutableEntry(0L, null)))
        assertFalse((snapshot as Map<*, *>).containsKey(97))
        assertFalse((snapshot.keys as Set<*>).contains(97))
        assertFalse((snapshot.entries as Set<*>).contains(SimpleImmutableEntry(97, null)))
        assertEquals("absent", snapshot.getOrDefault(0, "absent"))
        assertNull(snapshot.getOrDefault(97, "absent"))
    }

    @Test
    fun `builder transfers ownership so subsequent builds cannot change a published snapshot`() {
        val builder = SortedLongNullMap.Builder()
        builder.add(3)
        builder.add(3)
        builder.add(9)
        val first = builder.build()
        builder.add(1)
        builder.add(5)
        val second = builder.build()

        assertEquals(mapOf(3L to null, 9L to null), first)
        assertEquals(mapOf(1L to null, 5L to null), second)
        assertEquals(emptyMap(), builder.build())
        assertFalse(builder.build().containsValue(null))
    }

    @Test
    fun `snapshot views have immutable entries and exhausted iterators reject reads`() {
        val builder = SortedLongNullMap.Builder()
        builder.add(42)
        val snapshot = builder.build()
        val keys = snapshot.keys.iterator()
        assertEquals(42L, keys.next())
        assertFalse(keys.hasNext())
        assertFailsWith<NoSuchElementException> { keys.next() }
        val entries = snapshot.entries.iterator()
        val entry = entries.next()
        assertFalse(entries.hasNext())
        assertFailsWith<NoSuchElementException> { entries.next() }
        assertFailsWith<UnsupportedOperationException> {
            (entry as MutableMap.MutableEntry<Long, Any?>).setValue("changed")
        }
        assertEquals(mapOf(42L to null), snapshot)
    }
}
