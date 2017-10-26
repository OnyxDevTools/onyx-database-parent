package embedded.exception

import category.EmbeddedDatabaseTests
import com.onyx.exception.InitializationException
import embedded.base.BaseTest
import entities.exception.InvalidIndexTypeEntity
import org.junit.After
import org.junit.Before
import org.junit.experimental.categories.Category

import java.io.IOException

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category(EmbeddedDatabaseTests::class)
class TestIndexExceptions : BaseTest() {

    @Before
    @Throws(InitializationException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }
}
