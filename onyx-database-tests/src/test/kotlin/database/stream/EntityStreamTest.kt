package database.stream

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.from
import com.onyx.persistence.stream.QueryMapStream
import com.onyx.persistence.stream.QueryStream
import database.base.DatabaseBaseTest
import entities.identifiers.ImmutableSequenceIdentifierEntity
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import streams.BasicQueryMapStream
import streams.BasicQueryStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class EntityStreamTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    /**
     * Test a basic Query Stream implementation
     */
    @Test
    fun testBasicQueryStream() {
        Assume.assumeFalse("Unsupported feature for remote persistence manager", factory is RemotePersistenceManagerFactory)

        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        manager.saveEntity<IManagedEntity>(testEntity)

        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))

        val hadDataToStream = AtomicBoolean(false)
        manager.stream(query, object : QueryStream<ImmutableSequenceIdentifierEntityForDelete> {
            override fun accept(entity: ImmutableSequenceIdentifierEntityForDelete, persistenceManager: PersistenceManager): Boolean {
                entity.correlation = 2
                hadDataToStream.set(true)
                return true
            }
        })

        manager.from<ImmutableSequenceIdentifierEntity>()
            .stream<ImmutableSequenceIdentifierEntity> { item ->

                true
            }

        assertTrue(hadDataToStream.get(), "Stream had data to iterate")
    }



    /**
     * Test a basic Query Stream implementation
     */
    @Test
    fun testBasicQueryStreamByClassLoading() {
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        manager.saveEntity<IManagedEntity>(testEntity)

        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))
        manager.stream(query, BasicQueryStream::class.java)
        manager.find<IManagedEntity>(testEntity)

        assertEquals(99, testEntity.correlation, "Stream successfully updated entities")
    }


    /**
     * Test a basic Query Stream implementation
     */
    @Test
    fun testBasicQueryStreamDictionaryByClassLoading() {
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        manager.saveEntity<IManagedEntity>(testEntity)

        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))
        manager.stream(query, BasicQueryMapStream::class.java)

        manager.find<IManagedEntity>(testEntity)

        assertEquals(55, testEntity.correlation, "Stream successfully updated entities")
    }

    /**
     * This is a simple example of how to iterate through the entities as a structure representation.
     * The purpose of this is to display that we can iterate through it without having the dependency
     * of what format the entity used to be in.  In this case, it would help with migrations.
     *
     */
    @Test
    fun testStreamAsDictionary() {
        Assume.assumeFalse("Unsupported feature for remote persistence manager", factory is RemotePersistenceManagerFactory)

        // Save some test data
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        manager.saveEntity<IManagedEntity>(testEntity)

        // Create query to feed to the stream
        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))

        val hadDataToStream = AtomicBoolean(false)

        // Create a QueryMapStream as opposed to a QueryStream
        // Create a QueryMapStream as opposed to a QueryStream
        val modifyStream = object : QueryMapStream<MutableMap<String, Any>> {
            override fun accept(entity: MutableMap<String, Any>, persistenceManager: PersistenceManager): Boolean {

                // Modify the entity structure
                entity.put("correlation", 5)

                // Remap to the entity so that we can persist it with the changes to the dictionary after we manipulate it.
                val freshEntity = ImmutableSequenceIdentifierEntity()
                freshEntity.fromMap(entity, persistenceManager.context)

                // Save the entity
                persistenceManager.saveEntity(freshEntity)
                assertEquals(5, freshEntity.correlation, "Stream updated dictionary value")

                hadDataToStream.set(true)
                return true
            }

        }

        // Kick off the whole thing
        manager.stream(query, modifyStream)

        assertTrue(hadDataToStream.get(), "Stream had data to iterate")
    }

    companion object {

        /**
         * do not run this for web because it only supports a subst of features for streaming
         */
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(CacheManagerFactory::class, EmbeddedPersistenceManagerFactory::class, RemotePersistenceManagerFactory::class)
    }

}

