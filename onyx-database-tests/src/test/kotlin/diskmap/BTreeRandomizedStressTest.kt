package diskmap

import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import org.junit.Test
import java.util.Collections
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BTreeRandomizedStressTest {

    @Test
    fun randomizedInsertDeleteMatchesHashMapAfterCacheEviction() {
        val factory = DefaultDiskMapFactory("btree-random-stress", StoreType.IN_MEMORY)
        try {
            val actual = factory.getHashMap<DiskMap<Int, String>>(Int::class.java, "tree")
            val expected = HashMap<Int, String>()
            val order = (0 until 100_000).toMutableList()
            Collections.shuffle(order, Random(0x5eedL))

            order.forEach { key ->
                val value = "v$key"
                expected[key] = value
                actual[key] = value
            }
            actual.clearCache()

            order.forEachIndexed { step, key ->
                assertEquals(expected.remove(key), actual.remove(key), "remove mismatch at step=$step key=$key")
                assertEquals(expected.size.toLong(), actual.longSize(), "size mismatch at step=$step")
                if (step % 4_093 == 0) {
                    assertEquals(expected.keys.sorted(), actual.keys.toList(), "keys mismatch at step=$step")
                    actual.clearCache()
                }
            }
            assertTrue(actual.isEmpty())
        } finally {
            factory.close()
        }
    }
}
