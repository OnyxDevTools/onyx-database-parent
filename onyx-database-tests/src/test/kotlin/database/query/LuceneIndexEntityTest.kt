package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.like
import com.onyx.persistence.query.select
import database.base.DatabaseBaseTest
import entities.LuceneIndexedPartitionedEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class LuceneIndexEntityTest(
    override var factoryClass: KClass<*>,
    private val testStoreType: StoreType
) : DatabaseBaseTest(factoryClass) {

    @Before
    override fun initialize() {
        factory = EmbeddedPersistenceManagerFactory(EMBEDDED_DATABASE_LOCATION).apply {
            storeType = testStoreType
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
    }

    @Before
    fun prepare() {
        manager.from<LuceneIndexedPartitionedEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun persistenceManagersToTest(): Collection<Array<Any>> = listOf(
            arrayOf(EmbeddedPersistenceManagerFactory::class, StoreType.MEMORY_MAPPED_FILE),
            arrayOf(EmbeddedPersistenceManagerFactory::class, StoreType.MEMORY_MAPPED_FILE)
        )
    }

    @Test
    fun testSearchNonExistent() {
        manager.from<LuceneIndexedPartitionedEntity>().inPartition("iduno").where("value" eq "iduno").list<LuceneIndexEntityTest>()
    }

    @Test
    fun testSearchNonExistentNoPredicate() {
        manager.from<LuceneIndexedPartitionedEntity>().inPartition("iduno").list<LuceneIndexEntityTest>()
    }

    @Test
    fun searchLuceneFieldWithScore() {
        val entity = LuceneIndexedPartitionedEntity().apply { value = "iduno" }
        manager.saveEntity(entity)
        val res = manager
            .select("*", "__score__").from<LuceneIndexedPartitionedEntity>()
            .where("value" like "iduno")
            .list<Map<String, Any>>()

        assertNotNull(res)
        assertNotNull(res.firstOrNull())
        assertEquals(0.13076457f, res[0]["__score__"])
    }

    @Test
    fun deletingSubsequentSearchablePartitionsKeepsFieldIndexWriterOpen() {
        manager.saveEntity(LuceneIndexedPartitionedEntity().apply {
            region = "lucene-index-first"
            value = "first searchable value"
            databaseId = "database-first"
        })
        manager.saveEntity(LuceneIndexedPartitionedEntity().apply {
            region = "lucene-index-second"
            value = "second searchable value"
            databaseId = "database-second"
        })

        assertEquals(
            1,
            manager.from<LuceneIndexedPartitionedEntity>()
                .inPartition("lucene-index-first")
                .delete()
        )
        assertEquals(
            1,
            manager.from<LuceneIndexedPartitionedEntity>()
                .inPartition("lucene-index-second")
                .delete()
        )
    }

    @Test
    fun deletingSearchablePartitionWithDefaultIndexDoesNotCorruptIndexData() {
        manager.saveEntity(LuceneIndexedPartitionedEntity().apply {
            region = "indexed-delete"
            value = "deleted lucene content"
            databaseId = "deleted-database"
        })
        val retained = manager.saveEntity(LuceneIndexedPartitionedEntity().apply {
            region = "indexed-retained"
            value = "retained lucene content"
            databaseId = "retained-database"
        })

        assertEquals(
            1,
            manager.from<LuceneIndexedPartitionedEntity>()
                .inPartition("indexed-delete")
                .delete()
        )

        val recreated = manager.saveEntity(LuceneIndexedPartitionedEntity().apply {
            region = "indexed-delete"
            value = "recreated lucene content"
            databaseId = "recreated-database"
        })
        assertEquals(
            recreated.id,
            manager.from<LuceneIndexedPartitionedEntity>()
                .inPartition("indexed-delete")
                .where("databaseId" eq "recreated-database")
                .first<LuceneIndexedPartitionedEntity>()
                .id
        )
        assertTrue(
            manager.from<LuceneIndexedPartitionedEntity>()
                .inPartition("indexed-retained")
                .where("databaseId" eq "retained-database")
                .list<LuceneIndexedPartitionedEntity>()
                .any { it.id == retained.id }
        )

        factory.close()
        initialize()

        assertEquals(
            recreated.id,
            manager.from<LuceneIndexedPartitionedEntity>()
                .inPartition("indexed-delete")
                .where("databaseId" eq "recreated-database")
                .first<LuceneIndexedPartitionedEntity>()
                .id
        )
        assertTrue(
            manager.from<LuceneIndexedPartitionedEntity>()
                .inPartition("indexed-retained")
                .where("databaseId" eq "retained-database")
                .list<LuceneIndexedPartitionedEntity>()
                .any { it.id == retained.id }
        )
    }
}
