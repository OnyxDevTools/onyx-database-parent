package database.list

import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.AllAttributeForFetchSequenceGen
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class IndexListTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {
        manager.from(AllAttributeForFetchSequenceGen::class).delete()
        var entity: AllAttributeForFetchSequenceGen
        val list = ArrayList<AllAttributeForFetchSequenceGen>()
        for (i in 1..5000) {
            entity = AllAttributeForFetchSequenceGen()
            entity.id = i.toLong()
            entity.indexVal = i
            list.add(entity)
        }
        manager.saveEntities(list)
    }

    @Test
    fun testIdentifierRange() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("indexVal" gt 2500) and ("indexVal" lt 3000))
        assertEquals(499, results.size, "Results should have 499 entities")
    }

    @Test
    fun testIdentifierRangeLTEqual() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("indexVal" gt 2500) and ("indexVal" lte 3000))
        assertEquals(500, results.size, "Results should have 500 entities")
    }

    @Test
    fun testIdentifierRangeEqual() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("indexVal" gte 101) and ("indexVal" lte 1000))
        assertEquals(900, results.size, "Results should have 5000 entities")
    }

    @Test
    fun testIdentifierGreaterThan() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("indexVal" gt 4000))
        assertEquals(1000, results.size, "Results should have 1000 entities")
    }

    @Test
    fun testIdentifierLessThanNoResults() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("indexVal" lt 1))
        assertEquals(0, results.size, "Results should have no entities")
    }

    @Test
    fun testIdentifierGreaterThanNoResults() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("indexVal" gt 5000))
        assertEquals(0, results.size, "Results should have no entities")
    }

}
