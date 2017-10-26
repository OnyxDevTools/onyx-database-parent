package remote.list

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeForFetch
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException
import java.util.Date

/**
 * Created by timothy.osborn on 11/6/14.
 */
@Category(RemoteServerTests::class)
class FullTableScanTest : RemoteBaseTest() {

    @Before
    @Throws(InitializationException::class)
    fun seedData() {
        initialize()

        var entity = AllAttributeForFetch()
        entity.id = "FIRST ONE"
        entity.stringValue = "Some test strin"
        entity.dateValue = Date(1000)
        entity.doublePrimitive = 3.3
        entity.doubleValue = 1.1
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1000L
        entity.longValue = 323L
        entity.intValue = 3
        entity.intPrimitive = 3
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE1"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE2"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin2"
        entity.dateValue = Date(1002)
        entity.doublePrimitive = 3.32
        entity.doubleValue = 1.12
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1001L
        entity.longValue = 321L
        entity.intValue = 5
        entity.intPrimitive = 6
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin3"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        entity.intValue = 6
        entity.intPrimitive = 3
        save(entity)
        find(entity)

        entity.longValue = 23L

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        save(entity)
        find(entity)
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testBasicAnd() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
                .and("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some test str")
                .and("id", QueryCriteriaOperator.EQUAL, "FIRST ONE1")

        val results = manager!!.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(1, results.size.toLong())
        Assert.assertEquals(results[0].stringValue, "Some test strin1")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testBasicAndOr() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
                .or("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")

        val results = manager!!.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(4, results.size.toLong())
    }


    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testBasicOrsSub() {
        val containsSubTes = QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some tes")
        val containsSubTestStrin1 = QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some test strin1")

        Assert.assertEquals(5, manager!!.list<IManagedEntity>(AllAttributeForFetch::class.java, containsSubTes.or(containsSubTestStrin1)).size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testBasicAndSub() {
        val stringValueEqualsValue = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        val containsSubTes = QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some tes")
        val intValueNotEqual2 = QueryCriteria("intValue", QueryCriteriaOperator.NOT_EQUAL, 2)
        val orStringValueEqualsSomeTest = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin2")

        Assert.assertEquals(2, manager!!.list<IManagedEntity>(AllAttributeForFetch::class.java, stringValueEqualsValue.and(containsSubTes.or(intValueNotEqual2)).or(orStringValueEqualsSomeTest)).size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testBasicOrSub() {
        val stringValueEqualsValue = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        val containsSubTes = QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some tes")
        val intValueNotEqual2 = QueryCriteria("intValue", QueryCriteriaOperator.NOT_EQUAL, 2)
        val orStringValueEqualsSomeTest = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin2")

        Assert.assertEquals(9, manager!!.list<IManagedEntity>(AllAttributeForFetch::class.java, stringValueEqualsValue.or(containsSubTes.or(intValueNotEqual2)).or(orStringValueEqualsSomeTest)).size.toLong())
    }

}
