package embedded.diskmap

import category.EmbeddedDatabaseTests
import org.junit.BeforeClass
import org.junit.experimental.categories.Category

import java.io.File

/**
 * Created by timothy.osborn on 3/21/15.
 */
@Category(EmbeddedDatabaseTests::class)
open class AbstractTest {
    companion object {

        val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/hiya.db"


        @BeforeClass
        fun beforeTest() {
            val testDataBase = File(TEST_DATABASE)
            if (testDataBase.exists()) {
                testDataBase.delete()
            }
        }
    }

}
