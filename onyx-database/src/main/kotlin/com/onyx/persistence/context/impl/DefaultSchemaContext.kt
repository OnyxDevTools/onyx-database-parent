package com.onyx.persistence.context.impl

import com.onyx.descriptor.*
import com.onyx.diskmap.DefaultMapBuilder
import com.onyx.diskmap.MapBuilder
import com.onyx.diskmap.store.StoreType
import com.onyx.entity.*
import com.onyx.exception.EntityClassNotFoundException
import com.onyx.exception.InvalidRelationshipTypeException
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.fetch.ScannerFactory
import com.onyx.helpers.PartitionHelper
import com.onyx.index.IndexController
import com.onyx.index.impl.IndexControllerImpl
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.IdentifierGenerator
import com.onyx.persistence.annotations.RelationshipType
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import com.onyx.persistence.query.impl.DefaultQueryCacheController
import com.onyx.record.RecordController
import com.onyx.record.impl.RecordControllerImpl
import com.onyx.record.impl.SequenceRecordControllerImpl
import com.onyx.relationship.RelationshipController
import com.onyx.relationship.impl.ToManyRelationshipControllerImpl
import com.onyx.relationship.impl.ToOneRelationshipControllerImpl
import com.onyx.transaction.impl.DefaultTransactionStore
import com.onyx.transaction.TransactionController
import com.onyx.transaction.TransactionStore
import com.onyx.transaction.impl.TransactionControllerImpl
import com.onyx.util.EntityClassLoader
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayList
import kotlin.reflect.KClass

/**
 * Schema context that defines local stores for data storage and partitioning. This can only be accessed by a single process. Databases must
 * not have multiple process accessed at the same time.
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.context.SchemaContext
 *
 * @since 1.0.0
 *
 */
// TODO: Move rebuilding of index out
open class DefaultSchemaContext : SchemaContext {

    // region Properties

    // Context id that maps back to the database instance name.  This uniquely identifies a database and is required
    // when having multiple running instances on a single machine.  By default it will be the database location
    final override val contextId: String

    // Location where the database folder is
    final override lateinit var location: String

    // Controls the interaction of how the queries are cached.
    @Suppress("LeakingThis") // Does not matter there should always be a 1 - 1 on database factories and Schema Contexts
    override var queryCacheController: QueryCacheController = DefaultQueryCacheController(this)

    // Wait to initialize when the system persistence manager is set
    override lateinit var transactionController: TransactionController

    // Indicates whether the database has been stopped
    @Volatile override var killSwitch = false

    private lateinit var commitJob:Job

    private lateinit var transactionStore:TransactionStore

    // endregion

    // region Constructors

    constructor() {
        contextId = EmbeddedPersistenceManagerFactory.DEFAULT_INSTANCE
    }

    /**
     * Constructor.
     *
     * @param contextId Database identifier that must be unique and tied to its process
     */
    constructor(contextId: String, location: String) {
        this.contextId = contextId
        this.location = location
        this.transactionStore = DefaultTransactionStore(location)

        @Suppress("LeakingThis")
        Contexts.put(this)

        commitJob = runJob {
            while (!killSwitch) {
                dataFiles.forEach { _, db -> db.commit() }
                delay(10L, TimeUnit.SECONDS)
            }
        }
    }

    // endregion

    // region System Persistence Managers

    /**
     * Returns what persistence manager should be de-serialized when attaching this context to an object through the network. Or lack of
     * network
     */
    override lateinit var serializedPersistenceManager: PersistenceManager

    /**
     * System entity persistence manager.
     *
     * Used to access system level data
     *
     * @since 1.0.0
     */
    override var systemPersistenceManager: PersistenceManager? = null
        set(systemPersistenceManager) {
            field = systemPersistenceManager!!
            serializedPersistenceManager = field!!
            this.transactionController = TransactionControllerImpl(transactionStore, field)
        }

    // endregion

