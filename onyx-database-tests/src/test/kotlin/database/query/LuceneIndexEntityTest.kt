package database.query

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

@RunWith(Parameterized::class)
class LuceneIndexEntityTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<LuceneIndexedPartitionedEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = listOf(EmbeddedPersistenceManagerFactory::class)
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
}
