package web.stream

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.StreamException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.stream.QueryMapStream
import com.onyx.persistence.stream.QueryStream
import entities.identifiers.ImmutableSequenceIdentifierEntity
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters
import streams.BasicQueryMapStream
import streams.BasicQueryStream
import web.base.BaseTest

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by Tim Osborn on 6/2/16.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(WebServerTests::class)
class EntityStreamTest : BaseTest() {

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

    /**
     * Test a basic Query Stream implementation
     * @throws OnyxException Should not happen
     */
    @Test(expected = StreamException::class)
    @Throws(OnyxException::class)
    fun testBasicQueryStream() {
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        save(testEntity)

        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))

        val hadDataToStream = AtomicBoolean(false)
        manager.stream(query, object :QueryStream<ImmutableSequenceIdentifierEntity> {
            override fun accept(entity: ImmutableSequenceIdentifierEntity, persistenceManager: PersistenceManager) {
                entity.correlation = 2
                hadDataToStream.set(true)
            }
        })

        assert(hadDataToStream.get())
    }

    /**
     * Test a Query Stream implementation with an andThan syntax
     * @throws OnyxException
     */
    @Test(expected = StreamException::class)
    @Ignore
    @Throws(OnyxException::class)
    fun testBasicQueryStreamAndThen() {
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        save(testEntity)

        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))

        val hadDataToStream = AtomicBoolean(false)

        val modifyStream:QueryStream<ImmutableSequenceIdentifierEntity> = object : QueryStream<ImmutableSequenceIdentifierEntity> {
            override fun accept(entity: ImmutableSequenceIdentifierEntity, persistenceManager: PersistenceManager) {
                entity.correlation = 2
                hadDataToStream.set(true)
            }
        }

        val didModifyData = AtomicBoolean(false)

        manager.stream(query, modifyStream)

        assert(hadDataToStream.get())
        assert(didModifyData.get())

    }

    /**
     * Test a basic Query Stream implementation
     * @throws OnyxException Should not happen
     */
    @Test(expected = StreamException::class)
    @Throws(OnyxException::class)
    fun testBasicQueryStreamByClassLoading() {
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        save(testEntity)

        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))
        manager.stream(query, BasicQueryStream::class.java)

        manager.find<IManagedEntity>(testEntity)

        assert(testEntity.correlation == 99)
    }


    /**
     * Test a basic Query Stream implementation
     * @throws OnyxException Should not happen
     */
    @Test(expected = StreamException::class)
    @Throws(OnyxException::class)
    fun testBasicQueryStreamDictionaryByClassLoading() {
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        save(testEntity)

        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))
        manager.stream(query, BasicQueryMapStream::class.java)

        manager.find<IManagedEntity>(testEntity)

        assert(testEntity.correlation == 55)
    }

    /**
     * This is a simple example of how to iterate through the entities as a structure representation.
     * The purpose of this is to display that we can iterate through it without having the dependency
     * of what format the entity used to be in.  In this case, it would help with migrations.
     *
     * @throws OnyxException Should Not happen
     */
    @Test(expected = StreamException::class)
    @Throws(OnyxException::class)
    fun testStreamAsDictionary() {
        // Save some test data
        val testEntity = ImmutableSequenceIdentifierEntityForDelete()
        testEntity.correlation = 1
        save(testEntity)

        // Create query to feed to the stream
        val query = Query(ImmutableSequenceIdentifierEntityForDelete::class.java, QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0))

        val hadDataToStream = AtomicBoolean(false)

        // Create a QueryMapStream as opposed to a QueryStream
        // Create a QueryMapStream as opposed to a QueryStream
        val modifyStream = object : QueryMapStream<MutableMap<String, Any>> {
            override fun accept(entity: MutableMap<String, Any>, persistenceManager: PersistenceManager) {

                // Modify the entity structure
                entity.put("correlation", 5)

                // Remap to the entity so that we can persist it with the changes to the dictionary after we manipulate it.
                val freshEntity = ImmutableSequenceIdentifierEntity()
                freshEntity.fromMap(entity, persistenceManager.context)

                // Save the entity
                try {
                    persistenceManager.saveEntity<ImmutableSequenceIdentifierEntity>(freshEntity)
                    assert(freshEntity.correlation == 5)

                    hadDataToStream.set(true)
                } catch (e: OnyxException) {
                    e.printStackTrace()
                }
            }

        }

        // Kick off the whole thing
        manager.stream(query, modifyStream)

        assert(hadDataToStream.get())
    }
}
