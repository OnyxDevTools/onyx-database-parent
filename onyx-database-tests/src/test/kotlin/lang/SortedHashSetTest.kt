package lang

import com.onyx.lang.SortedHashSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SortedHashSetTest {

    @Test
    fun `adding elements maintains sort order`() {
        val set = SortedHashSet<Int>(Comparator.naturalOrder())
        set.add(5)
        set.add(1)
        set.add(3)
        set.add(2)
        set.add(4)
        assertEquals(listOf(1, 2, 3, 4, 5), set)
    }

    @Test
    fun `adding duplicate elements returns false and does not change size`() {
        val set = SortedHashSet<Int>(Comparator.naturalOrder())
        val first = set.add(1)
        val second = set.add(1)
        assertTrue(first)
        assertFalse(second)
        assertEquals(1, set.size)
    }

    @Test
    fun `comparator determines uniqueness`() {
        val set = SortedHashSet<String>(Comparator { a, b -> a.length.compareTo(b.length) })
        val first = set.add("a")
        val second = set.add("b")
        val third = set.add("ccc")
        assertTrue(first)
        assertFalse(second)
        assertTrue(third)
        assertEquals(listOf("a", "ccc"), set)
    }
}

