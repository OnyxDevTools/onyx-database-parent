package database.exceptions

import com.onyx.exception.EntityCallbackException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.exception.EntityCallbackExceptionEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class TestCallbackException(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = EntityCallbackException::class)
    fun testPersistCallbackException() {
        val entity = EntityCallbackExceptionEntity()
        manager.saveEntity<IManagedEntity>(entity)
    }

}