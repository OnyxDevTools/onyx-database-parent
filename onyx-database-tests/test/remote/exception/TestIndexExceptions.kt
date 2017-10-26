package remote.exception

import category.RemoteServerTests
import com.onyx.exception.InitializationException
import entities.exception.InvalidIndexTypeEntity
import org.junit.After
import org.junit.Before
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category(RemoteServerTests::class)
class TestIndexExceptions : RemoteBaseTest() {

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
