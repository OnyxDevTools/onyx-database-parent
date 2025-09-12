package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.impl.DiskSkipListMap
import database.base.DatabaseBaseTest
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

class RangeTraversalTest {
    companion object {
        private const val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/rangeTraversal.db"

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            DatabaseBaseTest.deleteDatabase(TEST_DATABASE)
            DatabaseBaseTest.deleteDatabase("$TEST_DATABASE.idx")
        }
    }

    @Test
    fun aboveBelowBetweenReachBottom() {
        val store = DefaultDiskMapFactory(TEST_DATABASE)
        val map = store.getHashMap(Int::class.java, "range") as DiskSkipListMap<Int, String>

        for (i in 0 until 100) {
            map.put(i, "v" + i)
        }

        val aboveVals = map.above(90, true).mapNotNull { map.getWithRecID(it) }
        assertEquals((90..99).map { "v$it" }.toSet(), aboveVals.toSet())

        val belowVals = map.below(9, true).mapNotNull { map.getWithRecID(it) }
        assertEquals((0..9).map { "v$it" }.toSet(), belowVals.toSet())

        val betweenVals = map.between(40, true, 44, true).mapNotNull { map.getWithRecID(it) }
        assertEquals((40..44).map { "v$it" }.toSet(), betweenVals.toSet())

        store.close()
    }
}
