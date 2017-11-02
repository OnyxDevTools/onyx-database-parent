package diskmap

import database.base.DatabaseBaseTest
import org.junit.BeforeClass

/**
 * Created by timothy.osborn on 3/21/15.
 */
open class AbstractTest {
    companion object {

        val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/mapTest.db"

        @BeforeClass
        @JvmStatic
        fun beforeTest() = DatabaseBaseTest.deleteDatabase(TEST_DATABASE)
    }
}
