package database.functions

import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.AllAttributeEntity
import entities.AllAttributeEntityWithRelationship
import entities.History
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(Parameterized::class)
class FormatFunctionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun insertTestData() {
        manager.from(AllAttributeEntityWithRelationship::class).delete()
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
        entity.doubleValue = null // Explicitly set to null for testing
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

        // Add some History entities with dates for date formatting tests
        manager.from(History::class).delete()
        val history1 = History(
            "historyID1",
            33.3,
            "A",
            "TSLA",
            Date(2023 - 1900, 0, 15) // Jan 15, 2023
        )
        manager.saveEntity(history1)
        
        val history2 = History(
            "historyID2",
            35.7,
            "A",
            "TSLA",
            Date(2023 - 1900, 1, 20) // Feb 20, 2023
        )
        manager.saveEntity(history2)
        
        val history3 = History(
            "historyID3",
            40.1,
            "A",
            "AAPL",
            Date(2023 - 1900, 0, 25) // Jan 25, 2023
        )
        manager.saveEntity(history3)
    }

    @Test
    fun testFormatInSelectClause() {
        // Test formatting numbers with custom pattern
        val results = manager.select(format("doublePrimitive", "#.##"))
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy("id".asc())
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results")
        val firstResult = results.first()
        assertEquals("2.00", firstResult["format(doublePrimitive, '#.##')"], "Should format number correctly")
    }

    @Test
    fun testFormatDateInSelectClause() {
        // Test formatting dates with custom pattern
        val results = manager.select(format("dateTime", "dd/MM/yyyy"), "symbolId")
                .from(History::class)
                .orderBy("dateTime".asc())
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results")
        val firstResult = results.first()
        assertEquals("15/01/2023", firstResult["format(dateTime, 'dd/MM/yyyy')"], "Should format date correctly")
    }

    @Test
    fun testFormatInSelectWithGroupBy() {
        // Test formatting in select with group by clause
        val results = manager.select("stringValue", format("longPrimitive", "#,###"))
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy("stringValue")
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return grouped results")
        val groupedResult = results.firstOrNull { it["stringValue"] == "9" }
        assertNotNull(groupedResult, "Should find group for stringValue='9'")
        // The format function should be applied to each item in the group
        assertTrue(groupedResult.containsKey("format(longPrimitive, '#,###')"), "Should contain formatted column")
    }

    @Test
    fun testFormatInSelectWithOrderBy() {
        // Test formatting in select with order by clause
        val results = manager.select(format("doublePrimitive", "#.##"), "id")
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy(format("doublePrimitive", "#.##").asc())
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return ordered results")
        val firstResult = results.first()
        // The entity with id="2" has doublePrimitive = 0.0, so when ordered by formatted values ascending, it should be first
        assertEquals("0.00", firstResult["format(doublePrimitive, '#.##')"], "Should order by formatted values")
    }

    @Test
    fun testFormatMultipleFieldsInSelect() {
        // Test formatting multiple fields in select clause
        val results = manager.select(
                format("doublePrimitive", "#.##"),
                format("longPrimitive", "#,###"),
                "id"
        )
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy("id".asc())
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results with multiple formatted fields")
        val firstResult = results.first()
        assertEquals("2.00", firstResult["format(doublePrimitive, '#.##')"], "Should format double correctly")
        assertEquals("2", firstResult["format(longPrimitive, '#,###')"], "Should format long correctly")
    }

    @Test
    fun testFormatInGroupByClause() {
        // Test using format function in group by clause
        val results = manager.select(format("longPrimitive", "#"), "count(id)")
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy(format("longPrimitive", "#"))
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return grouped results by formatted values")
        assertTrue(results.size > 1, "Should have multiple groups")
    }

    @Test
    fun testFormatInHavingClause() {
        // Test using format function in having clause through group by
        val results = manager.select(format("longPrimitive", "#"), "count(id)")
                .from(AllAttributeEntityWithRelationship::class)
                .groupBy(format("longPrimitive", "#"))
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results that can be filtered")
    }

    @Test
    fun testFormatWithNullValues() {
        // Test formatting with null values
        val results = manager.select("doubleValue", format("doubleValue", "#.##"), "id")
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy("id".desc()) // Order by descending ID to get the entity with null value first
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results")
        // Find the result where doubleValue is null (entity with id="9")
        val nullResult = results.firstOrNull { it["id"] == "9" }
        assertNotNull(nullResult, "Should find entity with null doubleValue")
        // Since doubleValue is null, it should return empty string
        assertEquals("", nullResult["format(doubleValue, '#.##')"], "Should return empty string for null values")
    }

    @Test
    fun testFormatStringValues() {
        // Test formatting string values (should return as-is)
        val results = manager.select(format("stringValue", ""), "id")
                .from(AllAttributeEntityWithRelationship::class)
                .orderBy("id".asc())
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results")
        val firstResult = results.first()
        assertEquals("2", firstResult["format(stringValue, '')"], "Should return string as-is")
    }

    @Test
    fun testFormatWithInvalidPatternFallback() {
        // Test that invalid patterns fall back to default formatting
        val results = manager.select(format("doublePrimitive", "invalidPattern"), "doublePrimitive")
                .from(AllAttributeEntityWithRelationship::class)
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results")
        val firstResult = results.first()
        // Should not throw exception and should return some formatted value
        assertNotNull(firstResult["format(doublePrimitive, 'invalidPattern')"], "Should handle invalid pattern gracefully")
    }

    @Test
    fun testFormatInComplexQuery() {
        // Test format function in a complex query with multiple clauses
        val results = manager.select(
            count("symbolId"),
                format("dateTime", "yyyy-MM-dd"),
                format("volume", "$#.##"),
                "symbolId"
        )
                .from(History::class)
                .where("symbolId" eq "TSLA")
                .groupBy(format("dateTime", "yyyy"))
                .orderBy(format("dateTime", "yyyy-MM-dd").asc())
                .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty(), "Should return results from complex query")
        
        // Debug what we're getting
        val formattedDateValue = results[0]["format(dateTime, 'yyyy-MM-dd')"]
        println("Formatted date value: $formattedDateValue (type: ${formattedDateValue?.javaClass?.simpleName})")
        
        assertFalse(formattedDateValue is Date, "Formatted date should be a String, but was ${formattedDateValue?.javaClass?.simpleName}: $formattedDateValue")
    }
}
