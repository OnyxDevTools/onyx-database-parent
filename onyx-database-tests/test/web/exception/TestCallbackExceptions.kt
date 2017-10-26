package web.exception

import category.WebServerTests
import com.onyx.exception.EntityCallbackException
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import entities.exception.EntityCallbackExceptionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import web.base.BaseTest

import java.io.IOException


/**
 * Created by timothy.osborn on 2/10/15.
 */
@Category(WebServerTests::class)
class TestCallbackExceptions : BaseTest() {
    /**
     * Created by timothy.osborn on 12/14/14.
     */

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

    @get:Synchronized private var z = 0

    @Synchronized private fun increment() {
        z++
    }

    @Test(expected = EntityCallbackException::class)
    @Throws(OnyxException::class)
    fun testPersistCallbackException() {
        val entity = EntityCallbackExceptionEntity()
        manager.saveEntity<IManagedEntity>(entity)
    }

}
