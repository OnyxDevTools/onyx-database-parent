package database.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.like
import com.onyx.persistence.query.search
import com.onyx.persistence.query.searchAllTables
import database.base.DatabaseBaseTest
import entities.LucenePartitionedEntity
import entities.LuceneSearchEntity
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertContains
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
    fun testDeleteSearchablePartitionPreservesOtherPartitionAndSurvivesReopen() {
        val deleted = manager.saveEntity<IManagedEntity>(LucenePartitionedEntity().apply {
            region = "individual-delete"
            tag = "deleted"
            body = "unique deleted searchable payload"
        }) as LucenePartitionedEntity
        val retained = manager.saveEntity<IManagedEntity>(LucenePartitionedEntity().apply {
            region = "individual-retained"
            tag = "retained"
            body = "unique retained searchable payload"
        }) as LucenePartitionedEntity

        assertEquals(
            1,
            manager.from<LucenePartitionedEntity>()
                .inPartition("individual-delete")
                .delete()
        )

        assertEquals(0L, manager.from<LucenePartitionedEntity>()
            .inPartition("individual-delete")
            .count())
        assertTrue(manager.from<LucenePartitionedEntity>()
            .inPartition("individual-delete")
            .search("unique deleted payload")
            .list<LucenePartitionedEntity>()
            .isEmpty())

        val retainedImmediately = manager.from<LucenePartitionedEntity>()
            .inPartition("individual-retained")
            .search("unique retained payload")
            .list<LucenePartitionedEntity>()
        assertTrue(retainedImmediately.any { it.id == retained.id })
        assertEquals("retained", retainedImmediately.first { it.id == retained.id }.tag)

        factory.close()
        initialize()

        assertEquals(0L, manager.from<LucenePartitionedEntity>()
            .inPartition("individual-delete")
            .count())
        assertTrue(manager.from<LucenePartitionedEntity>()
            .inPartition("individual-delete")
            .search("unique deleted payload")
            .list<LucenePartitionedEntity>()
            .none { it.id == deleted.id })

        val retainedAfterReopen = manager.from<LucenePartitionedEntity>()
            .inPartition("individual-retained")
            .search("unique retained payload")
            .list<LucenePartitionedEntity>()
        assertTrue(retainedAfterReopen.any { it.id == retained.id })

        val recreated = manager.saveEntity<IManagedEntity>(LucenePartitionedEntity().apply {
            region = "individual-delete"
            tag = "recreated"
            body = "recreated searchable partition payload"
        }) as LucenePartitionedEntity
        assertTrue(
            manager.from<LucenePartitionedEntity>()
                .inPartition("individual-delete")
                .search("recreated partition payload")
                .list<LucenePartitionedEntity>()
                .any { it.id == recreated.id }
        )
        assertEquals(1L, manager.from<LucenePartitionedEntity>()
            .inPartition("individual-retained")
            .count())
    }

    @Test
    fun testDeleteAllSearchablePartitionsSurvivesReopen() {
        manager.saveEntity<IManagedEntity>(LucenePartitionedEntity().apply {
            region = "delete-all-north"
            tag = "deleted"
            body = "north searchable partition"
        })
        manager.saveEntity<IManagedEntity>(LucenePartitionedEntity().apply {
            region = "delete-all-south"
            tag = "deleted"
            body = "south searchable partition"
        })

        assertEquals(2, manager.from<LucenePartitionedEntity>().delete())

        factory.close()
        initialize()

        assertEquals(0L, manager.from<LucenePartitionedEntity>().count())

        val savedAfterReopen = manager.saveEntity<IManagedEntity>(LucenePartitionedEntity().apply {
            region = "delete-all-recreated"
            tag = "recreated"
            body = "searchable data saved after deleting all partitions"
        }) as LucenePartitionedEntity

        val searchResults = manager.from<LucenePartitionedEntity>()
            .inPartition("delete-all-recreated")
            .search("saved after deleting")
            .list<LucenePartitionedEntity>()
        assertTrue(searchResults.any { it.id == savedAfterReopen.id })
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

    @Test
    fun testSelectScoreWithSearch() {
        val searchEntity = LuceneSearchEntity().apply {
            title = "Vector Database Guide"
            body = "quick fox search score"
            category = "docs"
        }
        manager.saveEntity<IManagedEntity>(searchEntity)

        val results = manager.from<LuceneSearchEntity>()
            .search("fox")
            .select(Query.SCORE_SELECTION, Query.WILDCARD_SELECTION)
            .list<Map<String, Any?>>()

        assertTrue(results.isNotEmpty())
        val first = results.first()
        assertContains(first.keys, Query.SCORE_SELECTION)
        assertNotNull(first[Query.SCORE_SELECTION] as Float?)
        assertContains(first.keys, "id")
        assertContains(first.keys, "title")
        assertContains(first.keys, "body")
        assertContains(first.keys, "category")
    }
}
