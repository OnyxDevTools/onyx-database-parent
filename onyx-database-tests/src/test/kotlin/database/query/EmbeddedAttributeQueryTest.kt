package database.query

import com.onyx.extension.common.get
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.isNull
import com.onyx.persistence.query.notNull
import com.onyx.persistence.query.select
import database.base.PrePopulatedDatabaseTest
import entities.EmbeddedTestEntity
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(Parameterized::class)
class EmbeddedAttributeQueryTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testQueryPredicate() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
            embeddedAttribute = hashMapOf(
                "attribute" to "value",
                "number" to 34,
                "nested" to hashMapOf<String, Any?>(
                    "child" to "childValue",
                    "nullChild" to null
                )
            )
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("embeddedAttribute.attribute" eq "value")
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertEquals(item, entity)
    }

    @Test
    fun testResolver() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("child.name" eq "test")
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertEquals(item, entity)
    }

    @Test
    fun testResolverNegative() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("child.name" eq "tesst")
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertNull(item)
    }

    @Test
    fun testResolverList() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("children.name" eq "test")
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertEquals(item, entity)
    }

    @Test
    fun testResolverListNegative() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("children.name" eq "tests")
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertNull(item)
    }

    @Test
    fun testChildPredicate() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
            embeddedAttribute = hashMapOf(
                "attribute" to "value",
                "number" to 34,
                "nested" to hashMapOf<String, Any?>(
                    "child" to "childValue",
                    "nullChild" to null
                )
            )
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("embeddedAttribute.nested.child" eq "childValue")
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertEquals(item, entity)
    }

    @Test
    fun testNullPredicate() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
            embeddedAttribute = hashMapOf(
                "attribute" to "value",
                "number" to 34,
                "nested" to hashMapOf<String, Any?>(
                    "child" to "childValue",
                    "nullChild" to null
                )
            )
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("embeddedAttribute.nested.nullChild".isNull())
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertEquals(item, entity)
    }

    @Test
    fun testSelect() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
            embeddedAttribute = hashMapOf(
                "attribute" to "value",
                "number" to 34,
                "nested" to hashMapOf<String, Any?>(
                    "child" to "childValue",
                    "nullChild" to null
                )
            )
        }

        manager.save(entity)

        val results = manager
            .from<EmbeddedTestEntity>()
            .where("embeddedAttribute.nested.nullChild".notNull())
            .list<EmbeddedTestEntity>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertNull(item)
    }

    @Test
    fun testSelectQuery() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
            embeddedAttribute = hashMapOf(
                "attribute" to "value",
                "number" to 34,
                "nested" to hashMapOf<String, Any?>(
                    "child" to "childValue",
                    "nullChild" to null
                )
            )
        }

        manager.save(entity)

        val results = manager
            .select("embeddedAttribute")
            .from<EmbeddedTestEntity>()
            .where("embeddedAttribute.attribute" eq "value")
            .list<Map<String, Any>>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertEquals(item?.get("embeddedAttribute"), entity.embeddedAttribute)
    }

    @Test
    fun testSelectChildQuery() {
        val entity = EmbeddedTestEntity().apply {
            id = 1L
            embeddedAttribute = hashMapOf(
                "attribute" to "value",
                "number" to 34,
                "nested" to hashMapOf<String, Any?>(
                    "child" to "childValue",
                    "nullChild" to null
                )
            )
        }

        manager.save(entity)

        val results = manager
            .select("embeddedAttribute.nested.child")
            .from<EmbeddedTestEntity>()
            .where("embeddedAttribute.attribute" eq "value")
            .list<Map<String, Any>>()

        val item = results.firstOrNull()

        assertNotNull(results)
        assertEquals(item?.get("embeddedAttribute.nested.child"), entity.embeddedAttribute["nested"]!!.get("child"))
    }

}