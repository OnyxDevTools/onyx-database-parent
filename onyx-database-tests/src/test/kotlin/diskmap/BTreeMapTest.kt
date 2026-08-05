package diskmap

import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import database.base.DatabaseBaseTest
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BTreeMapTest {

    @Before
    fun before() {
        DatabaseBaseTest.deleteDatabase(TEST_DATABASE)
        DatabaseBaseTest.deleteDatabase("$TEST_DATABASE.idx")
    }

    @Test
    fun splitsKeepEntriesOrderedAndRecordIdsStable() {
        val store = DefaultDiskMapFactory(TEST_DATABASE)
        val map = store.getHashMap<DiskMap<Int, String>>(Int::class.java, "tree")

        val stable = map.putAndGet(10, "before").recordId
        (0 until 2500).shuffled(Random(42)).forEach { map[it] = "v$it" }
        map[10] = "after"

        assertEquals(stable, map.getRecID(10))
        assertEquals("after", map.getWithRecID(stable))
        assertEquals((0 until 2500).toList(), map.keys.toList())
        assertEquals(2500, map.size)
        assertEquals(2500, map.references.size)
        store.close()
    }

    @Test
    fun putCanCaptureThePreviouslyPersistedValueForIndexMaintenance() {
        val store = DefaultDiskMapFactory(TEST_DATABASE)
        val map = store.getHashMap<DiskMap<Int, String>>(Int::class.java, "tree")
        val stable = map.putAndGet(10, "before").recordId

        val result = map.putAndGet(10, "after", preUpdate = null, capturePreviousValue = true)

        assertFalse(result.isInsert)
        assertEquals(stable, result.recordId)
        assertEquals("before", result.previousValue)
        assertEquals("after", map.getWithRecID(stable))
        store.close()
    }

    @Test
    fun deleteRebalancesPagesAndContractsTheRoot() {
        val store = DefaultDiskMapFactory(TEST_DATABASE)
        val map = store.getHashMap<DiskMap<Int, String>>(Int::class.java, "tree")
        (0 until 2500).forEach { map[it] = "v$it" }
        val remainingIds = (2400 until 2500).associateWith(map::getRecID)

        (0 until 2400).shuffled(Random(7)).forEach { key ->
            assertEquals("v$key", map.remove(key))
        }

        assertEquals((2400 until 2500).toList(), map.keys.toList())
        (2400 until 2500).forEach {
            assertEquals("v$it", map[it])
            assertEquals(remainingIds[it], map.getRecID(it))
        }

        (2400 until 2500).forEach { map.remove(it) }
        assertTrue(map.isEmpty())
        assertFalse(map.entries.iterator().hasNext())

        map[3] = "reinserted"
        assertEquals("reinserted", map[3])
        assertEquals(listOf(3), map.keys.toList())
        store.close()
    }

    @Test
    fun rootAndStableIdsSurviveReopen() {
        val entryCount = 60_000
        var store = DefaultDiskMapFactory(TEST_DATABASE)
        var map = store.getHashMap<DiskMap<Int, String>>(Int::class.java, "tree")
        (0 until entryCount).forEach { map[it] = "v$it" }
        val recordId = map.getRecID(777)
        store.commit()
        store.close()

        store = DefaultDiskMapFactory(TEST_DATABASE)
        map = store.getHashMap(Int::class.java, "tree")
        assertEquals("v777", map[777])
        assertEquals(recordId, map.getRecID(777))
        assertEquals("v777", map.getWithRecID(recordId))
        assertEquals((0 until entryCount).toList(), map.keys.toList())

        map.clearCache()
        (0 until entryCount).forEach { map.remove(it) }
        assertNull(map[777])
        assertTrue(map.isEmpty())
        store.commit()
        store.close()

        store = DefaultDiskMapFactory(TEST_DATABASE)
        map = store.getHashMap(Int::class.java, "tree")
        assertTrue(map.isEmpty())
        store.close()
    }

    companion object {
        private const val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/bTreeMapTest.db"
    }
}
