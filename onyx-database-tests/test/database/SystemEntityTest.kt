package database

import category.EmbeddedDatabaseTests
import com.onyx.entity.SystemEntity
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.Query
import database.database.base.DatabaseBaseTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import kotlin.reflect.KClass
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 3/7/15.
 */
@RunWith(Parameterized::class)
class SystemEntityTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun before() {
        initialize()
    }

    @After
    fun after() {
        shutdown()
    }

    @Test
    fun testQuerySystemEntities() {
        val query = Query(SystemEntity::class.java)
        val results = manager.executeQuery<SystemEntity>(query)
        assertTrue(results.isNotEmpty(), "System Entities should exist")
    }
}
