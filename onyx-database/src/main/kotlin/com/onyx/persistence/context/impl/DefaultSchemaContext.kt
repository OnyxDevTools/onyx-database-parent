@file:Suppress("MemberVisibilityCanBePrivate")

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
import com.onyx.extension.common.*
import com.onyx.extension.nullPartition
import com.onyx.extension.createNewEntity
import com.onyx.extension.get
import com.onyx.interactors.cache.QueryCacheInteractor
import com.onyx.interactors.cache.impl.DefaultQueryCacheInteractor
import com.onyx.interactors.encryption.EncryptionInteractor
import com.onyx.interactors.encryption.data.Base64
import com.onyx.interactors.index.IndexInteractor
import com.onyx.interactors.index.impl.DefaultIndexInteractor
import com.onyx.interactors.record.RecordInteractor
import com.onyx.interactors.record.impl.DefaultRecordInteractor
import com.onyx.interactors.record.impl.SequenceRecordInteractor
import com.onyx.interactors.record.impl.UUIDRecordInteractor
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
import java.util.*
import java.util.concurrent.TimeUnit
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

    override var storeType: StoreType = StoreType.FILE
    override var encryption: EncryptionInteractor? = null
    override var encryptDatabase: Boolean = false
    override var maxCardinality: Int = 1000000

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

    protected open var transactionStore: TransactionStore? = null

    // Class loader to dynamically add classes
    override var classLoader:ClassLoader = DefaultSchemaContext::class.java.classLoader

    protected open var memoryAlertJob: Job? = null

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
        @Suppress("LeakingThis")
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
        memoryAlertJob = watchMemoryUsage()
    }

    /**
     * Shutdown schema context. Close files, connections or any other IO mechanisms used within the context
     *
     * @since 1.0.0
     */
    override fun shutdown() {
        killSwitch = true

        memoryAlertJob?.cancel()

        // Shutdown all index interactors
        indexInteractors.forEach { (_, interactor) ->
            catchAll { interactor.shutdown() }
        }

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

        Contexts.remove(this)
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

        results.map { it["name"] }.forEach {
            var clazz: Class<*>? = null
            catchAll {
                clazz = metadata(this.contextId).classForName(it!!, this)
            }
            clazz ?: return@forEach
            getBaseDescriptorForEntity(clazz)
        }
    }

    /**
     * This method initializes the metadata needed to get started.  It creates the base level information about the system metadata so that we no longer have to lazy load them
     */
    @Suppress("MemberVisibilityCanPrivate")
    protected open fun initializeSystemEntities() {
        val classes:List<KClass<out ManagedEntity>> = arrayListOf(SystemEntity::class,SystemAttribute::class,SystemRelationship::class,SystemIndex::class,SystemIdentifier::class,SystemPartitionEntry::class)
        val systemEntities = ArrayList<SystemEntity>()
        var i = 1

        classes.forEach { entityKClass ->
            val descriptor = EntityDescriptor(entityKClass.java)
            descriptor.context = this
            val systemEntity = SystemEntity(descriptor)
            systemEntity.primaryKey = i

            this.descriptors[entityKClass.java.name] = descriptor
            this.defaultSystemEntities[entityKClass.java.name] = systemEntity
            this.systemEntityByIDMap[i] = systemEntity
            systemEntities.add(systemEntity)

            systemEntity.attributes.sortBy { it.name }
            systemEntity.relationships.sortBy { it.name }
            systemEntity.indexes.sortBy { it.name }

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
        systemEntity.attributes.sortBy { it.name }
        systemEntity.relationships.sortBy { it.name }
        systemEntity.indexes.sortBy { it.name }

        val newSystemEntity = SystemEntity(descriptor)
        newSystemEntity.attributes.forEach { attribute ->
            val existingAttribute = systemEntity.attributes.firstOrNull { it.name == attribute.name }
            attribute.size = existingAttribute?.size ?: attribute.size
            attribute.isNullable = existingAttribute?.isNullable ?: attribute.isNullable
            attribute.isPartition = newSystemEntity.partitionName == attribute.name
        }

        val entitiesMatch = newSystemEntity == systemEntity
        if (!entitiesMatch) {

            checkForInvalidRelationshipChanges(systemEntity, newSystemEntity)

            serializedPersistenceManager.from(SystemEntity::class).where("name" eq systemEntity.name).set("isLatestVersion" to false).update()
            serializedPersistenceManager.saveEntity<IManagedEntity>(newSystemEntity)
            systemEntity = newSystemEntity
        }

        defaultSystemEntities[systemEntity.name] = systemEntity
        systemEntityByIDMap[systemEntity.primaryKey] = systemEntity

        if (!entitiesMatch) {
            checkForIndexChanges(systemEntity, newSystemEntity)
        }

        return systemEntity
    }

    /**
     * Checks to see if a partition already exists for the corresponding entity descriptor.  If it does not, lets create it.
     *
     * @param descriptor   Entity descriptor to base the new partition on or to cross reference the old one
     * @since 1.1.0
     */
    @Throws(OnyxException::class)
    protected open fun checkForValidDescriptorPartition(descriptor: EntityDescriptor) {
        // Check to see if the partition already exists
        if (descriptor.partition != null && getPartitionWithValue(descriptor.entityClass, descriptor.partition?.partitionValue ?: "") != null) {
            return
        }

        // Add a new partition entry if it does not exist
        if (descriptor.partition != null) {
            val entry = SystemPartitionEntry(descriptor, descriptor.partition!!, partitionCounter.incrementAndGet())
            serializedPersistenceManager.saveEntity<IManagedEntity>(entry)
            partitionsByClass.remove(descriptor.entityClass)
        }
    }

    /**
     * Check For Index Changes.
     *
     * @param systemEntity System Entity from previous
     * @param newRevision Entity Descriptor with new potential index changes
     */
    protected open fun checkForIndexChanges(systemEntity: SystemEntity, newRevision: SystemEntity) {
        val oldIndexes = systemEntity.indexes.associateBy { it.name }
        val newIndexes = newRevision.indexes.associateBy { it.name }

        // Rebuild indexes that are new or have changed.  Indexes that were removed
        // should not trigger a rebuild, otherwise the rebuild process may attempt to
        // access descriptors that no longer exist which can lead to file corruption.
        (newIndexes - oldIndexes).values.forEach { rebuildIndex(systemEntity, it.name) }
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
    protected open fun checkForInvalidRelationshipChanges(systemEntity: SystemEntity, newRevision: SystemEntity) {

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
    protected open fun rebuildIndex(systemEntity: SystemEntity, indexName: String) = catchAll {
        val entityDescriptor = getBaseDescriptorForEntity(systemEntity.name)
        val indexDescriptor = entityDescriptor.indexes[indexName]
        if (entityDescriptor.partition != null) {

            val entries = getAllPartitions(entityDescriptor.entityClass)

            entries.forEach {
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

            @Suppress("DuplicatedCode")
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

            entity?.attributes?.sortBy { it.name }
            entity?.relationships?.sortBy { it.name }
            entity?.indexes?.sortBy { it.name }
            return@getOrPut entity
        }
    }

    // endregion

    // region Entity Descriptor
    private val maxCapacity = 5000

    protected open val descriptors = object : LinkedHashMap<String, EntityDescriptor>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EntityDescriptor>?): Boolean {
            return size > maxCapacity
        }
    }

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
    fun getBaseDescriptorForEntity(entityClass: String): EntityDescriptor = getDescriptorForEntity(metadata(this.contextId).classForName(entityClass, this).createNewEntity<IManagedEntity>(contextId))

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
        // Using java string concat because it is more efficient for small variables
        // over StringBuilder which is Kotlin's implementation.  It is deprecated in Kotlin
        // therefore it was added to Java class to work around.
        val entityKey = Base64.concat(entityClass.name, partitionIdVar.toString())

        return synchronized(descriptors) {
            descriptors.getOrPut(entityKey) {
                val descriptor = EntityDescriptor(entityClass)
                descriptor.context = this
                descriptor.partition?.partitionValue = partitionIdVar.toString()
                descriptors[entityKey] = descriptor

                // Get the latest System Entity
                var systemEntity: SystemEntity? = getSystemEntityByName(descriptor.entityClass.name)

                // If it does not exist, lets create a new one
                if (systemEntity == null) {
                    systemEntity = SystemEntity(descriptor)
                    serializedPersistenceManager.saveEntity<IManagedEntity>(systemEntity)
                }

                checkForEntityChanges(descriptor, systemEntity)
                checkForValidDescriptorPartition(descriptor)

                // Make sure entity attributes have loaded descriptors
                descriptor.attributes.values.filter { IManagedEntity::class.java.isAssignableFrom(it.type) }
                    .forEach { getDescriptorForEntity(it.field.type.createNewEntity<IManagedEntity>(this.contextId), "") }

                // Make sure entity attributes have loaded descriptors
                descriptor.relationships.values.forEach { getDescriptorForEntity(it.inverseClass.createNewEntity<IManagedEntity>(contextId), "") }
                return@getOrPut descriptor
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

    protected open val relationshipInteractors = OptimisticLockingMap<RelationshipDescriptor, RelationshipInteractor>(WeakHashMap())

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

    protected open val indexInteractors = OptimisticLockingMap<IndexDescriptor, IndexInteractor>(WeakHashMap())

    /**
     * Get Index Controller with Index descriptor.
     * This is not meant to be a public API.
     *
     * @param indexDescriptor Index Descriptor
     * @return Corresponding record controller
     * @since 1.0.0
     */
    override fun getIndexInteractor(indexDescriptor: IndexDescriptor): IndexInteractor =
        indexInteractors.getOrPut(indexDescriptor) {
            return@getOrPut when (indexDescriptor.indexType) {
                com.onyx.persistence.annotations.values.IndexType.VECTOR -> createVectorInteractor(indexDescriptor)
                com.onyx.persistence.annotations.values.IndexType.LUCENE -> createLuceneInteractor(indexDescriptor)
                else -> DefaultIndexInteractor(indexDescriptor.entityDescriptor, indexDescriptor, this)
            }
        }

    private fun createLuceneInteractor(indexDescriptor: IndexDescriptor): IndexInteractor {
        val className = "com.onyx.lucene.interactors.index.impl.LuceneIndexInteractor"
        return try {
            val clazz = Class.forName(className)
            val constructor = clazz.getConstructor(EntityDescriptor::class.java, IndexDescriptor::class.java, SchemaContext::class.java)
            constructor.newInstance(indexDescriptor.entityDescriptor, indexDescriptor, this) as IndexInteractor
        } catch (classNotFound: ClassNotFoundException) {
            throw IllegalStateException(
                "Lucene index support is not available. Add the onyx-lucene-index module to the classpath to enable IndexType.LUCENE.",
                classNotFound
            )
        }
    }

    private fun createVectorInteractor(indexDescriptor: IndexDescriptor): IndexInteractor {
        val className = "com.onyx.lucene.interactors.index.impl.VectorIndexInteractor"
        return try {
            val clazz = Class.forName(className)
            val constructor = clazz.getConstructor(EntityDescriptor::class.java, IndexDescriptor::class.java, SchemaContext::class.java)
            constructor.newInstance(indexDescriptor.entityDescriptor, indexDescriptor, this) as IndexInteractor
        } catch (classNotFound: ClassNotFoundException) {
            throw IllegalStateException(
                "Vector index support is not available. Add the onyx-lucene-index module to the classpath to enable IndexType.VECTOR.",
                classNotFound
            )
        }
    }


    // endregion

    // region Record Controller

    protected open val recordInteractors = OptimisticLockingMap<EntityDescriptor, RecordInteractor>(WeakHashMap())

    /**
     * Get Record Controller.
     *
     * @param descriptor Record's Entity Descriptor
     * @return get Record Controller.
     * @since 1.0.0
     * @since 9/26/2024 I've added a UUID generator
     */
    override fun getRecordInteractor(descriptor: EntityDescriptor): RecordInteractor =
        recordInteractors.getOrPut(descriptor) {
            when (descriptor.identifier!!.generator) {
                IdentifierGenerator.SEQUENCE -> SequenceRecordInteractor(descriptor, this)
                IdentifierGenerator.UUID -> UUIDRecordInteractor(descriptor, this)
                else -> DefaultRecordInteractor(descriptor, this)
            }
        }

    // endregion

    // region Data Files

    @JvmField internal val dataFiles: MutableMap<String, DiskMapFactory> = hashMapOf()

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor.
     *
     * This is not meant to be a public API.
     *
     * @param descriptor Record Entity Descriptor
     * @return Underlying data storage factory
     * @since 1.0.0
     */
    @Synchronized
    override fun getDataFile(descriptor: EntityDescriptor): DiskMapFactory {
        val key = if (descriptor.partition == null) descriptor.fileName else descriptor.fileName + descriptor.partition!!.partitionValue
        var finalLocation = descriptor.primaryLocation
        finalLocation = if(finalLocation != null) "$finalLocation/$key" else "$location/$key"

        return dataFiles.getOrPut(key) {
            return@getOrPut DefaultDiskMapFactory(finalLocation, storeType, this@DefaultSchemaContext)
        }
    }

    protected open val partitionCache: MutableMap<Long, SystemPartitionEntry> = Collections.synchronizedMap(WeakHashMap())

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

        val partition = partitionCache.getOrPut(partitionId) {
            val query = Query(SystemPartitionEntry::class.java, QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId))
            val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
            partitions[0]
        }

        return getDataFile(getDescriptorForEntity(descriptor.entityClass, partition.value))
    }

    // endregion

    // region Partitions

    data class PartitionInfo(val classToGet: Class<*>, val partitionValue:Any)

    protected open val partitionsByValue: MutableMap<PartitionInfo, SystemPartitionEntry?> = Collections.synchronizedMap(WeakHashMap<PartitionInfo, SystemPartitionEntry?>())
    protected open val partitionsByClass = WeakHashMap<Class<*>, List<SystemPartitionEntry>>()

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
        return@getOrPut serializedPersistenceManager.from<SystemPartitionEntry>().where("id" eq classToGet.name + partitionValue.toString()).firstOrNull()
    }

    override fun getAllPartitions(classToGet: Class<*>): List<SystemPartitionEntry> = synchronized(descriptors) {
        partitionsByClass.getOrPut(classToGet) {
            return@getOrPut serializedPersistenceManager.from<SystemPartitionEntry>().where("entityClass" eq classToGet.name).list<SystemPartitionEntry>()
        }
    }

    protected open val partitionsById = OptimisticLockingMap<Long, SystemPartitionEntry?>(WeakHashMap())

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

    /**
     * In order to purge memory after long-running intensive tasks, this method has been added.  It will
     * clear non-volatile cached items in the disk maps
     */
    override fun flush() {
        dataFiles.values.forEach { it.flush() }
        this.indexInteractors.clear()
        this.recordInteractors.clear()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun watchMemoryUsage(): Job = runJob(5, TimeUnit.MINUTES) {
        if(Runtime.getRuntime().freeMemory().toDouble() / Runtime.getRuntime().totalMemory().toDouble() <= .50)
            this.flush()
    }
}
