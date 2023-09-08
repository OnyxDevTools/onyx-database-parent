package database.list

import com.onyx.persistence.query.between
import com.onyx.persistence.query.from
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class BetweenTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    // TODO: Test ID,
    // TODO: Test Full table scan
    // TODO: Test Index
    // TODO: Test Partition Index

    @Test
    @Ignore
    fun testStringIDGreaterThanEqual() {
        val results = manager.from<AllAttributeForFetch>()
               .where("id" between Pair("FIRST", "LAST"))
               .list<AllAttributeForFetch>()
        assertEquals(5, results.size, "Expected 5 results from query")
    }
}