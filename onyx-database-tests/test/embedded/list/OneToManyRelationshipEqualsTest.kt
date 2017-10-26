package embedded.list

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import embedded.base.BaseTest
import entities.OneToManyChildFetchEntity
import entities.OneToOneFetchEntity
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.ArrayList
import java.util.Date

@Category(EmbeddedDatabaseTests::class)
class OneToManyRelationshipEqualsTest : BaseTest() {
    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(InitializationException::class)
    fun seedData() {
        initialize()

        var entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE"
        entity.stringValue = "Some test strin"
        entity.dateValue = Date(1000)
        entity.doublePrimitive = 3.3
        entity.doubleValue = 1.1
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1000L
        entity.longValue = 323L
        save(entity)
        find(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE1"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        save(entity)
        find(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE2"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        save(entity)
        find(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin2"
        entity.dateValue = Date(1002)
        entity.doublePrimitive = 3.32
        entity.doubleValue = 1.12
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1001L
        entity.longValue = 321L
        save(entity)
        find(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin3"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        save(entity)
        find(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE4"
        save(entity)
        find(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE5"
        save(entity)
        find(entity)


        var entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE"
        entity2.stringValue = "Some test strin"
        entity2.dateValue = Date(1000)
        entity2.doublePrimitive = 3.3
        entity2.doubleValue = 1.1
        entity2.booleanValue = false
        entity2.booleanPrimitive = true
        entity2.longPrimitive = 1000L
        entity2.longValue = 323L
        save(entity2)
        find(entity2)

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE1"
        save(entity2)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE1"
        entity2.stringValue = "Some test strin1"
        entity2.dateValue = Date(1001)
        entity2.doublePrimitive = 3.31
        entity2.doubleValue = 1.11
        entity2.booleanValue = true
        entity2.booleanPrimitive = false
        entity2.longPrimitive = 1002L
        entity2.longValue = 322L
        save(entity2)
        find(entity2)

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE2"
        save(entity2)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE2"
        entity2.stringValue = "Some test strin1"
        entity2.dateValue = Date(1001)
        entity2.doublePrimitive = 3.31
        entity2.doubleValue = 1.11
        entity2.booleanValue = true
        entity2.booleanPrimitive = false
        entity2.longPrimitive = 1002L
        entity2.longValue = 322L
        save(entity2)
        find(entity2)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE3"
        entity2.stringValue = "Some test strin2"
        entity2.dateValue = Date(1002)
        entity2.doublePrimitive = 3.32
        entity2.doubleValue = 1.12
        entity2.booleanValue = true
        entity2.booleanPrimitive = false
        entity2.longPrimitive = 1001L
        entity2.longValue = 321L
        save(entity2)
        find(entity2)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE3"
        entity2.stringValue = "Some test strin3"
        entity2.dateValue = Date(1022)
        entity2.doublePrimitive = 3.35
        entity2.doubleValue = 1.126
        entity2.booleanValue = false
        entity2.booleanPrimitive = true
        entity2.longPrimitive = 1301L
        entity2.longValue = 322L
        save(entity2)
        find(entity2)

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE2"
        save(entity2)

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE3"
        save(entity2)
        find(entity2)

        find(entity2.parents!!)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE4"
        save(entity2)
        find(entity2)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE5"
        save(entity2)
        find(entity2)

    }

    @Test
    @Throws(OnyxException::class)
    fun testOneToOneHasRelationshipMeetsOne() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")
                .and("children.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3")

        val time = System.currentTimeMillis()
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, criteria)
        val done = System.currentTimeMillis()

        Assert.assertEquals(1, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class)
    fun testOneToOneHasRelationship() {

        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some test strin")
                .and("children.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3")

        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, criteria)

        Assert.assertEquals(1, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class)
    fun testOneToOneNoMeetCriteriaRelationship() {

        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some te1st strin3")
                .and("children.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3")

        val time = System.currentTimeMillis()
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, criteria)
        val done = System.currentTimeMillis()

        Assert.assertEquals(0, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class)
    fun testOneToManyInCriteriaRelationship() {
        val idlist = ArrayList<Any>()
        idlist.add("FIRST ONE3")
        idlist.add("FIRST ONE2")

        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some test strin1")
                .and("children.id", QueryCriteriaOperator.IN, idlist)

        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, criteria)
        Assert.assertEquals(0, results.size.toLong())
    }
}