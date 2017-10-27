package database.exceptions

import com.onyx.exception.*
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.ValidateRequiredIDEntity
import entities.ValidationEntity
import entities.exception.TestValidExtendAbstract
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass

/**
 * Created by timothy.osborn on 1/21/15.
 */
@RunWith(Parameterized::class)
class TestEntityValidation(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = AttributeNonNullException::class)
    fun testNullValue() {
        val validationEntity = ValidationEntity()
        validationEntity.id = 3L
        manager.saveEntity<IManagedEntity>(validationEntity)
    }

    @Test(expected = AttributeSizeException::class)
    fun testAttributeSizeException() {
        val validationEntity = ValidationEntity()
        validationEntity.id = 3L
        validationEntity.requiredString = "ASFD"
        validationEntity.maxSizeString = "ASD1234569a"
        manager.saveEntity<IManagedEntity>(validationEntity)
    }

    @Test
    fun testValidAttributeSizeException() {
        val validationEntity = ValidationEntity()
        validationEntity.id = 3L
        validationEntity.requiredString = "ASFD"
        validationEntity.maxSizeString = "ASD1234569"
        manager.saveEntity<IManagedEntity>(validationEntity)
    }

    @Test(expected = IdentifierRequiredException::class)
    fun testRequiredIDException() {
        val validationEntity = ValidateRequiredIDEntity()
        validationEntity.requiredString = "ASFD"
        validationEntity.maxSizeString = "ASD1234569"
        manager.saveEntity<IManagedEntity>(validationEntity)
    }

    /**
     * Negative test to ensure extending an abstract managed entity still applies to entity
     */
    @Test
    fun testValidObjectAsExtendingAbstract() {
        val obj = TestValidExtendAbstract()
        manager.saveEntity<IManagedEntity>(obj)
        manager.find<IManagedEntity>(obj)
    }
}