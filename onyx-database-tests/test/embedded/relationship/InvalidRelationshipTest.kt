package embedded.relationship

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InvalidRelationshipTypeException
import com.onyx.persistence.IManagedEntity
import embedded.base.BaseTest
import entities.relationship.HasInvalidToMany
import entities.relationship.HasInvalidToOne
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException

/**
 * Created by Tim Osborn on 5/29/16.
 */
@Category(EmbeddedDatabaseTests::class)
class InvalidRelationshipTest : BaseTest() {

    @Before
    @Throws(OnyxException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    @Throws(OnyxException::class)
    fun testInvalidOneToOneWithListType() {
        val myInvalidEntity = HasInvalidToOne()
        myInvalidEntity.identifier = "INVALIDONE"
        manager.saveEntity<IManagedEntity>(myInvalidEntity)
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    @Throws(OnyxException::class)
    fun testInvalidOneToManyWithNonListType() {
        val myInvalidEntity = HasInvalidToMany()
        myInvalidEntity.identifier = "INVALIDONE"
        manager.saveEntity<IManagedEntity>(myInvalidEntity)
    }
}
