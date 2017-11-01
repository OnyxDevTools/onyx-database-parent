package diskmap

import org.junit.BeforeClass

import java.io.File

/**
 * Created by timothy.osborn on 3/21/15.
 */
open class AbstractTest {
    companion object {

        val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/hiya.db"

        @BeforeClass
        @JvmStatic
        fun beforeTest() {
            val testDataBase = File(TEST_DATABASE)
            if (testDataBase.exists()) {
                testDataBase.delete()
            }
        }
    }

}
