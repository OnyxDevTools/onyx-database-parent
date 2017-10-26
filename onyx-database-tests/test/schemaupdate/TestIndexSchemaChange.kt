package schemaupdate

import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import embedded.base.BaseTest
import entities.schema.SchemaIndexChangedEntity
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Created by Tim Osborn on 9/1/15.
 */
@Ignore
class TestIndexSchemaChange : BaseTest() {
    @Before
    @Throws(Exception::class)
    fun startup() {
        this.initialize()
    }

    @After
    @Throws(Exception::class)
    fun teardown() {
        this.shutdown()
    }

    @Test
    @Throws(Exception::class)
    fun deleteData() {
        this.shutdown()
        Thread.sleep(2000)
        BaseTest.deleteDatabase()
    }

    /**
     * Initialize test by inserting dummy record
     *
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun initializeTest() {

        val entity = SchemaIndexChangedEntity()
        entity.otherIndex = 1
        //        entity.longValue = 23l;

        save(entity)

        find(entity)

        Assert.assertTrue(entity.id == 1L)
        //        Assert.assertTrue(entity.longValue == 23l);
    }

    /**
     * Test change the index type from a long to a String
     *
     * PRE - Change longValue attribute to String within SchemaIndexChangedEntity
     *
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testChangeIndexType() {

        val entity = SchemaIndexChangedEntity()
        entity.id = 1
        find(entity)

        // Wait to re-index
        Thread.sleep(2000)

        val query = Query(SchemaIndexChangedEntity::class.java, QueryCriteria("longValue", QueryCriteriaOperator.EQUAL, "23"))
        val results = manager.executeQuery<SchemaIndexChangedEntity>(query)

        Assert.assertTrue(results.size == 1)
        Assert.assertTrue(results[0].longValue == "23")
    }

    /**
     * Test change the index type from a long to a String
     *
     * PRE - Un Comment index field
     *
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testAddIndex() {

        val entity = SchemaIndexChangedEntity()
        entity.id = 1
        find(entity)

        // Wait to re-index
        Thread.sleep(2000)

        val query = Query(SchemaIndexChangedEntity::class.java, QueryCriteria("otherIndex", QueryCriteriaOperator.EQUAL, 1))
        val results = manager.executeQuery<SchemaIndexChangedEntity>(query)

        Assert.assertTrue(results.size == 1)
        Assert.assertTrue(results[0].otherIndex == 1)
    }
}