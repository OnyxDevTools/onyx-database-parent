package database.exceptions

import com.onyx.exception.*
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.ValidationEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass


/**
 * Created by timothy.osborn on 1/21/15.
 * Updated by Chris Osborn on 5/15/15
 */
@RunWith(Parameterized::class)
class TestQueryValidation(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = AttributeNonNullException::class)
    fun testNullValue() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("requiredString", null))
        manager.executeUpdate(updateQuery)
    }

    @Test(expected = AttributeMissingException::class)
    fun testAttributeMissing() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("booger", null))
        manager.executeUpdate(updateQuery)
    }

    @Test(expected = AttributeSizeException::class)
    fun testAttributeSizeException() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("maxSizeString", "12345678901"))
        manager.executeUpdate(updateQuery)

    }

    @Test(expected = AttributeUpdateException::class)
    fun testUpdateIdentifier() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("id", 5L))
        manager.executeUpdate(updateQuery)
    }

    @Test(expected = OnyxException::class)
    fun testMissingEntityTypeException() {
        val criteria = QueryCriteria("name", QueryCriteriaOperator.NOT_NULL)
        val query = Query()
        query.criteria = criteria
        manager.executeQuery<Any>(query)
    }
}
