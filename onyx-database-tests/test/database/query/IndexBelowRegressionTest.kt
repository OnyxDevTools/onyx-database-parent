package database.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.from
import com.onyx.persistence.query.lt
import database.base.PrePopulatedDatabaseTest
import entities.PageAnalytic
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.Math.abs
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class IndexBelowRegressionTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    /**
     * This test tests a regression with the Kotlin refactor.  The loadFactor caused the map builder
     * to create a DiskHashMap that had a bug in the below method whereas it did not set the map head
     * and caused things to crap out
     */
    @Test
    fun testDateIndexSelect() {
        for (i in 0..1000) {
            val item = PageAnalytic()
            item.path = "asdf/asdf/asdf" + i

            item.requestDate = Date(abs(randomInteger.toLong()) + 1513000000000)
            println(item.requestDate)
            manager.saveEntity(item)
        }

        val dateFormat = SimpleDateFormat("MM-dd-yyyy");
        val endDate = dateFormat.parse("12-31-2023")
        assertEquals(1001, manager.from(PageAnalytic::class).where("requestDate" lt endDate).list<IManagedEntity>().size)
    }
}