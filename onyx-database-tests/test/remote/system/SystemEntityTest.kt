package remote.system

import category.RemoteServerTests
import com.onyx.entity.SystemEntity
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException

/**
 * Created by timothy.osborn on 3/7/15.
 */
@Category(RemoteServerTests::class)
class SystemEntityTest : RemoteBaseTest() {

    @Before
    @Throws(InitializationException::class, InterruptedException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
        RemoteBaseTest.databaseServer!!.stop()
    }

    @Test
    @Throws(OnyxException::class)
    fun testQuerySystemEntities() {
        val query = Query()
        query.entityType = SystemEntity::class.java
        query.criteria = QueryCriteria("name", QueryCriteriaOperator.NOT_NULL)

        val results = manager!!.executeQuery<SystemEntity>(query)
        Assert.assertTrue(results.size > 0)
    }
}
