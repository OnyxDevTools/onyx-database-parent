package database.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.search
import com.onyx.persistence.query.searchAllTables
import database.base.DatabaseBaseTest
import entities.LucenePartitionedEntity
import entities.LuceneSearchEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class FullTextSearchTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<LuceneSearchEntity>().delete()
        manager.from<LucenePartitionedEntity>()
            .inPartition(QueryPartitionMode.ALL)
            .delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = listOf(EmbeddedPersistenceManagerFactory::class)
    }

    @Test
    fun testBasicSearchAllTables() {
        val searchEntity = LuceneSearchEntity().apply {
            title = "Breaking News"
            body = "quick fox jumps over the river"
            category = "news"
        }
        val partitionedEntity = LucenePartitionedEntity().apply {
            region = "north"
            tag = "alpha"
            body = "fox sightings in the north region"
        }
        val nonMatchingEntity = LuceneSearchEntity().apply {
            title = "Weather Report"
            body = "sunny skies and warm breeze"
            category = "weather"
        }

        manager.saveEntity<IManagedEntity>(searchEntity)
        manager.saveEntity<IManagedEntity>(partitionedEntity)
        manager.saveEntity<IManagedEntity>(nonMatchingEntity)

        val results = manager.searchAllTables("fox")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.entityType == LuceneSearchEntity::class.java })
        assertTrue(results.any { it.entityType == LucenePartitionedEntity::class.java })
        results.forEach { result -> assertNotNull(result.id) }

        val builderResults = manager.search("fox").list()
        assertEquals(results.size, builderResults.size)
    }

    @Test
    fun testPartitionSearchAll() {
        val northEntity = LucenePartitionedEntity().apply {
            region = "north"
            tag = "delta"
            body = "delta payload in north partition"
        }
        val southEntity = LucenePartitionedEntity().apply {
            region = "south"
            tag = "delta"
            body = "delta payload in south partition"
        }
        manager.saveEntity<IManagedEntity>(northEntity)
        manager.saveEntity<IManagedEntity>(southEntity)

        val allPartitions = manager.from<LucenePartitionedEntity>()
            .search("delta")
            .inPartition(QueryPartitionMode.ALL)
            .list<LucenePartitionedEntity>()
        assertEquals(2, allPartitions.size)

        val northOnly = manager.from<LucenePartitionedEntity>()
            .search("delta")
            .inPartition("north")
            .list<LucenePartitionedEntity>()
        assertEquals(1, northOnly.size)
        assertEquals("north", northOnly.first().region)
    }

    @Test
    fun testSearchAllWithAdditionalPredicates() {
        val stormNews = LuceneSearchEntity().apply {
            title = "Storm Alert"
            body = "storm warning across the coast"
            category = "news"
        }
        val stormSports = LuceneSearchEntity().apply {
            title = "Storming Victory"
            body = "storm of goals in the final"
            category = "sports"
        }
        val calmNews = LuceneSearchEntity().apply {
            title = "Calm Seas"
            body = "gentle waves and calm winds"
            category = "news"
        }
        manager.saveEntity<IManagedEntity>(stormNews)
        manager.saveEntity<IManagedEntity>(stormSports)
        manager.saveEntity<IManagedEntity>(calmNews)

        val andResults = manager.from<LuceneSearchEntity>()
            .where(search("storm"))
            .and("category" eq "news")
            .list<LuceneSearchEntity>()
        assertEquals(1, andResults.size)
        assertEquals("news", andResults.first().category)

        val orCriteria = QueryCriteria("category", QueryCriteriaOperator.EQUAL, "sports")
            .or(QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "storm"))
        val orResults = manager.from<LuceneSearchEntity>()
            .where(orCriteria)
            .list<LuceneSearchEntity>()
        assertEquals(2, orResults.size)
        assertTrue(orResults.any { it.category == "sports" })
        assertTrue(orResults.any { it.category == "news" })
    }

    @Test
    fun testMinScoreFiltersResults() {
        val stormNews = LuceneSearchEntity().apply {
            title = "Storm Alert"
            body = "storm warning across the coast"
            category = "news"
        }
        manager.saveEntity<IManagedEntity>(stormNews)

        val filtered = manager.from<LuceneSearchEntity>()
            .search("storm", Float.MAX_VALUE)
            .list<LuceneSearchEntity>()

        assertTrue(filtered.isEmpty())
    }
}
