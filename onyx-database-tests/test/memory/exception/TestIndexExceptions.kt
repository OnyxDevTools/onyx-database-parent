package memory.exception

import category.InMemoryDatabaseTests
import com.onyx.exception.InitializationException
import entities.exception.InvalidIndexTypeEntity
import org.junit.After
import org.junit.Before
import org.junit.experimental.categories.Category

import java.io.IOException

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category(InMemoryDatabaseTests::class)
class TestIndexExceptions : memory.base.BaseTest() {

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
