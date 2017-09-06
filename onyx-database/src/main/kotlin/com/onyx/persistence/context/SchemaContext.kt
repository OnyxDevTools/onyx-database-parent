package com.onyx.persistence.context

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemPartitionEntry
import com.onyx.exception.EntityException
import com.onyx.index.IndexController
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.QueryCacheController
import com.onyx.record.RecordController
import com.onyx.relationship.RelationshipController
import com.onyx.diskmap.MapBuilder
import com.onyx.exception.TransactionException
import com.onyx.persistence.IManagedEntity
import com.onyx.transaction.TransactionController

import java.nio.channels.FileChannel

/**
 * The purpose of this interface is to resolve all the the metadata, storage mechanism,  and modeling regarding the structure of the database
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
interface SchemaContext {

    /**
     * Get location of database.  This can indicate a local or remote endpoint of database.
     * @return Database location.  Either an endpoint or local path
     * @since 1.0.0
     */
    val location: String

    /**
     * Get Context ID
     *
     * @return context id that maps back to the Persistence Manager Factory instance name
     */
    val contextId: String

    /**
     * Get Controller that handles transactions.  This creates a log of persistence within the database.
     */
    val transactionController: TransactionController

    /**
     * Get controller responsible for managing query caches
     *
     * @since 1.3.0
     */
    val queryCacheController: QueryCacheController

    /**
     * @since 1.0.0
     *
     * Setter for default persistence manager
     *
     * This is not meant to be a public API.  This is called within the persistence manager factory.  It is used to access system data.
     */
    var systemPersistenceManager: PersistenceManager?

    /**
     * This is not meant to be a public API.
     * This is used to indicate what persistence manager should be serialized.
     * In some cases the embedded or the remote could be injected but we need
     * to use a contract so we can tell which persistence manager pertains the
     * how we are using it.
     *
     * Returns what persistence manager should be de-serialized when attaching this context to an object through the network.  Or lack of network
     *
     * @return Serialized Persistence Manager.
     */
    val serializedPersistenceManager: PersistenceManager


    /**
     * Get Kill switch
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @return Volatile indicator the database is shutting down
     */
    val killSwitch: Boolean

    /**
     * Get Descriptor For Entity.  Initializes EntityDescriptor or returns one
     * if it already exists
     *
     * @param entityClass Entity Type
     * @return Entity Types default Entity Descriptor
     *
     * @throws EntityException Generic Exception
     */
    @Throws(EntityException::class)
    fun getBaseDescriptorForEntity(entityClass: Class<*>): EntityDescriptor?

    /**
     * Get Transaction File that is used to read and write to WAL journaled file
     */
    @Throws(TransactionException::class)
    fun getTransactionFile():FileChannel

    /**
     * Get Descriptor For Entity.  Initializes EntityDescriptor or returns one
     * if it already exists
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param entityClass Entity Type
     * @param partitionId Partition key
     * @return Records Entity Descriptor for a partition
     *
     * @throws EntityException Generic Exception
     */
    @Throws(EntityException::class)
    fun getDescriptorForEntity(entityClass: Class<*>?, partitionId: Any?): EntityDescriptor

    /**
     * Get Descriptor For Entity.  Initializes EntityDescriptor or returns one
     * if it already exists
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     *
     * @param entity Entity Instance
     *
     * @param partitionId Partition Field Value
     *
     * @return Record's entity descriptor
     *
     * @throws EntityException Generic Exception
     */
    @Throws(EntityException::class)
    fun getDescriptorForEntity(entity: IManagedEntity, partitionId: Any?): EntityDescriptor

    /**
     * Get Descriptor and have it automatically determine the partition ID
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     *
     * @param entity Entity Instance
     *
     * @return Record's entity descriptor
     *
     * @throws EntityException Generic Exception
     */
    @Throws(EntityException::class)
    fun getDescriptorForEntity(entity: Any): EntityDescriptor

    /**
     * Shutdown schema context.  Close files, connections or any other IO mechanisms used within the context
     *
     * @since 1.0.0
     */
    fun shutdown()

    /**
     * Start the context and initialize storage, connection, or any other IO mechanisms used within the schema context
     */
    fun start()

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor
     *
     * This is not meant to be a public API.
     *
     * @param descriptor Record Entity Descriptor
     *
     * @since 1.0.0
     * @return Underlying data storage factory
     */
    fun getDataFile(descriptor: EntityDescriptor): MapBuilder

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor that pertains to a partitionID
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param descriptor Record Entity Descriptor
     * @param partitionId Partition the records belong to
     *
     * @return Underlying data storage factory
     *
     * @throws EntityException Generic Exception
     */
    @Throws(EntityException::class)
    fun getPartitionDataFile(descriptor: EntityDescriptor, partitionId: Long): MapBuilder

    /**
     * Get Partition Entry for entity
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param classToGet Type of record
     * @param partitionValue Partition Value
     * @return System Partition Entry by class and partition key
     *
     * @throws EntityException Generic Exception
     */
    @Throws(EntityException::class)
    fun getPartitionWithValue(classToGet: Class<*>, partitionValue: Any): SystemPartitionEntry?

    /**
     * Get System Partition with Id
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param partitionId Partition ID
     * @return System Partition Entry for class with partition id
     *
     * @throws EntityException Generic Exception
     */
    @Throws(EntityException::class)
    fun getPartitionWithId(partitionId: Long): SystemPartitionEntry?

    /**
     * Get Record Controller
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param descriptor Record's Entity Descriptor
     * @return Corresponding record controller for entity descriptor
     */
    fun getRecordController(descriptor: EntityDescriptor): RecordController

    /**
     * Get Index Controller with Index descriptor
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param indexDescriptor Index Descriptor
     *
     * @return Corresponding record controller
     */
    fun getIndexController(indexDescriptor: IndexDescriptor): IndexController

    /**
     * Get Relationship Controller that corresponds to the relationship descriptor
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param relationshipDescriptor Entity relationship descriptor
     * @throws EntityException Generic Exception
     * @return Relationship Controller for relationship descriptor
     */
    @Throws(EntityException::class)
    fun getRelationshipController(relationshipDescriptor: RelationshipDescriptor): RelationshipController

    /**
     * Create Temporary Map Builder
     * @since 1.0.0
     *
     * @return Create new storage mechanism factory
     */
    fun createTemporaryMapBuilder(): MapBuilder

    /**
     * Get System Entity By Name
     * @since 1.0.0
     * @param name System entity name
     * @throws EntityException Default Exception
     * @return Latest System Entity version with matching name
     */
    @Throws(EntityException::class)
    fun getSystemEntityByName(name: String): SystemEntity?

    /**
     * Get System Entity By ID
     *
     * @param systemEntityId Unique identifier for system entity version
     * @return System Entity matching ID
     */
    fun getSystemEntityById(systemEntityId: Int): SystemEntity?

    /**
     * Release a map builder and prepare it for re-use
     * This was added to prevent direct buffers from being destroyed.
     * That caused performance issues
     *
     * @param mapBuilder Map builder to recycle
     *
     * @since 1.3.0
     */
    fun releaseMapBuilder(mapBuilder: MapBuilder)

}
