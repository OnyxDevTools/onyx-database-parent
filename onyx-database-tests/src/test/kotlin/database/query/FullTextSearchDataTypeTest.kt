package database.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.gt
import com.onyx.persistence.query.search
import com.onyx.persistence.query.searchAllTables
import database.base.DatabaseBaseTest
import entities.LuceneDataTypeEntity
import entities.LuceneSearchEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.Date

@RunWith(Parameterized::class)
class FullTextSearchDataTypeTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<LuceneSearchEntity>().delete()
        manager.from<LuceneDataTypeEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = listOf(EmbeddedPersistenceManagerFactory::class)
    }

    @Test
    fun testFullTextSearchWithDifferentDataTypes() {
        // Create entities with different data types
        val entity1 = LuceneDataTypeEntity().apply {
            title = "quick brown fox"
            description = "First test entity"
            longValue = 123456789L
            intValue = 42
            doubleValue = 3.14159
            booleanValue = true
            dateValue = Date(1000000000000L) // 2001-09-09
            floatValue = 2.718f
            byteValue = 127
            shortValue = 32767
            charValue = 'A'
        }

        val entity2 = LuceneDataTypeEntity().apply {
            title = "lazy dog"
            description = "Second test entity"
            longValue = 987654321L
            intValue = 24
            doubleValue = 2.71828
            booleanValue = false
            dateValue = Date(1200000000000L) // 2008-01-10
            floatValue = 3.141f
            byteValue = 64
            shortValue = 16384
            charValue = 'Z'
        }

        val entity3 = LuceneDataTypeEntity().apply {
            title = "jumping fox"
            description = "Third test entity"
            longValue = 111111111L
            intValue = 84
            doubleValue = 1.41421
            booleanValue = true
            dateValue = Date(1400000000000L) // 2014-05-13
            floatValue = 1.732f
            byteValue = 32
            shortValue = 8192
            charValue = 'X'
        }

        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)

        // Test search on string value
        val stringResults = manager.from<LuceneDataTypeEntity>()
            .search("fox")
            .list<LuceneDataTypeEntity>()
        assertEquals(2, stringResults.size)
        assertTrue(stringResults.any { it.title?.contains("fox") == true })

        // Test search on numeric values (should find entities containing those numbers in any field)
        val numericResults = manager.from<LuceneDataTypeEntity>()
            .search("123456789")
            .list<LuceneDataTypeEntity>()
        // Should find entity1 because it contains 123456789 in longValue field
        assertEquals(1, numericResults.size)
        assertEquals(123456789L, numericResults[0].longValue)

        // Test search on boolean values
        val booleanResults = manager.from<LuceneDataTypeEntity>()
            .search("true")
            .list<LuceneDataTypeEntity>()
        // Should find entities with booleanValue = true
        assertEquals(2, booleanResults.size)
        assertTrue(booleanResults.all { it.booleanValue == true })

        // Test search on date values (partial match)
        val dateResults = manager.from<LuceneDataTypeEntity>()
            .search("2001")
            .list<LuceneDataTypeEntity>()
        // Should find entity1 which has date 2001-09-09
        assertEquals(1, dateResults.size)
        assertEquals(Date(1000000000000L), dateResults[0].dateValue)
    }

    @Test
    fun testFullTextSearchWithAndPredicateOnDifferentDataTypes() {
        // Create entities with different data types
        val entity1 = LuceneDataTypeEntity().apply {
            title = "important document"
            description = "First priority item"
            longValue = 1000000L
            intValue = 50
            doubleValue = 2.5
            booleanValue = true
        }

        val entity2 = LuceneDataTypeEntity().apply {
            title = "unimportant document"
            description = "Low priority item"
            longValue = 2000000L
            intValue = 75
            doubleValue = 3.5
            booleanValue = false
        }

        val entity3 = LuceneDataTypeEntity().apply {
            title = "important memo"
            description = "Medium priority item"
            longValue = 1500000L
            intValue = 60
            doubleValue = 2.5
            booleanValue = true
        }

        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)

        // Test full-text search combined with AND predicate on numeric field
        val andResults = manager.from<LuceneDataTypeEntity>()
            .where(search("important"))
            .and("intValue" gt 55)
            .list<LuceneDataTypeEntity>()
        // Should find entity3 ("important memo" with intValue=60 > 55)
        assertEquals(1, andResults.size)
        assertTrue(andResults[0].title?.contains("important") == true)
        assertTrue(andResults[0].intValue!! > 55)

        // Test full-text search combined with AND predicate on boolean field
        val andBooleanResults = manager.from<LuceneDataTypeEntity>()
            .where(search("document"))
            .and("booleanValue" eq true)
            .list<LuceneDataTypeEntity>()
        assertEquals(1, andBooleanResults.size)
        assertEquals(true, andBooleanResults[0].booleanValue)
        assertTrue(andBooleanResults[0].title?.contains("document") == true)
    }

    @Test
    fun testFullTextSearchWithOrPredicateOnDifferentDataTypes() {
        // Create entities with different data types
        val entity1 = LuceneDataTypeEntity().apply {
            title = "red car"
            description = "Fast vehicle"
            longValue = 100L
            intValue = 10
            doubleValue = 1.1
            booleanValue = true
        }

        val entity2 = LuceneDataTypeEntity().apply {
            title = "blue truck"
            description = "Heavy vehicle"
            longValue = 200L
            intValue = 20
            doubleValue = 2.2
            booleanValue = false
        }

        val entity3 = LuceneDataTypeEntity().apply {
            title = "green bike"
            description = "Light vehicle"
            longValue = 300L
            intValue = 30
            doubleValue = 3.3
            booleanValue = true
        }

        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)

        // Test full-text search combined with OR predicate on numeric field
        val orCriteria = QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN, 25)
            .or(QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "red"))
        val orResults = manager.from<LuceneDataTypeEntity>()
            .where(orCriteria)
            .list<LuceneDataTypeEntity>()
        assertEquals(2, orResults.size)
        assertTrue(orResults.any { it.title?.contains("red") == true }) // matches "red"
        assertTrue(orResults.any { it.intValue!! > 25 }) // intValue=30 > 25

        // Test full-text search combined with OR predicate on boolean field
        val orBooleanCriteria = QueryCriteria("booleanValue", QueryCriteriaOperator.EQUAL, false)
            .or(QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "bike"))
        val orBooleanResults = manager.from<LuceneDataTypeEntity>()
            .where(orBooleanCriteria)
            .list<LuceneDataTypeEntity>()
        assertEquals(2, orBooleanResults.size)
        assertTrue(orBooleanResults.any { it.booleanValue == false }) // booleanValue=false
        assertTrue(orBooleanResults.any { it.title?.contains("bike") == true }) // matches "bike"
    }

    @Test
    fun testFullTextSearchWithComplexAndOrPredicates() {
        // Create entities with different data types
        val entity1 = LuceneDataTypeEntity().apply {
            title = "urgent project"
            description = "High priority work"
            longValue = 1000L
            intValue = 100
            doubleValue = 5.0
            booleanValue = true
            dateValue = Date(1500000000000L) // 2017-07-14
        }

        val entity2 = LuceneDataTypeEntity().apply {
            title = "normal task"
            description = "Regular work"
            longValue = 500L
            intValue = 50
            doubleValue = 2.5
            booleanValue = false
            dateValue = Date(1400000000000L) // 2014-05-13
        }

        val entity3 = LuceneDataTypeEntity().apply {
            title = "urgent meeting"
            description = "Important discussion"
            longValue = 750L
            intValue = 75
            doubleValue = 3.75
            booleanValue = true
            dateValue = Date(1600000000000L) // 2020-09-13
        }

        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)

        // Complex query: (full-text search for "urgent") AND ((intValue > 80) OR (booleanValue = true))
        val urgentCriteria = QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "urgent")
        val intValueCriteria = QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN, 80)
        val booleanCriteria = QueryCriteria("booleanValue", QueryCriteriaOperator.EQUAL, true)

        val complexCriteria = urgentCriteria.and(intValueCriteria.or(booleanCriteria))
        val complexResults = manager.from<LuceneDataTypeEntity>()
            .where(complexCriteria)
            .list<LuceneDataTypeEntity>()

        // Should return entities that match "urgent" and either have intValue > 80 or booleanValue = true
        assertTrue(complexResults.isNotEmpty())
        assertTrue(complexResults.all { it.title?.contains("urgent") == true })
    }

    @Test
    fun testFullTextSearchAllTablesWithDifferentDataTypes() {
        // Create entities with different data types
        val entity1 = LuceneDataTypeEntity().apply {
            title = "database search"
            description = "Searching databases"
            longValue = 123456L
            intValue = 42
            booleanValue = true
        }

        val entity2 = LuceneDataTypeEntity().apply {
            title = "lucene indexing"
            description = "Indexing with Lucene"
            longValue = 789012L
            intValue = 24
            booleanValue = false
        }

        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)

        // Test searchAllTables with numeric values
        val results = manager.searchAllTables("123456")
        assertTrue(results.isNotEmpty())

        // Test searchAllTables with boolean values
        val booleanResults = manager.searchAllTables("true")
        assertTrue(booleanResults.isNotEmpty())
    }

    @Test
    fun testFullTextSearchWithFloatAndByteDataTypes() {
        // Create entities with float and byte data types
        val entity1 = LuceneDataTypeEntity().apply {
            title = "scientific calculation"
            description = "Mathematical constants"
            floatValue = 3.14159f
            byteValue = 127
            shortValue = 32767
            charValue = 'π'
        }

        val entity2 = LuceneDataTypeEntity().apply {
            title = "mathematical constant"
            description = "Scientific numbers"
            floatValue = 2.71828f
            byteValue = 64
            shortValue = 16384
            charValue = 'e'
        }

        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)

        // Test search on float values
        val floatResults = manager.from<LuceneDataTypeEntity>()
            .search("3.14159")
            .list<LuceneDataTypeEntity>()
        assertEquals(1, floatResults.size)
        assertEquals(3.14159f, floatResults[0].floatValue)

        // Test search on byte values
        val byteResults = manager.from<LuceneDataTypeEntity>()
            .search("127")
            .list<LuceneDataTypeEntity>()
        assertEquals(1, byteResults.size)
        assertEquals(127, byteResults[0].byteValue)

        // Test search on char values
        val charResults = manager.from<LuceneDataTypeEntity>()
            .search("π")
            .list<LuceneDataTypeEntity>()
        assertEquals(1, charResults.size)
        assertEquals('π', charResults[0].charValue)
    }
}