    // region Context Lifecycle - Start/Stop

    /**
     * Start the context and initialize storage or any other IO mechanisms used within the schema context.
     *
     * @since 1.0.0
     */
    override fun start() {
        killSwitch = false
        createTemporaryDiskMapPool()

        initializeSystemEntities()
        initializePartitionSequence()
        initializeEntityDescriptors()
    }

    /**
     * Shutdown schema context. Close files, connections or any other IO mechanisms used within the context
     *
     * @since 1.0.0
     */
    override fun shutdown() {
        killSwitch = true

        // Shutdown all databases
        dataFiles.forEach {
            catchAll {
                it.value.commit()
                it.value.close()
            }
        }

        // Shutdown all databases temporary disk map builders
        temporaryDiskMapQueue.clear()

        // Added to ensure all builders are closed whether they are checked out or not
        temporaryMaps.forEach {
            it.close()
            it.delete()
        }

        // Close transaction file
        transactionStore.close()

        ScannerFactory.getInstance(this).reset()

        dataFiles.clear() // Clear all data files
        descriptors.clear() // Clear all descriptors
        recordControllers.clear() // Clear all Record Controllers
        relationshipControllers.clear() // Clear all relationship controllers
        indexControllers.clear() // Clear all index controllers

        commitJob.cancel()
    }

    // endregion

    // region Initializers

    private val partitionCounter = AtomicLong(0)

    /**
     * The purpose of this is to auto number the partition ids
     */
    private fun initializePartitionSequence() {
        // Get the max partition index
        val indexController = this.getIndexController(descriptors[SystemPartitionEntry::class.java.name]!!.indexes["index"]!!)
        val values = indexController.findAllValues()

        partitionCounter.set(if (values != null && !values.isEmpty()) values.maxBy { it as Long } as Long else 0L)
    }

    /**
     * The purpose of this is to iterate through the system entities and pre-cache all of the entity descriptors
     * So that we can detect schema changes earlier.  For instance an index change can start re-building the index at startup.
     */
    private fun initializeEntityDescriptors() {
        // Added criteria for greater than 7 so that we do not disturb the system entities
        val query = Query(SystemEntity::class.java, QueryCriteria("name", QueryCriteriaOperator.NOT_STARTS_WITH, "com.onyx.entity.System"))
        query.selections = listOf("name")
        val results = serializedPersistenceManager.executeQuery<Map<*, *>>(query)

        results.map { it["name"] as String }.forEach { getBaseDescriptorForEntity(Class.forName(it)) }
    }

    /**
     * This method initializes the metadata needed to get started.  It creates the base level information about the system metadata so that we no longer have to lazy load them
     */
    private fun initializeSystemEntities() {
        val classes:List<KClass<out ManagedEntity>> = listOf(SystemEntity::class,SystemAttribute::class,SystemRelationship::class,SystemIndex::class,SystemIdentifier::class,SystemPartition::class,SystemPartitionEntry::class)
        val systemEntities = ArrayList<SystemEntity>()
        var i = 1

        classes.forEach {
            val descriptor = EntityDescriptor(it.java)
            val systemEntity = SystemEntity(descriptor)
            systemEntity.primaryKey = i

            this.descriptors.put(it.java.name, descriptor)
            this.defaultSystemEntities.put(it.java.name, systemEntity)
            this.systemEntityByIDMap.put(i, systemEntity)
            systemEntities.add(systemEntity)
            i++
        }

        serializedPersistenceManager.saveEntities(systemEntities)
    }

    // endregion

    // region Private Methods for Checking System Entity Changes

