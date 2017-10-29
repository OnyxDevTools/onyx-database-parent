package database.list

import com.onyx.extension.notNull
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class EmptyCriteriaTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    /**
     * The purpose of this test is to verify that the empty criteria gives results and lists all of the entities of a certain
     * type
     */
    @Test
    fun testEmptyCriteria() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java)
        assertEquals(6, results.size.toLong(), "Expected 6 AllAttributeForFetch entities")
    }

    /**
     * This test validates that the not null criteria operator is valid to use against a primitive type.
     */
    @Test
    fun testEmptyCriteriaOnInt() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "intPrimitive".notNull())
        assertEquals(6, results.size,"Expected 6 AllAttributeForFetch entities")
    }
}