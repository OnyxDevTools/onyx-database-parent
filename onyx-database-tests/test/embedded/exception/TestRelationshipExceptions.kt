package embedded.exception

import category.EmbeddedDatabaseTests
import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.*
import embedded.base.BaseTest
import entities.AllAttributeEntity
import entities.exception.EntityToOneDoesNotMatch
import entities.exception.NoInverseEntity
import entities.exception.OTMNoListEntity
import entities.exception.RelationshipNoEntityType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category(EmbeddedDatabaseTests::class)
class TestRelationshipExceptions : BaseTest() {

    @Before
    @Throws(InitializationException::class)
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
    fun testInvalidInverse() {
        val (entityClass, entity, identifier, partition, preUpdateCallback, preInsertCallback, preRemoveCallback, prePersistCallback, postUpdateCallback, postInsertCallback, postRemoveCallback, postPersistCallback, attributes, indexes, relationships) = EntityDescriptor(NoInverseEntity::class.java)
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    @Throws(OnyxException::class)
    fun testInvalidType() {
        val (entityClass, entity, identifier, partition, preUpdateCallback, preInsertCallback, preRemoveCallback, prePersistCallback, postUpdateCallback, postInsertCallback, postRemoveCallback, postPersistCallback, attributes, indexes, relationships) = EntityDescriptor(OTMNoListEntity::class.java)
    }

    @Test(expected = EntityClassNotFoundException::class)
    @Throws(OnyxException::class)
    fun testInvalidTypeInverse() {
        val (entityClass, entity, identifier, partition, preUpdateCallback, preInsertCallback, preRemoveCallback, prePersistCallback, postUpdateCallback, postInsertCallback, postRemoveCallback, postPersistCallback, attributes, indexes, relationships) = EntityDescriptor(RelationshipNoEntityType::class.java)
    }

    @Test(expected = InvalidRelationshipTypeException::class)
    @Throws(OnyxException::class)
    fun testInvalidTypeInverseNoMatch() {
        val (entityClass, entity, identifier, partition, preUpdateCallback, preInsertCallback, preRemoveCallback, prePersistCallback, postUpdateCallback, postInsertCallback, postRemoveCallback, postPersistCallback, attributes, indexes, relationships) = EntityDescriptor(EntityToOneDoesNotMatch::class.java)
    }


    @Test(expected = RelationshipNotFoundException::class)
    @Throws(OnyxException::class)
    fun testRelationshipNotFound() {
        val entity = AllAttributeEntity()
        entity.id = "ZZZ"
        save(entity)

        manager.initialize(entity, "ASDFASDF")
    }

}
