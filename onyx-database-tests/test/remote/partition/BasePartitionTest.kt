package remote.partition

import category.RemoteServerTests
import com.onyx.exception.InitializationException
import org.junit.After
import org.junit.Before
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category(RemoteServerTests::class)
open class BasePartitionTest : RemoteBaseTest() {

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

}
