package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.like
import com.onyx.persistence.query.select
import database.base.DatabaseBaseTest
import entities.VectorIndexedPartitionedEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class VectorManagedIndexEntityTest(
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
        manager.from<VectorIndexedPartitionedEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun persistenceManagersToTest(): Collection<Array<Any>> = listOf(
            arrayOf(EmbeddedPersistenceManagerFactory::class, StoreType.FILE),
            arrayOf(EmbeddedPersistenceManagerFactory::class, StoreType.MEMORY_MAPPED_FILE)
        )
    }

    @Test
    fun testSearchNonExistent() {
        manager.from<VectorIndexedPartitionedEntity>()
            .inPartition("iduno")
            .where("value" eq "iduno")
            .list<VectorIndexedPartitionedEntity>()
    }

    @Test
    fun testSearchNonExistentNoPredicate() {
        manager.from<VectorIndexedPartitionedEntity>()
            .inPartition("iduno")
            .list<VectorIndexedPartitionedEntity>()
    }

    @Test
    fun searchVectorFieldWithScore() {
        val entity = VectorIndexedPartitionedEntity().apply { value = "iduno" }
        manager.saveEntity(entity)
        val res = manager
            .select("*", "__score__").from<VectorIndexedPartitionedEntity>()
            .where("value" like "iduno")
            .list<Map<String, Any>>()

        assertNotNull(res)
        assertNotNull(res.firstOrNull())
        assertEquals(1.0f, res[0]["__score__"])
    }

    @Test
    fun deletingSubsequentSearchablePartitionsKeepsFieldIndexWriterOpen() {
        manager.saveEntity(VectorIndexedPartitionedEntity().apply {
            region = "fingerprint-index-first"
            value = "first searchable value"
            databaseId = "database-first"
        })
        manager.saveEntity(VectorIndexedPartitionedEntity().apply {
            region = "fingerprint-index-second"
            value = "second searchable value"
            databaseId = "database-second"
        })

        assertEquals(
            1,
            manager.from<VectorIndexedPartitionedEntity>()
                .inPartition("fingerprint-index-first")
                .delete()
        )
        assertEquals(
            1,
            manager.from<VectorIndexedPartitionedEntity>()
                .inPartition("fingerprint-index-second")
                .delete()
        )
    }

    @Test
    fun deletingSearchablePartitionWithDefaultIndexDoesNotCorruptIndexData() {
        manager.saveEntity(VectorIndexedPartitionedEntity().apply {
            region = "indexed-delete"
            value = "deleted fingerprint content"
            databaseId = "deleted-database"
        })
        val retained = manager.saveEntity(VectorIndexedPartitionedEntity().apply {
            region = "indexed-retained"
            value = "retained fingerprint content"
            databaseId = "retained-database"
        })

        assertEquals(
            1,
            manager.from<VectorIndexedPartitionedEntity>()
                .inPartition("indexed-delete")
                .delete()
        )

        val recreated = manager.saveEntity(VectorIndexedPartitionedEntity().apply {
            region = "indexed-delete"
            value = "recreated fingerprint content"
            databaseId = "recreated-database"
        })
        assertEquals(
            recreated.id,
            manager.from<VectorIndexedPartitionedEntity>()
                .inPartition("indexed-delete")
                .where("databaseId" eq "recreated-database")
                .first<VectorIndexedPartitionedEntity>()
                .id
        )
        assertTrue(
            manager.from<VectorIndexedPartitionedEntity>()
                .inPartition("indexed-retained")
                .where("databaseId" eq "retained-database")
                .list<VectorIndexedPartitionedEntity>()
                .any { it.id == retained.id }
        )

        factory.close()
        initialize()

        assertEquals(
            recreated.id,
            manager.from<VectorIndexedPartitionedEntity>()
                .inPartition("indexed-delete")
                .where("databaseId" eq "recreated-database")
                .first<VectorIndexedPartitionedEntity>()
                .id
        )
        assertTrue(
            manager.from<VectorIndexedPartitionedEntity>()
                .inPartition("indexed-retained")
                .where("databaseId" eq "retained-database")
                .list<VectorIndexedPartitionedEntity>()
                .any { it.id == retained.id }
        )
    }
}
