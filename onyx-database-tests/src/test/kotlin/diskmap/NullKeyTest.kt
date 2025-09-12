package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import database.base.DatabaseBaseTest
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertFailsWith

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
    fun putNullKeyThrows() {
        val store = DefaultDiskMapFactory(TEST_DATABASE)
        val map: MutableMap<String?, String> = store.getHashMap(String::class.java, "nullKeyMap")
        assertFailsWith<NullPointerException> { map[null] = "nil" }
        store.close()
    }
}
