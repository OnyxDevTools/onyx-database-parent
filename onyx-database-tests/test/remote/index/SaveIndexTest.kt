package remote.index

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import entities.identifiers.StringIdentifierEntity
import entities.index.StringIdentifierEntityIndex
import org.junit.*
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException
import java.util.Arrays

/**
 * Created by timothy.osborn on 1/23/15.
 */
@Category(RemoteServerTests::class)
class SaveIndexTest : RemoteBaseTest() {

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(InitializationException::class)
    fun before() {
        initialize()
    }

    @Test
    @Throws(OnyxException::class)
    fun saveStringIndexUpdateTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        save(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager!!.executeQuery<StringIdentifierEntity>(findQuery)

        Assert.assertTrue(results.size == 1)

        entity.indexValue = "BLA"
        save(entity)

        results = manager!!.executeQuery(findQuery)
        Assert.assertTrue(results.size == 0)

        val findQuery2 = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "BLA"))
        results = manager!!.executeQuery(findQuery2)

        Assert.assertTrue(results.size == 1)

    }

    @Test
    @Throws(OnyxException::class)
    fun saveStringIndexDeleteTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        save(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager!!.executeQuery<StringIdentifierEntity>(findQuery)

        Assert.assertTrue(results.size == 1)

        entity.indexValue = "BLA"
        delete(entity)

        results = manager!!.executeQuery(findQuery)
        Assert.assertTrue(results.size == 0)

    }

    @Test
    @Throws(OnyxException::class)
    fun saveStringIndexDeleteQueryTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        save(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager!!.executeQuery<StringIdentifierEntity>(findQuery)

        Assert.assertTrue(results.size == 1)

        val deleteQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        manager!!.executeDelete(deleteQuery)

        results = manager!!.executeQuery(findQuery)
        Assert.assertTrue(results.size == 0)

    }

    @Test
    @Throws(OnyxException::class)
    fun saveStringIndexUpdateQueryTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        save(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager!!.executeQuery<StringIdentifierEntity>(findQuery)

        Assert.assertTrue(results.size == 1)

        val updateQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"), AttributeUpdate("indexValue", "HIYA"))
        manager!!.executeUpdate(updateQuery)

        results = manager!!.executeQuery(findQuery)
        Assert.assertTrue(results.size == 0)


        val updatedFindQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "HIYA"))

        results = manager!!.executeQuery(updatedFindQuery)
        Assert.assertTrue(results.size == 1)

    }

    @Test
    @Throws(OnyxException::class)
    fun testSaveWithExistingFullScanPrior() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        save(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        val results = manager!!.executeQuery<StringIdentifierEntity>(findQuery)

        Assert.assertTrue(results.size == 1)

    }

    @Test
    @Throws(OnyxException::class)
    fun testSaveWithExistingFullScanPriorWitIn() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        save(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.IN, Arrays.asList("INDEX VALUE")))
        val results = manager!!.executeQuery<StringIdentifierEntity>(findQuery)

        Assert.assertTrue(results.size == 1)

    }
}
