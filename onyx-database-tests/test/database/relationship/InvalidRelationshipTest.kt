package database.relationship

import com.onyx.exception.InvalidRelationshipTypeException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.relationship.HasInvalidToMany
import entities.relationship.HasInvalidToOne
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class InvalidRelationshipTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = InvalidRelationshipTypeException::class)
    fun testInvalidOneToOneWithListType() {
        val myInvalidEntity = HasInvalidToOne()
        myInvalidEntity.identifier = "INVALIDONE"
        manager.saveEntity<IManagedEntity>(myInvalidEntity)
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    fun testInvalidOneToManyWithNonListType() {
        val myInvalidEntity = HasInvalidToMany()
        myInvalidEntity.identifier = "INVALIDONE"
        manager.saveEntity<IManagedEntity>(myInvalidEntity)
    }
}
