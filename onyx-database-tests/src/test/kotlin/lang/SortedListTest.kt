package lang

import com.onyx.lang.SortedList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class SortedListTest {
    @Test
    fun `equal keys remain sorted and preserve insertion order`() {
        val input = (0..1023).map { it % 11 to it }.shuffled(Random(42))
        val comparator = compareBy<Pair<Int, Int>> { it.first }
        val results = SortedList(comparator)
        input.forEach { results.add(it) }
        assertEquals(input.sortedWith(comparator), results)
    }
}
