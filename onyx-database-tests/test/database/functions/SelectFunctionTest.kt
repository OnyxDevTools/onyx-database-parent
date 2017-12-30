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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class SelectFunctionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun insertTestData() {
        val entity = AllAttributeEntityWithRelationship()

        entity.longPrimitive = 2L
        entity.stringValue = "2"
        entity.doublePrimitive = 2.0
        entity.id = "1"
        entity.otherString = "This is a string"
        entity.relationship = AllAttributeEntity()
        entity.relationship!!.id = "HI"
        entity.relationship!!.intValue = 3
        manager.saveEntity(entity)
        entity.doublePrimitive = 0.0
        entity.longPrimitive = 3L
        entity.stringValue = "3"
        entity.id = "2"
        entity.otherString = "This is a string 2"
        entity.relationship!!.id = "HI2"
        entity.relationship!!.intValue = 3
        manager.saveEntity(entity)
        entity.longPrimitive = 4L
        entity.stringValue = "4"
        entity.id = "3"
        entity.relationship!!.id = "HI3"
        entity.otherString = "This is a string 3"
        entity.relationship!!.intValue = 4
        manager.saveEntity(entity)
        entity.longPrimitive = 5L
        entity.stringValue = "5"
        entity.id = "4"
        entity.relationship!!.id = "HI4"
        entity.relationship!!.intValue = 4
        entity.otherString = "This is a string 4"
        manager.saveEntity(entity)
        entity.longPrimitive = 6L
        entity.stringValue = "6"
        entity.id = "5"
        entity.relationship!!.id = "HI5"
        entity.relationship!!.intValue = 5
        entity.otherString = "This is a string 5"
        manager.saveEntity(entity)
        entity.longPrimitive = 7L
        entity.stringValue = "7"
        entity.id = "6"
        entity.otherString = "This is a string 6"
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
        entity.relationship = AllAttributeEntity()
        entity.relationship?.stringValue = "RELTN"
        entity.relationship!!.id = "OTHER"
        manager.saveEntity(entity)
        entity.longPrimitive = 9L
        entity.stringValue = "99"
        entity.id = "11"
        manager.saveEntity(entity)

    }

    @Test
    fun testUpper() {
        val results = manager.select(upper("stringValue"), "stringValue")
                .from(AllAttributeEntityWithRelationship::class)
                .list<Map<Any?, Any?>>()

        val match = results.firstOrNull { it["upper(stringValue)"] == "Some other Value".toUpperCase() }
        assertNotNull(match, "Failure to uppercase")
    }

    @Test
    fun testUpperGroupBy() {
        val results = manager.select(upper("stringValue"), "stringValue")
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy(upper("stringValue"))
                .list<Map<Any?, List<Any?>?>>()

        assertTrue(results.isNotEmpty(), "This query should not have broken even tough I did something stupid")
    }

    @Test
    fun testLower() {
        val results = manager.select(lower("stringValue"), "stringValue")
                .from(AllAttributeEntityWithRelationship::class)
                .list<Map<Any?, Any?>>()

        val match = results.firstOrNull { it["lower(stringValue)"] == "Some other Value".toLowerCase() }
        assertNotNull(match, "Failure to lowercase")
    }

    @Test
    fun testLowerOrderBy() {
        val results = manager.select(lower("stringValue"), "stringValue")
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy(lower("stringValue").desc())
                .list<Map<Any?, Any?>>()

        assertEquals("Some other Value".toLowerCase(), results.first()["lower(stringValue)"], "Failure to sort with function")
    }

    @Test
    fun testLowerOrderByWithoutFunction() {
        val results = manager.select(lower("stringValue"))
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy("stringValue".desc())
                .list<Map<Any?, Any?>>()

        assertEquals("Some other Value".toLowerCase(), results.first()["lower(stringValue)"], "Failure to sort with function")
    }

    @Test
    fun testSubstring() {
        val results = manager.select(substring("otherString", 0,4))
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy("stringValue".desc())
                .list<Map<Any?, Any?>>()

        assertEquals("This", results[0]["substring(otherString, 0, 4)"], "Failure to substring")
    }

    @Test
    fun testReplace() {
        val results = manager.select(replace("otherString", "\\w","z"))
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy("stringValue".desc())
                .list<Map<Any?, Any?>>()

        assertEquals("zzzz zz z zzzzzz z", results[0]["replace(otherString, '\\w', 'z')"], "Failure to string replace")

    }

    @Test
    fun testFlatFunctions() {
        val result = manager.select(count("otherString"), avg("longPrimitive"), max("longPrimitive"), min("longPrimitive"),sum("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .distinct()
                .list<Map<String, Any>>()
                .first()

        assertEquals(14L, result["avg(longPrimitive)"], "Invalid Average")
        assertEquals(162L, result["sum(longPrimitive)"], "Invalid Sum")
        assertEquals(2L, result["min(longPrimitive)"], "Invalid Min")
        assertEquals(6, result["count(otherString)"], "Invalid Count")
        assertEquals(99L, result["max(longPrimitive)"], "Invalid Max")

    }

    @Test
    fun testFlatFunctionsNonDistinct() {
        val result = manager.select("id", count("otherString"), avg("longPrimitive"), max("longPrimitive"), min("longPrimitive"),sum("longPrimitive"))
                .from(AllAttributeEntityWithRelationship::class)
                .list<Map<String, Any>>()
                .first()

        assertEquals(14L, result["avg(longPrimitive)"], "Invalid Average")
        assertEquals(162L, result["sum(longPrimitive)"], "Invalid Sum")
        assertEquals(2L, result["min(longPrimitive)"], "Invalid Min")
        assertEquals(11, result["count(otherString)"], "Invalid Count")
        assertEquals(99L, result["max(longPrimitive)"], "Invalid Max")

    }

    @Test
    fun testFlatFunctionsForSingleMax() {
        val result = manager.select(max("longPrimitive"), "stringValue")
                .from(AllAttributeEntityWithRelationship::class)
                .list<Map<String, Any?>>().first()

        assertEquals("9", result["stringValue"], "String value did not return as expected")
    }

    @Test
    fun testFlatFunctionsForSingleMaxWithRelationshipValue() {
        val result = manager.select(max("longPrimitive"), "stringValue", "relationship.stringValue")
                .from(AllAttributeEntityWithRelationship::class)
                .list<Map<String, Any?>>().first()

        assertEquals("RELTN", result["relationship.stringValue"], "String value did not return as expected")
        assertEquals("9", result["stringValue"], "String value did not return as expected")
    }
}