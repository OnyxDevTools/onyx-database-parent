package embedded.partition

import category.EmbeddedDatabaseTests
import com.onyx.exception.InitializationException
import embedded.base.BaseTest
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.experimental.categories.Category

import java.io.IOException

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category(EmbeddedDatabaseTests::class)
open class BasePartitionTest : BaseTest() {

    @Before
    @Throws(InitializationException::class, InterruptedException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            BaseTest.deleteDatabase()
        }
    }

}
