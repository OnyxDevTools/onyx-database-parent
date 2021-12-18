package com.onyx.persistence.context.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.store.StoreType
import com.onyx.entity.*
import com.onyx.exception.EntityClassNotFoundException
import com.onyx.exception.InvalidRelationshipTypeException
import com.onyx.exception.OnyxException
import com.onyx.extension.nullPartition
import com.onyx.extension.common.ClassMetadata.classForName
import com.onyx.extension.common.async
import com.onyx.extension.common.catchAll
import com.onyx.extension.createNewEntity
import com.onyx.extension.get
import com.onyx.interactors.cache.QueryCacheInteractor
import com.onyx.interactors.cache.impl.DefaultQueryCacheInteractor
import com.onyx.interactors.encryption.EncryptionInteractor
import com.onyx.interactors.index.IndexInteractor
import com.onyx.interactors.index.impl.DefaultIndexInteractor
import com.onyx.interactors.record.RecordInteractor
import com.onyx.interactors.record.impl.DefaultRecordInteractor
import com.onyx.interactors.record.impl.SequenceRecordInteractor
import com.onyx.interactors.relationship.RelationshipInteractor
import com.onyx.interactors.relationship.impl.ToManyRelationshipInteractor
import com.onyx.interactors.relationship.impl.ToOneRelationshipInteractor
import com.onyx.interactors.transaction.TransactionInteractor
import com.onyx.interactors.transaction.TransactionStore
import com.onyx.interactors.transaction.impl.DefaultTransactionInteractor
import com.onyx.interactors.transaction.impl.DefaultTransactionStore
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
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
open class DefaultSchemaContext : SchemaContext {

    // region Properties

    // Context id that maps back to the database instance name.  This uniquely identifies a database and is required
    // when having multiple running instances on a single machine.  By default it will be the database location
    final override val contextId: String

    override var storeType: StoreType = StoreType.MEMORY_MAPPED_FILE
    override var encryption: EncryptionInteractor? = null
    override var encryptDatabase: Boolean = false

    // Location where the database folder is
    final override lateinit var location: String

    // Controls the interaction of how the queries are cached.
    // Does not matter there should always be a 1 - 1 on database factories and Schema Contexts
    @Suppress("LeakingThis")
    override var queryCacheInteractor: QueryCacheInteractor = DefaultQueryCacheInteractor(this)

    // Wait to initialize when the system persistence manager is set
    override lateinit var transactionInteractor: TransactionInteractor

    // Indicates whether the database has been stopped
    @Volatile override var killSwitch = false

    private var transactionStore: TransactionStore? = null

    // Class loader to dynamically add classes
    override var classLoader:ClassLoader = DefaultSchemaContext::class.java.classLoader

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
            if(transactionStore != null)
                this.transactionInteractor = DefaultTransactionInteractor(transactionStore!!, field!!)
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

