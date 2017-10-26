package web.partition

import category.WebServerTests
import com.onyx.exception.InitializationException
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.experimental.categories.Category
import web.base.BaseTest

import java.io.IOException

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category(WebServerTests::class)
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
        @BeforeClass
        fun beforeClass() {
            BaseTest.deleteDatabase()
        }
    }

}
