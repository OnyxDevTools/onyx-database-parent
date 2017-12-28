package database.functions

import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.AllAttributeEntity
import entities.AllAttributeEntityWithRelationship
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class GroupByTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun insertTestData() {
        val entity = AllAttributeEntityWithRelationship()

        entity.longPrimitive = 2L
        entity.stringValue = "2"
        entity.doublePrimitive = 2.0
        entity.id = "1"
        entity.relationship = AllAttributeEntity()
        entity.relationship!!.id = "HI"
        entity.relationship!!.intValue = 3
        manager.saveEntity(entity)
        entity.doublePrimitive = 0.0
        entity.longPrimitive = 3L
        entity.stringValue = "3"
        entity.id = "2"
        entity.relationship!!.id = "HI2"
        entity.relationship!!.intValue = 3
        manager.saveEntity(entity)
        entity.longPrimitive = 4L
        entity.stringValue = "4"
        entity.id = "3"
        entity.relationship!!.id = "HI3"
        entity.relationship!!.intValue = 4
        manager.saveEntity(entity)
        entity.longPrimitive = 5L
        entity.stringValue = "5"
        entity.id = "4"
        entity.relationship!!.id = "HI4"
        entity.relationship!!.intValue = 4
        manager.saveEntity(entity)
        entity.longPrimitive = 6L
        entity.stringValue = "6"
        entity.id = "5"
        entity.relationship!!.id = "HI5"
        entity.relationship!!.intValue = 5
        manager.saveEntity(entity)
        entity.longPrimitive = 7L
        entity.stringValue = "7"
        entity.id = "6"
        manager.saveEntity(entity)
        entity.longPrimitive = 8L
        entity.stringValue = "8"
        entity.id = "7"
        manager.saveEntity(entity)
        entity.longPrimitive = 9L
        entity.stringValue = "9"
        entity.id = "8"
        manager.saveEntity(entity)
        entity.longPrimitive = 10L
        entity.stringValue = "Some other Value"
        entity.id = "9"
        manager.saveEntity(entity)
        entity.longPrimitive = 99L
        entity.stringValue = "9"
        entity.id = "10"
        manager.saveEntity(entity)
        entity.longPrimitive = 9L
        entity.stringValue = "99"
        entity.id = "11"
        manager.saveEntity(entity)

    }

    @Test
    fun testGroupBy() {
        val results = manager.select("longPrimitive", "stringValue")
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("longPrimitive", "stringValue")
                .list<Map<Any?, List<Any?>?>>()

        assertEquals(2, results[1][9L]!!.count(), "There should have been 2 grouped by 9 longPrimitiveValue results")
        assertEquals(2, results[0]["9"]!!.count(), "There should have been 2 grouped by 9 stringValue results")
    }

    @Test
    fun testMin() {
        val results = manager.select("stringValue", min("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(9L, results.first { it["stringValue"] == "9" }["min(longPrimitive)"] , "99 should be the max result")
    }

    @Test
    fun testMax() {
        val results = manager.select("stringValue", max("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(99L, results.first { it["stringValue"] == "9" }["max(longPrimitive)"] , "99 should be the max result")

    }

    @Test
    fun testAvg() {
        val results = manager.select("stringValue", avg("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(54L, results.first { it["stringValue"] == "9" }["avg(longPrimitive)"] , "2 should be the avg result")

    }

    @Test
    fun testCount() {
        val results = manager.select("stringValue", count("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(2, results.first { it["stringValue"] == "9" }["count(longPrimitive)"] , "2 should be the count result")

    }

    @Test
    fun testSum() {
        val results = manager.select("stringValue", sum("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(108L, results.first { it["stringValue"] == "9" }["sum(longPrimitive)"] , "108 should be the sum result")
    }

    @Test
    fun testMultipleFunctions() {
        val results = manager.select("stringValue", sum("longPrimitive"), avg("longPrimitive"), count("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(108L, results.first { it["stringValue"] == "9" }["sum(longPrimitive)"] , "108 should be the sum result")
        assertEquals(54L, results.first { it["stringValue"] == "9" }["avg(longPrimitive)"] , "2 should be the avg result")
        assertEquals(2, results.first { it["stringValue"] == "9" }["count(longPrimitive)"] , "2 should be the count result")
    }

    @Test
    fun testMultipleFunctionsForFields() {
        val results = manager.select("stringValue", "longPrimitive", sum("longPrimitive"), avg("longPrimitive"), count("longPrimitive"), max("doublePrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(108L, results.first { it["stringValue"] == "9" }["sum(longPrimitive)"] , "108 should be the sum result")
        assertEquals(54L, results.first { it["stringValue"] == "9" }["avg(longPrimitive)"] , "2 should be the avg result")
        assertEquals(2, results.first { it["stringValue"] == "9" }["count(longPrimitive)"] , "2 should be the count result")
        assertEquals(2.0, results.first { it["stringValue"] == "2" }["max(doublePrimitive)"] , "2.0 should be the value of max(doublePrimitive)")
    }

    @Test
    fun testGroupOnRelationship() {
        val results = manager.select("relationship.id", "longPrimitive", sum("relationship.intValue"), sum("longPrimitive"), avg("longPrimitive"), count("longPrimitive"), max("doublePrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("relationship.id")
                .list<Map<String, Any?>>()

        assertEquals(148L, results.first { it["relationship.id"] == "HI5" }["sum(longPrimitive)"] , "148L should be the sum result")
        assertEquals(35, results.first { it["relationship.id"] == "HI5"}["sum(relationship.intValue)"] , "Relationship intValue is invalid")
    }

    @Test
    fun testGroupOnRelationshipOrderByCount() {
        val results = manager.select("relationship.id", "longPrimitive", sum("relationship.intValue"), sum("longPrimitive"), avg("longPrimitive"), count("longPrimitive"), max("doublePrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("relationship.id")
                .orderBy(sum("relationship.intValue").desc())
                .list<Map<String, Any?>>()

        assertEquals(148L, results.first { it["relationship.id"] == "HI5" }["sum(longPrimitive)"] , "148L should be the sum result")
        assertEquals(35, results.first { it["relationship.id"] == "HI5"}["sum(relationship.intValue)"] , "Relationship intValue is invalid")
        assertEquals(results.first(), results.first { it["relationship.id"] == "HI5" }, "Failure to sort")
    }
}