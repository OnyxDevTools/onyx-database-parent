package database.list

import com.onyx.persistence.query.from
import com.onyx.persistence.query.gt
import com.onyx.persistence.query.isNull
import database.base.DatabaseBaseTest
import entities.NullIndexEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class NullIndexRangeTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun before() {
        manager.from<NullIndexEntity>().delete()
    }

    @Test
    fun rangeQuerySkipsNullIndexes() {
        manager.saveEntity(NullIndexEntity().apply { id = "1"; longIndex = null })
        manager.saveEntity(NullIndexEntity().apply { id = "2"; longIndex = 5L })
        manager.saveEntity(NullIndexEntity().apply { id = "3"; longIndex = 10L })

        val results = manager.from<NullIndexEntity>()
            .where("longIndex" gt 6L)
            .list<NullIndexEntity>()

        assertEquals(1, results.size)
        assertEquals(10L, results[0].longIndex)
    }

    @Test
    fun testIndexIsNull() {
        manager.saveEntity(NullIndexEntity().apply { id = "1"; longIndex = null })
        manager.saveEntity(NullIndexEntity().apply { id = "2"; longIndex = 5L })
        manager.saveEntity(NullIndexEntity().apply { id = "3"; longIndex = 10L })

        val results = manager.from<NullIndexEntity>()
            .where("longIndex".isNull())
            .list<NullIndexEntity>()

        assertEquals(1, results.size)
    }
}

