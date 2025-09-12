package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.impl.DiskSkipListMap
import database.base.DatabaseBaseTest
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals

class NullKeyTest {

    companion object {
        private const val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/nullKeyTest.db"

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            DatabaseBaseTest.deleteDatabase(TEST_DATABASE)
            DatabaseBaseTest.deleteDatabase("$TEST_DATABASE.idx")
        }
    }

    @Test
    fun putAndGetNullKey() {
        val store = DefaultDiskMapFactory(TEST_DATABASE)
        val map: MutableMap<String?, String> = store.getHashMap(String::class.java, "nullKeyMap")
        map[null] = "nil"
        assertEquals("nil", map[null])
        store.close()
    }

    @Test
    fun aboveSkipsNullKey() {
        val store = DefaultDiskMapFactory(TEST_DATABASE)
        val map: MutableMap<String?, String> = store.getHashMap(String::class.java, "rangeNullKeyMap")
        map[null] = "nil"
        map["b"] = "bee"
        val skipMap = map as DiskSkipListMap<String?, String>
        val results = skipMap.above("a", false).map { skipMap.getWithRecID(it) }
        assertEquals(listOf("bee"), results)
        store.close()
    }
}

