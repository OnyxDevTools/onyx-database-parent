package database

import com.onyx.entity.SystemEntity
import com.onyx.persistence.query.Query
import database.base.DatabaseBaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import kotlin.reflect.KClass
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 3/7/15.
 */
@RunWith(Parameterized::class)
class SystemEntityTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testQuerySystemEntities() {
        val query = Query(SystemEntity::class.java)
        val results = manager.executeQuery<SystemEntity>(query)
        assertTrue(results.isNotEmpty(), "System Entities should exist")
    }
}
