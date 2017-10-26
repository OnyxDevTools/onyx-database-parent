package memory.exception


import category.InMemoryDatabaseTests
import com.onyx.exception.*
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import entities.ValidationEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException

/**
 * Created by timothy.osborn on 1/21/15.
 * Updated by Chris Osborn on 5/15/15
 */
@Category(InMemoryDatabaseTests::class)
class TestQueryValidation : memory.base.BaseTest() {

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

    @Test(expected = AttributeNonNullException::class)
    @Throws(OnyxException::class)
    fun testNullValue() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("requiredString", null))
        manager.executeUpdate(updateQuery)
    }


    @Test(expected = AttributeMissingException::class)
    @Throws(OnyxException::class)
    fun testAttributeMissing() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("booger", null))
        manager.executeUpdate(updateQuery)
    }

    @Test(expected = AttributeSizeException::class)
    @Throws(OnyxException::class)
    fun testAttributeSizeException() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("maxSizeString", "12345678901"))
        manager.executeUpdate(updateQuery)

    }

    @Test(expected = AttributeUpdateException::class)
    @Throws(OnyxException::class)
    fun testUpdateIdentifier() {
        val updateQuery = Query(ValidationEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L), AttributeUpdate("id", 5L))
        manager.executeUpdate(updateQuery)
    }

    @Test(expected = OnyxException::class)
    @Throws(OnyxException::class)
    fun testMissingEntityTypeException() {
        val criteria = QueryCriteria("name", QueryCriteriaOperator.NOT_NULL)
        val query = Query()
        // query.setEntityType(SystemEntity.class); //should throw error because this line is missing
        query.criteria = criteria
        manager.executeQuery<Any>(query)
    }

}
