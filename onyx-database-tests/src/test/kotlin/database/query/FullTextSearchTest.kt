package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.like
import com.onyx.persistence.query.search
import com.onyx.persistence.query.searchAllTables
import database.base.DatabaseBaseTest
import entities.VectorPartitionedEntity
import entities.VectorSearchEntity
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
    override fun initialize() {
        factory = EmbeddedPersistenceManagerFactory(EMBEDDED_DATABASE_LOCATION).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
    }

    @Before
    fun prepare() {
        manager.from<VectorSearchEntity>().delete()
        manager.from<VectorPartitionedEntity>()
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
        val searchEntity = VectorSearchEntity().apply {
            title = "Breaking News"
            body = "quick fox jumps over the river"
            category = "news"
        }
        val partitionedEntity = VectorPartitionedEntity().apply {
            region = "north"
            tag = "alpha"
            body = "fox sightings in the north region"
        }
        val nonMatchingEntity = VectorSearchEntity().apply {
            title = "Weather Report"
            body = "sunny skies and warm breeze"
            category = "weather"
        }

        manager.saveEntity<IManagedEntity>(searchEntity)
        manager.saveEntity<IManagedEntity>(partitionedEntity)
        manager.saveEntity<IManagedEntity>(nonMatchingEntity)

        val results = manager.searchAllTables("fox")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.entityType == VectorSearchEntity::class.java })
        assertTrue(results.any { it.entityType == VectorPartitionedEntity::class.java })
        results.forEach { result -> assertNotNull(result.id) }

        val builderResults = manager.search("fox").list()
        assertEquals(results.size, builderResults.size)
    }

    @Test
    fun testPartitionSearchAll() {
        val northEntity = VectorPartitionedEntity().apply {
            region = "north"
            tag = "delta"
            body = "delta payload in north partition"
        }
        val southEntity = VectorPartitionedEntity().apply {
            region = "south"
            tag = "delta"
            body = "delta payload in south partition"
        }
        manager.saveEntity<IManagedEntity>(northEntity)
        manager.saveEntity<IManagedEntity>(southEntity)

        val allPartitions = manager.from<VectorPartitionedEntity>()
            .search("delta")
            .inPartition(QueryPartitionMode.ALL)
            .list<VectorPartitionedEntity>()
        assertEquals(2, allPartitions.size)

        val northOnly = manager.from<VectorPartitionedEntity>()
            .search("delta")
            .inPartition("north")
            .list<VectorPartitionedEntity>()
        assertEquals(1, northOnly.size)
        assertEquals("north", northOnly.first().region)
    }

    @Test
    fun testDeleteSearchablePartitionPreservesOtherPartitionAndSurvivesReopen() {
        val deleted = manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "individual-delete"
            tag = "deleted"
            body = "unique deleted searchable payload"
        }) as VectorPartitionedEntity
        val retained = manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "individual-retained"
            tag = "retained"
            body = "unique retained searchable payload"
        }) as VectorPartitionedEntity

        assertEquals(
            1,
            manager.from<VectorPartitionedEntity>()
                .inPartition("individual-delete")
                .delete()
        )

        assertEquals(0L, manager.from<VectorPartitionedEntity>()
            .inPartition("individual-delete")
            .count())
        assertTrue(manager.from<VectorPartitionedEntity>()
            .inPartition("individual-delete")
            .search("unique deleted payload")
            .list<VectorPartitionedEntity>()
            .isEmpty())

        val retainedImmediately = manager.from<VectorPartitionedEntity>()
            .inPartition("individual-retained")
            .search("unique retained payload")
            .list<VectorPartitionedEntity>()
        assertTrue(retainedImmediately.any { it.id == retained.id })
        assertEquals("retained", retainedImmediately.first { it.id == retained.id }.tag)

        factory.close()
        initialize()

        assertEquals(0L, manager.from<VectorPartitionedEntity>()
            .inPartition("individual-delete")
            .count())
        assertTrue(manager.from<VectorPartitionedEntity>()
            .inPartition("individual-delete")
            .search("unique deleted payload")
            .list<VectorPartitionedEntity>()
            .none { it.id == deleted.id })

        val retainedAfterReopen = manager.from<VectorPartitionedEntity>()
            .inPartition("individual-retained")
            .search("unique retained payload")
            .list<VectorPartitionedEntity>()
        assertTrue(retainedAfterReopen.any { it.id == retained.id })

        val recreated = manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "individual-delete"
            tag = "recreated"
            body = "recreated searchable partition payload"
        }) as VectorPartitionedEntity
        assertTrue(
            manager.from<VectorPartitionedEntity>()
                .inPartition("individual-delete")
                .search("recreated partition payload")
                .list<VectorPartitionedEntity>()
                .any { it.id == recreated.id }
        )
        assertEquals(1L, manager.from<VectorPartitionedEntity>()
            .inPartition("individual-retained")
            .count())
    }

    @Test
    fun testDeleteAllSearchablePartitionsSurvivesReopen() {
        manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "delete-all-north"
            tag = "deleted"
            body = "north searchable partition"
        })
        manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "delete-all-south"
            tag = "deleted"
            body = "south searchable partition"
        })

        assertEquals(2, manager.from<VectorPartitionedEntity>().delete())

        factory.close()
        initialize()

        assertEquals(0L, manager.from<VectorPartitionedEntity>().count())

        val savedAfterReopen = manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "delete-all-recreated"
            tag = "recreated"
            body = "searchable data saved after deleting all partitions"
        }) as VectorPartitionedEntity

        val searchResults = manager.from<VectorPartitionedEntity>()
            .inPartition("delete-all-recreated")
            .search("saved after deleting")
            .list<VectorPartitionedEntity>()
        assertTrue(searchResults.any { it.id == savedAfterReopen.id })
    }

    @Test
    fun testSearchAllWithAdditionalPredicates() {
        val stormNews = VectorSearchEntity().apply {
            title = "Storm Alert"
            body = "storm warning across the coast"
            category = "news"
        }
        val stormSports = VectorSearchEntity().apply {
            title = "Storming Victory"
            body = "storm of goals in the final"
            category = "sports"
        }
        val calmNews = VectorSearchEntity().apply {
            title = "Calm Seas"
            body = "gentle waves and calm winds"
            category = "news"
        }
        manager.saveEntity<IManagedEntity>(stormNews)
        manager.saveEntity<IManagedEntity>(stormSports)
        manager.saveEntity<IManagedEntity>(calmNews)

        val andResults = manager.from<VectorSearchEntity>()
            .where(search("storm"))
            .and("category" eq "news")
            .list<VectorSearchEntity>()
        assertEquals(1, andResults.size)
        assertEquals("news", andResults.first().category)

        val orCriteria = QueryCriteria("category", QueryCriteriaOperator.EQUAL, "sports")
            .or(QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "storm"))
        val orResults = manager.from<VectorSearchEntity>()
            .where(orCriteria)
            .list<VectorSearchEntity>()
        assertEquals(2, orResults.size)
        assertTrue(orResults.any { it.category == "sports" })
        assertTrue(orResults.any { it.category == "news" })
    }

    @Test
    fun testComplementedSearchDoesNotLeakScoresIntoOrMatches() {
        val searchMatchAdmittedByOr = manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "Foo anchor"
            body = "foo payload"
            category = "anchor"
        }) as VectorSearchEntity
        val complementMatch = manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "Plain document"
            body = "ordinary payload"
            category = "other"
        }) as VectorSearchEntity
        manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "Excluded foo"
            body = "foo payload"
            category = "other"
        })

        val criteria = QueryCriteria(
            Query.FULL_TEXT_ATTRIBUTE,
            QueryCriteriaOperator.NOT_MATCHES,
            "foo"
        ).or(QueryCriteria("category", QueryCriteriaOperator.EQUAL, "anchor"))
        val results = manager.from<VectorSearchEntity>()
            .where(criteria)
            .select("id", Query.SCORE_SELECTION)
            .list<Map<String, Any?>>()
            .associateBy { it["id"] as Long }

        assertEquals(setOf(searchMatchAdmittedByOr.id, complementMatch.id), results.keys)
        assertEquals(null, results.getValue(searchMatchAdmittedByOr.id)[Query.SCORE_SELECTION])
        assertEquals(null, results.getValue(complementMatch.id)[Query.SCORE_SELECTION])
    }

    @Test
    fun testCompoundStructuredPredicateAcrossAllPartitionsKeepsPartitionRestrictions() {
        val north = manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "compound-north"
            tag = "compound-anchor"
            body = "needle in north"
        }) as VectorPartitionedEntity
        val south = manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "compound-south"
            tag = "compound-anchor"
            body = "needle in south"
        }) as VectorPartitionedEntity
        manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            region = "compound-decoy"
            tag = "compound-anchor"
            body = "unrelated payload"
        })

        val results = manager.from<VectorPartitionedEntity>()
            .where(QueryCriteria("tag", QueryCriteriaOperator.EQUAL, "compound-anchor"))
            .and(QueryCriteria("body", QueryCriteriaOperator.CONTAINS, "needle"))
            .inPartition(QueryPartitionMode.ALL)
            .list<VectorPartitionedEntity>()

        assertEquals(setOf(north.id, south.id), results.map(VectorPartitionedEntity::id).toSet())
        assertEquals(setOf("compound-north", "compound-south"), results.map(VectorPartitionedEntity::region).toSet())
    }

    @Test
    fun testMinScoreFiltersResults() {
        val stormNews = VectorSearchEntity().apply {
            title = "Storm Alert"
            body = "storm warning across the coast"
            category = "news"
        }
        manager.saveEntity<IManagedEntity>(stormNews)

        val filtered = manager.from<VectorSearchEntity>()
            .search("storm", Float.MAX_VALUE)
            .list<VectorSearchEntity>()

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testCompoundSearchKeepsGlobalScoreOrder() {
        val weaker = VectorSearchEntity().apply {
            title = "First inserted"
            body = "alpha"
            category = "ranking"
        }
        val stronger = VectorSearchEntity().apply {
            title = "Second inserted"
            body = "alpha beta"
            category = "ranking"
        }
        manager.saveEntity<IManagedEntity>(weaker)
        manager.saveEntity<IManagedEntity>(stronger)

        val results = manager.from<VectorSearchEntity>()
            .where(
                ("category" eq "ranking").and(
                    search(VectorSearchQuery(text = "alpha beta", requireAllTerms = false))
                )
            )
            .list<VectorSearchEntity>()

        assertEquals(listOf(stronger.id, weaker.id), results.map(VectorSearchEntity::id))
    }

    @Test
    fun testSelectScoreWithSearch() {
        val searchEntity = VectorSearchEntity().apply {
            title = "Vector Database Guide"
            body = "quick fox search score"
            category = "docs"
        }
        manager.saveEntity<IManagedEntity>(searchEntity)

        val results = manager.from<VectorSearchEntity>()
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