    /**
     * This method will detect to see if there are any entity changes.  If so, it will create a new SystemEntity record
     * to reflect the new version and serializer
     *
     * @param descriptor   Base Entity Descriptor
     * @param systemEntityToCheck Current system entity key to base the comparison on the new entity descriptor
     * @return Newly created system entity if it was created otherwise the existing one
     * @throws OnyxException default exception
     * @since 1.1.0
     */
    @Throws(OnyxException::class)
    private fun checkForEntityChanges(descriptor: EntityDescriptor, systemEntityToCheck: SystemEntity): SystemEntity {
        var systemEntity = systemEntityToCheck

        val newSystemEntity = SystemEntity(descriptor)
        if (newSystemEntity != systemEntity) {

            checkForIndexChanges(systemEntity, newSystemEntity)
            checkForInvalidRelationshipChanges(systemEntity, newSystemEntity)

            serializedPersistenceManager.saveEntity<IManagedEntity>(newSystemEntity)
            systemEntity = newSystemEntity
        }

        defaultSystemEntities.put(systemEntity.name, systemEntity)
        systemEntityByIDMap.put(systemEntity.primaryKey, systemEntity)

        return systemEntity
    }

    /**
     * Checks to see if a partition already exists for the corresponding entity descriptor.  If it does not, lets create it.
     *
     * @param descriptor   Entity descriptor to base the new partition on or to cross reference the old one
     * @param systemEntity System entity to get from the database and compare partition on
     * @since 1.1.0
     */
    @Throws(OnyxException::class)
    private fun checkForValidDescriptorPartition(descriptor: EntityDescriptor, systemEntity: SystemEntity) {
        // Check to see if the partition already exists
        if (systemEntity.partition != null && descriptor.partition != null) {
            if (systemEntity.partition!!.entries.filter { it.value == descriptor.partition!!.partitionValue }.count() > 0) {
                return
            }
        }

        // Add a new partition entry if it does not exist
        if (descriptor.partition != null) {
            if (systemEntity.partition == null) {
                systemEntity.partition = SystemPartition(descriptor.partition!!, systemEntity)
            }

            val entry = SystemPartitionEntry(descriptor, descriptor.partition!!, systemEntity.partition!!, partitionCounter.incrementAndGet())
            systemEntity.partition!!.entries.add(entry)
            serializedPersistenceManager.saveEntity<IManagedEntity>(entry)
        }
    }

    /**
     * Check For Index Changes.
     *
     * @param systemEntity System Entity from previous
     * @param newRevision Entity Descriptor with new potential index changes
     */
    private fun checkForIndexChanges(systemEntity: SystemEntity, newRevision: SystemEntity) {
        val oldIndexes = systemEntity.indexes.associate { Pair(it.name, it) }
        val newIndexes = newRevision.indexes.associate { Pair(it.name, it) }

        (oldIndexes - newIndexes).values.forEach { rebuildIndex(systemEntity, it.name) }
    }

    /**
     * Check for valid relationships.
     *
     * @param systemEntity System entity from previous version
     * @param newRevision New revision of system entity
     *
     * @throws InvalidRelationshipTypeException when relationship is invalid
     */
    @Throws(InvalidRelationshipTypeException::class)
    private fun checkForInvalidRelationshipChanges(systemEntity: SystemEntity, newRevision: SystemEntity) {

        val oldRelationships = systemEntity.relationships.associate { Pair(it.name, it) }
        val newRelationships = newRevision.relationships.associate { Pair(it.name, it) }

        (newRelationships - oldRelationships).values.forEach {
            val old = oldRelationships[it.name]

            if (old != null && old.relationshipType.toInt() == RelationshipType.MANY_TO_MANY.ordinal || old != null && old.relationshipType.toInt() == RelationshipType.ONE_TO_MANY.ordinal) {

                if (it.relationshipType == RelationshipType.MANY_TO_ONE.ordinal.toByte() || it.relationshipType == RelationshipType.ONE_TO_ONE.ordinal.toByte()) {
                    throw InvalidRelationshipTypeException(InvalidRelationshipTypeException.CANNOT_UPDATE_RELATIONSHIP)
                }
            }
        }

    }

