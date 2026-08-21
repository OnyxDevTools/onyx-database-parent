package diskmap

import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import org.junit.Test
import java.util.Date
import kotlin.random.Random
import kotlin.test.assertEquals

class BTreeKeyCodecTest {

    @Test
    fun primitiveCodecsPreserveTheirNaturalOrdering() {
        val factory = DefaultDiskMapFactory("btree-key-codecs", StoreType.IN_MEMORY)
        try {
            val longs = factory.getHashMap<DiskMap<Long, Long>>(Long::class.java, "longs")
            val longKeys = (-600L..600L).shuffled(Random(9))
            longKeys.forEach { longs[it] = it }
            longs.clearCache()
            assertEquals(longKeys.sorted(), longs.keys.toList())

            val floats = factory.getHashMap<DiskMap<Float, Int>>(Float::class.java, "floats")
            val floatKeys = listOf(
                Float.NEGATIVE_INFINITY, -100.5f, -0.0f, 0.0f, 7.25f,
                Float.POSITIVE_INFINITY, Float.NaN
            )
            floatKeys.shuffled(Random(3)).forEachIndexed { index, key -> floats[key] = index }
            floats.clearCache()
            assertEquals(floatKeys.sorted(), floats.keys.toList())

            val doubles = factory.getHashMap<DiskMap<Double, Int>>(Double::class.java, "doubles")
            val doubleKeys = listOf(
                Double.NEGATIVE_INFINITY, -1.0, -0.0, 0.0, 1.0,
                Double.POSITIVE_INFINITY, Double.NaN
            )
            doubleKeys.shuffled(Random(4)).forEachIndexed { index, key -> doubles[key] = index }
            doubles.clearCache()
            assertEquals(doubleKeys.sorted(), doubles.keys.toList())
        } finally {
            factory.close()
        }
    }

    @Test
    fun serializedComparableKeysStayOrderedAfterCacheEviction() {
        val factory = DefaultDiskMapFactory("btree-object-key-codecs", StoreType.IN_MEMORY)
        try {
            val strings = factory.getHashMap<DiskMap<String, Int>>(String::class.java, "strings")
            val stringKeys = (0 until 2_000).map { "key-${it.toString().padStart(5, '0')}" }
                .shuffled(Random(21))
            stringKeys.forEachIndexed { index, key -> strings[key] = index }
            strings.clearCache()
            assertEquals(stringKeys.sorted(), strings.keys.toList())

            val dates = factory.getHashMap<DiskMap<Date, Long>>(Date::class.java, "dates")
            val dateKeys = (0 until 600).map { Date((it - 300L) * 86_400_000L) }.shuffled(Random(22))
            dateKeys.forEach { dates[it] = it.time }
            dates.clearCache()
            assertEquals(dateKeys.sorted(), dates.keys.toList())
        } finally {
            factory.close()
        }
    }
}