        // Close transaction file
        catchAll { transactionStore?.close() }
        dataFiles.clear() // Clear all data files
        descriptors.clear() // Clear all descriptors
        recordInteractors.clear() // Clear all Record Controllers
        relationshipInteractors.clear() // Clear all relationship controllers
        indexInteractors.clear() // Clear all index controllers

    }

    // endregion

    // region Initializer

    private val partitionCounter = AtomicLong(0)

    /**
     * The purpose of this is to auto number the partition ids
     */
    @Suppress("MemberVisibilityCanPrivate")
    protected open fun initializePartitionSequence() {
        // Get the max partition index
        val indexInteractor = this.getIndexInteractor(descriptors[SystemPartitionEntry::class.java.name]!!.indexes["index"]!!)
        val values = indexInteractor.findAllValues()

        partitionCounter.set(if (values.isNotEmpty()) values.maxByOrNull { it as Long } as Long else 0L)
    }

    /**
     * The purpose of this is to iterate through the system entities and pre-cache all of the entity descriptors
     * So that we can detect schema changes earlier.  For instance an index change can start re-building the index at startup.
     */
    @Suppress("MemberVisibilityCanPrivate")
    protected open fun initializeEntityDescriptors() {
        // Added criteria for greater than 7 so that we do not disturb the system entities
        val results = serializedPersistenceManager.select("name").from(SystemEntity::class)
                .where(("isLatestVersion" eq true) and ("name" notStartsWith "com.onyx.entity.System"))
                .list<Map<String, String>>()

        results.map { it["name"] }.forEach { getBaseDescriptorForEntity(classForName(it!!, this)) }
    }

    /**
     * This method initializes the metadata needed to get started.  It creates the base level information about the system metadata so that we no longer have to lazy load them
     */
    @Suppress("MemberVisibilityCanPrivate")
    protected open fun initializeSystemEntities() {
        val classes:List<KClass<out ManagedEntity>> = arrayListOf(SystemEntity::class,SystemAttribute::class,SystemRelationship::class,SystemIndex::class,SystemIdentifier::class,SystemPartition::class,SystemPartitionEntry::class)
        val systemEntities = ArrayList<SystemEntity>()
        var i = 1

        classes.forEach {
            val descriptor = EntityDescriptor(it.java)
            descriptor.context = this
            val systemEntity = SystemEntity(descriptor)
            systemEntity.primaryKey = i

            this.descriptors[it.java.name] = descriptor
            this.defaultSystemEntities[it.java.name] = systemEntity
            this.systemEntityByIDMap[i] = systemEntity
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
    @Suppress("MemberVisibilityCanPrivate")
    protected open fun checkForEntityChanges(descriptor: EntityDescriptor, systemEntityToCheck: SystemEntity): SystemEntity {
        var systemEntity = systemEntityToCheck

        val newSystemEntity = SystemEntity(descriptor)
        if (newSystemEntity != systemEntity) {

            checkForIndexChanges(systemEntity, newSystemEntity)
            checkForInvalidRelationshipChanges(systemEntity, newSystemEntity)

            if(newSystemEntity.partition != null
                    && systemEntity.partition != null
                    && newSystemEntity.partition == systemEntity.partition)
                newSystemEntity.partition = systemEntity.partition

            serializedPersistenceManager.from(SystemEntity::class).where("name" eq systemEntity.name).set("isLatestVersion" to false).update()
            serializedPersistenceManager.saveEntity<IManagedEntity>(newSystemEntity)
            systemEntity = newSystemEntity
        }

        defaultSystemEntities[systemEntity.name] = systemEntity
        systemEntityByIDMap[systemEntity.primaryKey] = systemEntity

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
        val oldIndexes = systemEntity.indexes.associateBy { it.name }
        val newIndexes = newRevision.indexes.associateBy { it.name }

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

        val oldRelationships = systemEntity.relationships.associateBy { it.name }
        val newRelationships = newRevision.relationships.associateBy { it.name }

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
    private fun rebuildIndex(systemEntity: SystemEntity, indexName: String) = catchAll {
        val entityDescriptor = getBaseDescriptorForEntity(systemEntity.className!!)
        val indexDescriptor = entityDescriptor.indexes[indexName]
        if (systemEntity.partition != null) {
            systemEntity.partition!!.entries.forEach {
                val partitionEntityDescriptor = getDescriptorForEntity(entityDescriptor.entityClass, it.value)

                async {
                    catchAll {
                        getIndexInteractor(partitionEntityDescriptor.indexes[indexDescriptor!!.name]!!).rebuild()
                    }
                }
            }

        } else {
            async {
                catchAll {
                    getIndexInteractor(indexDescriptor!!).rebuild()
                }
            }
        }
    }

    // endregion

    // region System Entity

    @Suppress("MemberVisibilityCanPrivate")
    protected val systemEntityByIDMap = HashMap<Int, SystemEntity?>()
    @Suppress("MemberVisibilityCanPrivate")
    protected val defaultSystemEntities = HashMap<String, SystemEntity?>()

    /**
     * Get System Entity By Name.
     *
     * @param name System Entity Name
     * @return Latest System Entity matching that name
     * @throws OnyxException Default Exception
     */
    @Throws(OnyxException::class)
    override fun getSystemEntityByName(name: String): SystemEntity? = synchronized(defaultSystemEntities) {
        defaultSystemEntities.getOrPut(name) {

            val result = serializedPersistenceManager
                    .from(SystemEntity::class)
                    .where("name" eq name).and("isLatestVersion" eq true)
                    .orderBy("primaryKey".desc())
                    .limit(1)
                    .list<SystemEntity>()
                    .firstOrNull()

            if (result != null) {
                synchronized(systemEntityByIDMap) {
                    systemEntityByIDMap.put(result.primaryKey, result)
                }
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
    override fun getSystemEntityById(systemEntityId: Int): SystemEntity? = synchronized(systemEntityByIDMap) {
        systemEntityByIDMap.getOrPut(systemEntityId) {
            val entity = serializedPersistenceManager.findById<SystemEntity>(SystemEntity::class.java, systemEntityId)

            if (entity != null) {
                synchronized(defaultSystemEntities) {
                    defaultSystemEntities.put(entity.name, entity)
                }

                entity.attributes.sortBy { it.name }
                entity.relationships.sortBy { it.name }
                entity.indexes.sortBy { it.name }
            }
            return@getOrPut entity
        }
    }

    // endregion

    // region Entity Descriptor

    private val descriptors = HashMap<String, EntityDescriptor>()

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
    @Suppress("MemberVisibilityCanPrivate")
    @Throws(OnyxException::class)
    fun getBaseDescriptorForEntity(entityClass: String): EntityDescriptor = getDescriptorForEntity(classForName(entityClass, this))

    private val descriptorLockCount = AtomicInteger(0)

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

        var descriptor:EntityDescriptor?

        if(descriptorLockCount.get() == 0) {
            descriptor = descriptors[entityKey]
            if(descriptorLockCount.get() == 0 && descriptor != null)
                return descriptor
        }

        return synchronized(descriptors) {
            descriptorLockCount.incrementAndGet()
            try {
                descriptor = descriptors[entityKey]
                if (descriptor != null)
                    return@synchronized descriptor!!

                descriptor = EntityDescriptor(entityClass)
                descriptor!!.context = this
                descriptor!!.partition?.partitionValue = partitionIdVar.toString()
                descriptors[entityKey] = descriptor!!

                // Get the latest System Entity
                var systemEntity: SystemEntity? = getSystemEntityByName(descriptor!!.entityClass.name)

                // If it does not exist, lets create a new one
                if (systemEntity == null) {
                    systemEntity = SystemEntity(descriptor)
                    serializedPersistenceManager.saveEntity<IManagedEntity>(systemEntity)
                }

                systemEntity = checkForEntityChanges(descriptor!!, systemEntity)
                checkForValidDescriptorPartition(descriptor!!, systemEntity)

                // Make sure entity attributes have loaded descriptors
                descriptor!!.attributes.values.filter { IManagedEntity::class.java.isAssignableFrom(it.type) }
                        .forEach { getDescriptorForEntity(it.field.type.createNewEntity<IManagedEntity>(), "") }

                // Make sure entity attributes have loaded descriptors
                descriptor!!.relationships.values.forEach { getDescriptorForEntity(it.inverseClass.createNewEntity<IManagedEntity>(), "") }

                return@synchronized descriptor!!
            }
            finally {
                descriptorLockCount.decrementAndGet()
            }
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

        val baseDescriptor = getDescriptorForEntity(entity, "")
        if(baseDescriptor.partition == null)
            return baseDescriptor

        val partitionId = entity.get<Any?>(context = this, descriptor = baseDescriptor, name = baseDescriptor.partition!!.name) ?: nullPartition
        return getDescriptorForEntity(entity, partitionId)
    }

    // endregion

    // region Relationship Controllers

    private val relationshipInteractors = OptimisticLockingMap<RelationshipDescriptor, RelationshipInteractor>(HashMap())

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
    override fun getRelationshipInteractor(relationshipDescriptor: RelationshipDescriptor): RelationshipInteractor =
        relationshipInteractors.getOrPut(relationshipDescriptor) {
            return@getOrPut if (relationshipDescriptor.relationshipType == RelationshipType.MANY_TO_MANY || relationshipDescriptor.relationshipType == RelationshipType.ONE_TO_MANY) {
                ToManyRelationshipInteractor(relationshipDescriptor.entityDescriptor, relationshipDescriptor, this)
            } else {
                ToOneRelationshipInteractor(relationshipDescriptor.entityDescriptor, relationshipDescriptor, this)
            }
        }


    // endregion

    // region Index Controller

    private val indexInteractors = OptimisticLockingMap<IndexDescriptor, IndexInteractor>(HashMap())

    /**
     * Get Index Controller with Index descriptor.
     * This is not meant to be a public API.
     *
     * @param indexDescriptor Index Descriptor
     * @return Corresponding record controller
     * @since 1.0.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun getIndexInteractor(indexDescriptor: IndexDescriptor): IndexInteractor =
        indexInteractors.getOrPut(indexDescriptor) {
            return@getOrPut DefaultIndexInteractor(indexDescriptor.entityDescriptor, indexDescriptor, this)
        }

    // endregion

    // region Record Controller

    private val recordInteractors = OptimisticLockingMap<EntityDescriptor, RecordInteractor>(HashMap())

    /**
     * Get Record Controller.
     *
     * @param descriptor Record's Entity Descriptor
     * @return get Record Controller.
     * @since 1.0.0
     */
    override fun getRecordInteractor(descriptor: EntityDescriptor): RecordInteractor =
        recordInteractors.getOrPut(descriptor) {
            if (descriptor.identifier!!.generator == IdentifierGenerator.SEQUENCE) SequenceRecordInteractor(descriptor, this@DefaultSchemaContext) else DefaultRecordInteractor(descriptor, this@DefaultSchemaContext)
        }

    // endregion

    // region Data Files

    @JvmField internal val dataFiles = OptimisticLockingMap<String, DiskMapFactory>(HashMap())

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
    override fun getDataFile(descriptor: EntityDescriptor): DiskMapFactory {
        val key = descriptor.fileName + if (descriptor.partition == null) "" else descriptor.partition!!.partitionValue
        val absoluteLocation = if(descriptor.absolutePath.isNullOrBlank()) location else descriptor.absolutePath
        return dataFiles.getOrPut(key) {
                return@getOrPut DefaultDiskMapFactory("$absoluteLocation/$key", storeType, this@DefaultSchemaContext)
            }
        }

    @JvmField internal val partitionDataFiles = OptimisticLockingMap<Long, DiskMapFactory>(HashMap())

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
    override fun getPartitionDataFile(descriptor: EntityDescriptor, partitionId: Long): DiskMapFactory {

        if (partitionId == 0L) {
            return getDataFile(descriptor)
        }

        return partitionDataFiles.getOrPut(partitionId) {
            val query = Query(SystemPartitionEntry::class.java, QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId))
            val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
            val partition = partitions[0]

            return@getOrPut getDataFile(getDescriptorForEntity(descriptor.entityClass, partition.value))
        }
    }

    // endregion

    // region Partitions

    data class PartitionInfo(val classToGet: Class<*>, val partitionValue:Any)

    private val partitionsByValue = OptimisticLockingMap<PartitionInfo, SystemPartitionEntry?>(HashMap())

    /**
     * Get Partition Entry for entity.
     *
     * @param classToGet     Entity type for record
     * @param partitionValue Partition Value
     * @return System Partition Entry for class with partition key
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    override fun getPartitionWithValue(classToGet: Class<*>, partitionValue: Any): SystemPartitionEntry? = partitionsByValue.getOrPut(PartitionInfo(classToGet, partitionValue)) {
        val query = Query(SystemPartitionEntry::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, classToGet.name + partitionValue.toString()))
        val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
        partitions.firstOrNull()
    }

    private val partitionsById = OptimisticLockingMap<Long, SystemPartitionEntry?>(HashMap())

    /**
     * Get System Partition with Id.
     *
     * @param partitionId Partition ID
     * @return System Partition Entry for class with partition id
     * @throws OnyxException Generic Exception
     * @since 1.0.0
     */
    override fun getPartitionWithId(partitionId: Long): SystemPartitionEntry? = partitionsById.getOrPut(partitionId) {
        val query = Query(SystemPartitionEntry::class.java, QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId))
        val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
        partitions.firstOrNull()
    }

    // endregion
}