    /**
     * Method that will re-build an index.  It will perform it for all partitions
     *
     * @param systemEntity Parent System Entity
     * @param indexName Index to rebuild
     */
    private fun rebuildIndex(systemEntity: SystemEntity, indexName: String) {
        catchAll {
            val entityDescriptor = getBaseDescriptorForEntity(systemEntity.className!!)
            val indexDescriptor = entityDescriptor!!.indexes[indexName]
            if (systemEntity.partition != null) {
                systemEntity.partition!!.entries.forEach {
                    val partitionEntityDescriptor = getDescriptorForEntity(entityDescriptor.entityClass, it.value)

                    async {
                        catchAll {
                            getIndexController(partitionEntityDescriptor.indexes[indexDescriptor!!.name]!!).rebuild()
                        }
                    }
                }

            } else {
                async {
                    catchAll {
                        getIndexController(indexDescriptor!!).rebuild()
                    }
                }
            }
        }
    }

    // endregion

    // region System Entity

    private val systemEntityByIDMap = BlockingHashMap<Int, SystemEntity?>()
    private val defaultSystemEntities = BlockingHashMap<String, SystemEntity?>()

    /**
     * Get System Entity By Name.
     *
     * @param name System Entity Name
     * @return Latest System Entity matching that name
     * @throws OnyxException Default Exception
     */
    @Throws(OnyxException::class)
    override fun getSystemEntityByName(name: String): SystemEntity? = runBlockingOn(defaultSystemEntities) {
        defaultSystemEntities.getOrPut(name) {
            val query = Query(SystemEntity::class.java, QueryCriteria("name", QueryCriteriaOperator.EQUAL, name), QueryOrder("primaryKey", false))
            query.maxResults = 1

            val result = serializedPersistenceManager.executeQuery<SystemEntity>(query).firstOrNull()
            if (result != null) {
                result.attributes.sortBy { it.name }
                result.relationships.sortBy { it.name }
                result.indexes.sortBy { it.name }
            }
            return@getOrPut result
        }
    }

    /**
     * Get System Entity By ID.
     *
     * @param systemEntityId Unique identifier for system entity version
     * @return System Entity matching ID
     */
    override fun getSystemEntityById(systemEntityId: Int): SystemEntity? = runBlockingOn(systemEntityByIDMap) {
        systemEntityByIDMap.getOrPut(systemEntityId) {
            val entity = serializedPersistenceManager.findById<SystemEntity>(SystemEntity::class.java, systemEntityId)

            if (entity != null) {
                entity.attributes.sortBy { it.name }
                entity.relationships.sortBy { it.name }
                entity.indexes.sortBy { it.name }
            }
            return@getOrPut entity
        }
    }

    // endregion

    // region Entity Descriptor

    private val descriptors = BlockingHashMap<String, EntityDescriptor>()

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * @param entity      Entity Instance
     * @param partitionId Partition Field Value
     * @return Record's entity descriptor
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getDescriptorForEntity(entity: IManagedEntity, partitionId: Any?): EntityDescriptor = getDescriptorForEntity(entity.javaClass, partitionId)

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * @param entityClass Entity Type
     * @return Entity Descriptor for class
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getBaseDescriptorForEntity(entityClass: Class<*>): EntityDescriptor? = getDescriptorForEntity(entityClass, "")

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * @param entityClass Entity Type
     * @return Entity Descriptor for class
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    fun getBaseDescriptorForEntity(entityClass: String): EntityDescriptor? = getDescriptorForEntity(Class.forName(entityClass))

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * @param entityClass Entity Type
     * @param partitionId Partition Id
     * @return Entity Descriptor for class and partition id
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getDescriptorForEntity(entityClass: Class<*>?, partitionId: Any?): EntityDescriptor {

        if (entityClass == null)
            throw EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND)

        val partitionIdVar = partitionId ?: ""
        val entityKey = entityClass.name + partitionIdVar.toString()

