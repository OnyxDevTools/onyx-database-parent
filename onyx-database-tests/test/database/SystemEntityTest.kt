package database

import com.onyx.entity.SystemEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import database.base.DatabaseBaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

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

    @Test
    fun testQueryLatestVersionSystemEntities() {
        val query = Query()
        query.entityType = SystemEntity::class.java
        query.criteria = QueryCriteria("isLatestVersion", QueryCriteriaOperator.EQUAL, true)
        query.queryOrders = Arrays.asList(QueryOrder("name", true))

        val results = manager.executeQuery<SystemEntity>(query)
        assertTrue(results.isNotEmpty(), "Query should have rendered results")
    }
}
