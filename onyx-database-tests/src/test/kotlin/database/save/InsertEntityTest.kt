package database.save

import com.onyx.application.impl.DatabaseServer
import com.onyx.diskmap.store.StoreType
import com.onyx.exception.EntityAlreadyExistsException
import com.onyx.exception.IdentifierRequiredException
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import entities.AddressNoPartition
import entities.EntityWithCallbacks
import entities.NullIndexEntity
import entities.PersonNoPartition
import entities.identifiers.MutableSequenceIdentifierEntity
import entities.identifiers.StringIdentifierEntity
import entities.partition.IndexPartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class InsertEntityTest(private val factoryClass: KClass<*>, private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: PersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private var server: DatabaseServer? = null
    private val randomString: String get() = UUID.randomUUID().toString()

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-insert-")
        val location = directory.resolve("database").toString()
        factory = when (factoryClass) {
            CacheManagerFactory::class -> CacheManagerFactory(location, location)
            RemotePersistenceManagerFactory::class -> {
                val port = ServerSocket(0).use { it.localPort }
                server = DatabaseServer(location).apply {
                    this.port = port
                    this.storeType = this@InsertEntityTest.storeType
                    setCredentials("admin", "admin")
                    start()
                }
                RemotePersistenceManagerFactory("onx://localhost:$port")
            }
            else -> EmbeddedPersistenceManagerFactory(location)
        }
        factory.storeType = storeType
        factory.setCredentials("admin", "admin")
        factory.initialize()
        manager = factory.persistenceManager
    }

    @After
    fun cleanup() {
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            try {
                server?.stop()
            } finally {
                if (::directory.isInitialized) directory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `insert rejects duplicates while save still updates and deletion permits reinsertion`() {
        val original = StringIdentifierEntity().apply { identifier = randomString; correlation = 1 }
        assertSame(original, manager.insert(original))
        val duplicate = StringIdentifierEntity().apply { identifier = original.identifier; correlation = 2 }

        assertFailsWith<EntityAlreadyExistsException> { manager.insertEntity(duplicate) }
        assertEquals(1, manager.findById<StringIdentifierEntity>(original.javaClass, original.identifier!!)!!.correlation)

        manager.save(duplicate)
        assertEquals(2, manager.findById<StringIdentifierEntity>(original.javaClass, original.identifier!!)!!.correlation)
        assertTrue(manager.delete(duplicate))
        manager.insert(duplicate)
        assertEquals(2, manager.findById<StringIdentifierEntity>(original.javaClass, original.identifier!!)!!.correlation)
    }

    @Test
    fun `insert generates sequence identifiers and rejects an explicit duplicate`() {
        val first = manager.insert(MutableSequenceIdentifierEntity().apply { correlation = 1 })
        val second = manager.insertEntity(MutableSequenceIdentifierEntity().apply { identifier = 0L })
        assertNotNull(first.identifier)
        assertTrue(first.identifier!! > 0L)
        assertNotEquals(first.identifier, second.identifier)

        assertFailsWith<EntityAlreadyExistsException> {
            manager.insert(MutableSequenceIdentifierEntity().apply { identifier = first.identifier; correlation = 2 })
        }
        assertEquals(1, manager.findById<MutableSequenceIdentifierEntity>(first.javaClass, first.identifier!!)!!.correlation)
    }

    @Test
    fun `insert generates UUIDs and leaves existing indexes and cached queries intact on duplicate`() {
        val indexValue = randomString
        val original = NullIndexEntity().apply { idx = indexValue }
        assertSame(original, manager.insert(original))
        assertTrue(original.id.isNotBlank())
        val second = manager.insert(NullIndexEntity())
        assertNotEquals(original.id, second.id)

        val cachedQuery = Query(NullIndexEntity::class.java, "idx" eq indexValue).apply { cache = true }
        assertEquals(listOf(original.id), manager.executeQuery<NullIndexEntity>(cachedQuery).map { it.id })
        val rejectedValue = randomString
        assertFailsWith<EntityAlreadyExistsException> {
            manager.insert(NullIndexEntity().apply { id = original.id; idx = rejectedValue })
        }

        assertEquals(indexValue, manager.findById<NullIndexEntity>(original.javaClass, original.id)!!.idx)
        assertEquals(listOf(original.id), manager.executeQuery<NullIndexEntity>(cachedQuery).map { it.id })
        assertEquals(1L, manager.from<NullIndexEntity>().where("idx" eq indexValue).count())
        assertEquals(0L, manager.from<NullIndexEntity>().where("idx" eq rejectedValue).count())
    }

    @Test
    fun `duplicate identifiers are scoped to the partition including the default partition`() {
        val partition = System.nanoTime()
        val identifier = System.nanoTime()
        for (partitionValue in listOf(null, partition, partition + 1)) {
            val entity = IndexPartitionEntity().apply {
                id = identifier
                partitionId = partitionValue
                indexVal = 1L
            }
            manager.insert(entity)
            assertFailsWith<EntityAlreadyExistsException> {
                manager.insert(IndexPartitionEntity().apply {
                    id = identifier
                    partitionId = partitionValue
                    indexVal = 2L
                })
            }
            assertEquals(1L, manager.find<IndexPartitionEntity>(entity).indexVal)
        }
    }

    @Test
    fun `insert runs insert callbacks and duplicates do not run persistence callbacks`() {
        val original = manager.insert(EntityWithCallbacks().apply { id = randomString; name = "original" })
        assertTrue(original.name!!.contains("_PreInsert"))
        assertTrue(original.name!!.contains("_PostInsert"))
        assertTrue(original.name!!.contains("_PrePersist"))
        assertTrue(original.name!!.contains("_PostPersist"))
        val persistedName = manager.findById<EntityWithCallbacks>(original.javaClass, original.id!!)!!.name
        val duplicate = EntityWithCallbacks().apply { id = original.id; name = "duplicate" }

        assertFailsWith<EntityAlreadyExistsException> { manager.insert(duplicate) }
        assertEquals("duplicate", duplicate.name)
        assertEquals(persistedName, manager.findById<EntityWithCallbacks>(original.javaClass, original.id!!)!!.name)
    }

    @Test
    fun `insert cascades saves but a duplicate does not cascade relationship changes`() {
        val address = AddressNoPartition().apply { street = "original"; houseNr = 1 }
        val person = PersonNoPartition().apply { firstName = "First"; lastName = "Last"; this.address = address }
        manager.insert(person)
        val saved = manager.findById<PersonNoPartition>(person.javaClass, person.id!!)!!
        manager.initialize(saved, "address")
        val addressId = saved.address!!.id!!
        assertEquals("original", manager.findById<AddressNoPartition>(address.javaClass, addressId)!!.street)

        val changedAddress = AddressNoPartition().apply { id = addressId; street = "changed"; houseNr = 2 }
        assertFailsWith<EntityAlreadyExistsException> {
            manager.insert(PersonNoPartition().apply {
                id = person.id
                firstName = "Changed"
                lastName = "Last"
                this.address = changedAddress
            })
        }
        assertEquals("original", manager.findById<AddressNoPartition>(address.javaClass, addressId)!!.street)
    }

    @Test
    fun `insert validates a required identifier`() {
        assertFailsWith<IdentifierRequiredException> { manager.insert(StringIdentifierEntity()) }
    }

    @Test
    fun `concurrent inserts of one identifier have exactly one winner`() {
        val identifier = randomString
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..24).map { value ->
                executor.submit<Int?> {
                    check(start.await(10, TimeUnit.SECONDS))
                    try {
                        manager.insert(StringIdentifierEntity().apply { this.identifier = identifier; correlation = value })
                        value
                    } catch (_: EntityAlreadyExistsException) {
                        null
                    }
                }
            }
            start.countDown()
            val winners = attempts.mapNotNull { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, winners.size)
            assertEquals(winners.single(), manager.findById<StringIdentifierEntity>(StringIdentifierEntity::class.java, identifier)!!.correlation)
        } finally {
            executor.shutdownNow()
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
        fun persistenceManagersToTest(): Collection<Array<Any>> = listOf(
            arrayOf(EmbeddedPersistenceManagerFactory::class, StoreType.MEMORY_MAPPED_FILE),
            arrayOf(EmbeddedPersistenceManagerFactory::class, StoreType.FILE),
            arrayOf(CacheManagerFactory::class, StoreType.IN_MEMORY),
            arrayOf(RemotePersistenceManagerFactory::class, StoreType.MEMORY_MAPPED_FILE),
        )
    }
}
