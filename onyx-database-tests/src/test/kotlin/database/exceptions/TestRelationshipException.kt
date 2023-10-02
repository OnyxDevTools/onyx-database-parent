package database.exceptions

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.*
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.AllAttributeEntity
import entities.exception.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class TestRelationshipExceptions(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = InvalidRelationshipTypeException::class)
    fun testInvalidInverse() {
        EntityDescriptor(NoInverseEntity::class.java)
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    fun testInvalidType() {
        EntityDescriptor(OTMNoListEntity::class.java)
    }

    @Test(expected = EntityClassNotFoundException::class)
    fun testInvalidTypeInverse() {
        EntityDescriptor(RelationshipNoEntityType::class.java)
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    fun testInvalidTypeInverseNoMatch() {
        EntityDescriptor(EntityToOneDoesNotMatch::class.java)
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    fun testInvalidTypeIdInverseNoMatch() {
        EntityDescriptor(EntityToOneIdDoesNotMatch::class.java)
    }

    @Test(expected = RelationshipNotFoundException::class)
    @Throws(OnyxException::class)
    fun testRelationshipNotFound() {
        val entity = AllAttributeEntity()
        entity.id = "ZZZ"
        manager.saveEntity<IManagedEntity>(entity)
        manager.initialize(entity, "ASDFASDF")
    }
}