        return runBlockingOn(descriptors) {

            descriptors.getOrElse(entityKey, {
                val descriptor = EntityDescriptor(entityClass)
                descriptor.partition?.partitionValue = partitionIdVar.toString()

                descriptors.put(entityKey, descriptor)

                // Get the latest System Entity
                var systemEntity: SystemEntity? = getSystemEntityByName(descriptor.entityClass.name)

                // If it does not exist, lets create a new one
                if (systemEntity == null) {
                    systemEntity = SystemEntity(descriptor)
                    serializedPersistenceManager.saveEntity<IManagedEntity>(systemEntity)
                }

                checkForValidDescriptorPartition(descriptor, systemEntity)
                checkForEntityChanges(descriptor, systemEntity)

                EntityClassLoader.writeClass(systemEntity, location, this@DefaultSchemaContext)

                return@getOrElse descriptor
            })
        }

    }

    /**
     * Get Descriptor and have it automatically determine the partition ID.
     *
     * @param entity Entity Instance
     * @return Record's entity descriptor
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getDescriptorForEntity(entity: Any): EntityDescriptor {
        if (entity !is IManagedEntity) {
            throw EntityClassNotFoundException(EntityClassNotFoundException.PERSISTED_NOT_FOUND, entity.javaClass)
        }

        val partitionId = PartitionHelper.getPartitionFieldValue(entity, this)
        return getDescriptorForEntity(entity, partitionId)
    }

    // endregion

    // region Relationship Controllers

    private val relationshipControllers = BlockingHashMap<RelationshipDescriptor, RelationshipController>()

    /**
     * Get Relationship Controller that corresponds to the relationship descriptor.
     *
     *
     *
     * This is not meant to be a public API.
     *
     * @param relationshipDescriptor Relationship Descriptor
     * @return Relationship Controller corresponding to relationship descriptor
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getRelationshipController(relationshipDescriptor: RelationshipDescriptor): RelationshipController = runBlockingOn(relationshipControllers) {
        relationshipControllers.getOrPut(relationshipDescriptor) {
            return@getOrPut if (relationshipDescriptor.relationshipType == RelationshipType.MANY_TO_MANY || relationshipDescriptor.relationshipType == RelationshipType.ONE_TO_MANY) {
                ToManyRelationshipControllerImpl(relationshipDescriptor.entityDescriptor, relationshipDescriptor, this)
            } else {
                ToOneRelationshipControllerImpl(relationshipDescriptor.entityDescriptor, relationshipDescriptor, this)
            }
        }
    }

    // endregion

    // region Index Controller

    private val indexControllers = BlockingHashMap<IndexDescriptor, IndexController>()

    /**
     * Get Index Controller with Index descriptor.
     * This is not meant to be a public API.
     *
     * @param indexDescriptor Index Descriptor
     * @return Corresponding record controller
     * @since 1.0.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun getIndexController(indexDescriptor: IndexDescriptor): IndexController = runBlockingOn(indexControllers) {
        indexControllers.getOrPut(indexDescriptor) {
            return@getOrPut IndexControllerImpl(indexDescriptor.entityDescriptor, indexDescriptor, this)
        }
    }

    // endregion

    // region Record Controller

    private val recordControllers = BlockingHashMap<EntityDescriptor, RecordController>()

    /**
     * Get Record Controller.
     *
     * @param descriptor Record's Entity Descriptor
     * @return get Record Controller.
     * @since 1.0.0
     */
    override fun getRecordController(descriptor: EntityDescriptor): RecordController = runBlockingOn(recordControllers) {
        recordControllers.getOrPut(descriptor) {
            if (descriptor.identifier!!.generator == IdentifierGenerator.SEQUENCE) SequenceRecordControllerImpl(descriptor, this@DefaultSchemaContext) else RecordControllerImpl(descriptor, this@DefaultSchemaContext)
        }
    }

    // endregion

    // region Data Files

    @JvmField internal val dataFiles = BlockingHashMap<String, MapBuilder>()

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor.
     *
     * This is not meant to be a public API.
     *
     * @param descriptor Record Entity Descriptor
     * @return Underlying data storage factory
     * @since 1.0.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun getDataFile(descriptor: EntityDescriptor): MapBuilder {
        val key = descriptor.fileName + if (descriptor.partition == null) "" else descriptor.partition!!.partitionValue
        return runBlockingOn(dataFiles) {
            dataFiles.getOrPut(key) {
                return@getOrPut DefaultMapBuilder("$location/$key", this@DefaultSchemaContext)
            }
        }
    }

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor that pertains to a partitionID.
     *
     * @param descriptor Record Entity Descriptor
     * @param partitionId    Partition the records belong to
     * @return Underlying data storage factory
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getPartitionDataFile(descriptor: EntityDescriptor, partitionId: Long): MapBuilder {

        if (partitionId == 0L) {
            return getDataFile(descriptor)
        }

        val query = Query(SystemPartitionEntry::class.java, QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId))
        val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
        val partition = partitions[0]

        return getDataFile(getDescriptorForEntity(descriptor.entityClass, partition.value))
    }

    // endregion

    // region Map Builder Queue

    @JvmField
    protected val temporaryMaps: MutableSet<MapBuilder> = HashSet()

    @JvmField
    protected val temporaryDiskMapQueue = ArrayBlockingQueue<MapBuilder>(32, false)

    /**
     * This method will create a pool of map builders.  They are used to run queries
     * and then be recycled and put back on the queue.
     *
     * @since 1.3.0
     */
    open protected fun createTemporaryDiskMapPool() {
        val temporaryFileLocation = this.location + File.separator + "temporary"
        File(temporaryFileLocation).mkdirs()

        for (i in 0..31) {
            val stringBuilder = temporaryFileLocation + File.separator + System.currentTimeMillis().toString() + BigInteger(20, random).toString(32)

            val file = File(stringBuilder)
            file.createNewFile()
            file.deleteOnExit() // Must delete since there is no more functionality to delete from references

            val builder = DefaultMapBuilder(file.path, StoreType.MEMORY_MAPPED_FILE, this, true)
            temporaryDiskMapQueue.add(builder)
            temporaryMaps.add(builder)
        }
    }

    /**
     * Create Temporary Map Builder.
     *
     * @return Create new storage mechanism factory
     * @since 1.3.0 Changed to use a pool of map builders.
     * The intent of this is to increase performance.  There was a performance
     * issue with map builders being destroyed invoking the DirectBuffer cleanup.
     * That was not performant
     */
    override fun createTemporaryMapBuilder(): MapBuilder = temporaryDiskMapQueue.take()

    /**
     * Recycle a temporary map builder so that it may be re-used
     *
     * @param mapBuilder Discarded map builder
     * @since 1.3.0
     */
    override fun releaseMapBuilder(mapBuilder: MapBuilder) {
        mapBuilder.reset()
        temporaryDiskMapQueue.offer(mapBuilder)
    }

    // endregion

    // region Partitions

    /**
     * Get Partition Entry for entity.
     *
     * @param classToGet     Entity type for record
     * @param partitionValue Partition Value
     * @return System Partition Entry for class with partition key
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getPartitionWithValue(classToGet: Class<*>, partitionValue: Any): SystemPartitionEntry? {
        val query = Query(SystemPartitionEntry::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, classToGet.name + partitionValue.toString()))
        val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
        return if (partitions.isEmpty()) null else partitions[0]
    }

    /**
     * Get System Partition with Id.
     *
     * @param partitionId Partition ID
     * @return System Partition Entry for class with partition id
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun getPartitionWithId(partitionId: Long): SystemPartitionEntry? {
        val query = Query(SystemPartitionEntry::class.java, QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId))
        val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
        return if (partitions.isEmpty()) null else partitions[0]
    }

    // endregion

    companion object {
        // Random generator for generating random temporary file names
        private val random = SecureRandom()
    }
}