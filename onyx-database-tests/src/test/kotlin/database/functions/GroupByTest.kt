package database.functions

import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.AllAttributeEntity
import entities.AllAttributeEntityWithRelationship
import entities.NullableGetterStarter
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
        manager.from(AllAttributeEntityWithRelationship::class).delete()

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
        entity.longPrimitive = 9L
        entity.stringValue = "99"
        entity.id = "12"
        manager.saveEntity(entity)
    }

    @Test
    fun testGroupBy() {
        val results = manager.select("longPrimitive", "lower(stringValue)")
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("longPrimitive", "stringValue")
                .orderBy("id".desc())
                .list<Map<String, Any?>>()

        assertEquals(11, results.size, "Only 11 results should have been returned")
        assertEquals("some other value", results[0]["lower(stringValue)"], "Invalid result")
    }

    @Test
    fun testMin() {
        val results = manager.select("stringValue", min("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertEquals(9L, results.first { it["stringValue"] == "9" }["min(longPrimitive)"] , "9 should be the min result")
    }

    @Test
    fun testGroupByMember() {
        val results = manager.select("stringValue", min("longPrimitive"))
            .from(AllAttributeEntityWithRelationship::class)
            .groupBy("stringValueGetter")
            .list<Map<String, Any?>>()

        assertEquals(9L, results.first { it["stringValue"] == "9" }["min(longPrimitive)"] , "9 should be the min result")
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

        assertEquals(157L, results.first { it["relationship.id"] == "HI5" }["sum(longPrimitive)"] , "148L should be the sum result")
        assertEquals(40, results.first { it["relationship.id"] == "HI5"}["sum(relationship.intValue)"] , "Relationship intValue is invalid")
    }

    @Test
    fun testGroupOnRelationshipOrderByCount() {
        val results = manager.select("relationship.id", "longPrimitive", sum("relationship.intValue"), sum("longPrimitive"), avg("longPrimitive"), count("longPrimitive"), max("doublePrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("relationship.id")
                .orderBy(sum("relationship.intValue").desc())
                .list<Map<String, Any?>>()

        assertEquals(157L, results.first { it["relationship.id"] == "HI5" }["sum(longPrimitive)"] , "148L should be the sum result")
        assertEquals(40, results.first { it["relationship.id"] == "HI5"}["sum(relationship.intValue)"] , "Relationship intValue is invalid")
        assertEquals(results.first(), results.first { it["relationship.id"] == "HI5" }, "Failure to sort")
    }

    @Test
    fun testAggregateOnNullableEntityGetterAttribute() {
        manager.from(NullableGetterStarter::class).delete()

        listOf(
            NullableGetterStarter("1", "A", hasPerformance = true, finishPosition = 2),
            NullableGetterStarter("2", "A", hasPerformance = true, finishPosition = 10),
            NullableGetterStarter("3", "A", hasPerformance = true, finishPosition = null),
            NullableGetterStarter("4", "A", hasPerformance = false, finishPosition = 2),
            NullableGetterStarter("5", "B", hasPerformance = true, finishPosition = 4),
        ).forEach(manager::save)

        val totals = manager
            .select(
                avg("performance.finishPosition"),
                sum("performance.finishPosition"),
                min("performance.finishPosition"),
                max("performance.finishPosition"),
                median("performance.finishPosition"),
                count("starterId"),
            )
            .from<NullableGetterStarter>()
            .where("performance.finishPosition".notNull())
            .first<Map<String, Any?>>()

        assertEquals(5, totals["avg(performance.finishPosition)"])
        assertEquals(16, totals["sum(performance.finishPosition)"])
        assertEquals(2, totals["min(performance.finishPosition)"])
        assertEquals(10, totals["max(performance.finishPosition)"])
        assertEquals(4, totals["median(performance.finishPosition)"])
        assertEquals(3, totals["count(starterId)"])

        val results = manager
            .select(
                avg("performance.finishPosition"),
                sum("performance.finishPosition"),
                min("performance.finishPosition"),
                max("performance.finishPosition"),
                std("performance.finishPosition"),
                variance("performance.finishPosition"),
                median("performance.finishPosition"),
                percentile("performance.finishPosition", 50.0),
                "medication",
                count("starterId"),
            )
            .from<NullableGetterStarter>()
            .where("performance.finishPosition".notNull())
            .groupBy("medication")
            .orderBy(avg("performance.finishPosition").desc())
            .list<Map<String, Any?>>()

        assertEquals(2, results.size)
        assertEquals("A", results.first()["medication"])

        val medicationA = results.first { it["medication"] == "A" }
        assertEquals(6, medicationA["avg(performance.finishPosition)"])
        assertEquals(12, medicationA["sum(performance.finishPosition)"])
        assertEquals(2, medicationA["min(performance.finishPosition)"])
        assertEquals(10, medicationA["max(performance.finishPosition)"])
        assertEquals(4.0, medicationA["std(performance.finishPosition)"])
        assertEquals(16.0, medicationA["variance(performance.finishPosition)"])
        assertEquals(6, medicationA["median(performance.finishPosition)"])
        assertEquals(6, medicationA["percentile(performance.finishPosition, 50.0)"])
        assertEquals(2, medicationA["count(starterId)"])

        val medicationB = results.first { it["medication"] == "B" }
        assertEquals(4, medicationB["avg(performance.finishPosition)"])
        assertEquals(4, medicationB["sum(performance.finishPosition)"])
        assertEquals(4, medicationB["min(performance.finishPosition)"])
        assertEquals(4, medicationB["max(performance.finishPosition)"])
        assertEquals(0.0, medicationB["std(performance.finishPosition)"])
        assertEquals(0.0, medicationB["variance(performance.finishPosition)"])
        assertEquals(4, medicationB["median(performance.finishPosition)"])
        assertEquals(4, medicationB["percentile(performance.finishPosition, 50.0)"])
        assertEquals(1, medicationB["count(starterId)"])
    }

    @Test
    fun testCountDistinctPreservesNumericStringIdentity() {
        manager.from(NullableGetterStarter::class).delete()

        manager.save(NullableGetterStarter(starterId = "01"))
        manager.save(NullableGetterStarter(starterId = "1"))

        val result = manager
            .select(count("starterId"))
            .from<NullableGetterStarter>()
            .distinct()
            .first<Map<String, Any?>>()

        assertEquals(2, result["count(starterId)"])
    }
}
