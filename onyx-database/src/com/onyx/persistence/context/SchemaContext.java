package com.onyx.persistence.context;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.*;
import com.onyx.index.IndexController;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.record.RecordController;
import com.onyx.relationship.RelationshipController;
import com.onyx.transaction.TransactionController;

import java.nio.channels.FileChannel;

/**
 * The purpose of this interface is to resolve all the the metadata, storage mechanism,  and modeling regarding the structure of the database
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory fac = new EmbeddedPersistenceManagerFactory();
 *   SchemaContext context = new DefaultSchemaContext(); // Instantiate a default schema context (Embedded)
 *
 *   factory.setDatabaseLocation("/MyDatabaseLocation");
 *   factory.setSchemaContext(context); // Define schema context
 *   factory.setCredentials("username", "password");
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the database
 *
 * </code>
 * </pre>
 *
 */
public interface SchemaContext
{

    /**
     * Get Descriptor For Entity.  Initializes EntityDescriptor or returns one
     * if it already exists
     *
     * @param entity Entity Type
     * @return Entity Types default Entity Descriptor
     *
     * @throws EntityException Generic Exception
     */
    EntityDescriptor getBaseDescriptorForEntity(Class entity) throws EntityException;

    /**
     * @since 1.0.0

     * Setter for default persistence manager
     *
     * This is not meant to be a public API.  This is called within the persistence manager factory.  It is used to access system data.
     *
     * @param defaultPersistenceManager Default Persistence Manager used to access system level entities
     */
    void setSystemPersistenceManager(PersistenceManager defaultPersistenceManager);

    /**
     * This is not meant to be a public API.
     * @return System Persistence Manager
     *
     */
    PersistenceManager getSystemPersistenceManager();

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
     *
     */
    PersistenceManager getSerializedPersistenceManager();

    /**
     * Get Descriptor For Entity.  Initializes EntityDescriptor or returns one
     * if it already exists
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param entity Entity Type
     * @param partitionId Partition value
     * @return Records Entity Descriptor for a partition
     *
     * @throws EntityException Generic Exception
     */
    EntityDescriptor getDescriptorForEntity(Class entity, Object partitionId) throws EntityException;

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
    EntityDescriptor getDescriptorForEntity(Object entity, Object partitionId) throws EntityException;

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
    EntityDescriptor getDescriptorForEntity(Object entity) throws EntityException;

    /**
     * Sets the database location.  This can either be a local location or a remote endpoint.
     *
     * @since 1.0.0
     *
     * @param location Database endpoint or local store location
     */
    void setLocation(String location);

    /**
     * Get Kill switch
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @return Volatile indicator the database is shutting down
     */
     boolean getKillSwitch();

    /**
     * Shutdown schema context.  Close files, connections or any other IO mechanisms used within the context
     *
     * @since 1.0.0
     * @throws SingletonException Only one instance of the record and index factories must exist
     */
    void shutdown() throws SingletonException;

    /**
     * Start the context and initialize storage, connection, or any other IO mechanisms used within the schema context
     */
    void start();

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
    MapBuilder getDataFile(EntityDescriptor descriptor);

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
    MapBuilder getPartitionDataFile(EntityDescriptor descriptor, long partitionId) throws EntityException;

    /**
     * Get Partition Entry for entity
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param classToGet Type of record
     * @param partitionValue Partition Value
     * @return System Partition Entry by class and partition value
     *
     * @throws EntityException Generic Exception
     */
    SystemPartitionEntry getPartitionWithValue(Class classToGet, Object partitionValue) throws EntityException;

    /**
     * Get System Partition with Id
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param classToGet Record Entity type
     * @param partitionId Partition ID
     * @return System Partition Entry for class with partition id
     *
     * @throws EntityException Generic Exception
     */
    SystemPartitionEntry getPartitionWithId(Class classToGet, long partitionId) throws EntityException;

    /**
     * Get Record Controller
     *
     * This is not meant to be a public API.
     *
     * @since 1.0.0
     * @param descriptor Record's Entity Descriptor
     * @return Corresponding record controller for entity descriptor
     */
    RecordController getRecordController(EntityDescriptor descriptor);

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
    IndexController getIndexController(IndexDescriptor indexDescriptor);

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
    RelationshipController getRelationshipController(RelationshipDescriptor relationshipDescriptor) throws EntityException;

    /**
     * Get location of database.  This can indicate a local or remote endpoint of database.
     * @return Database location.  Either an endpoint or local path
     * @since 1.0.0
     */
    String getLocation();

    /**
     * Get location of file base
     *
     * @since 1.0.0
     * @return Remote file path for file server
     */
    String getRemoteFileBase();

    /**
     * Create Temporary Map Builder
     * @since 1.0.0
     *
     * @return Create new storage mechanism factory
     */
    MapBuilder createTemporaryMapBuilder();

    /**
     * Get System Entity By Name
     * @since 1.0.0
     * @param name System entity name
     * @throws EntityException Default Exception
     * @return Latest System Entity version with matching name
     */
    SystemEntity getSystemEntityByName(String name) throws EntityException;

    /**
     * Get System Entity By ID
     *
     * @param systemEntityId Unique identifier for system entity version
     * @return System Entity matching ID
     * @throws EntityException Default Exception
     */
    SystemEntity getSystemEntityById(int systemEntityId);

    /**
     * Get Context ID
     *
     * @return context id that maps back to the Persistence Manager Factory instance name
     */
    String getContextId();

    /**
     * Get Transaction File that is used to read and write to WAL journaled file
     *
     * @return FileChannel Open File Channel
     */
    FileChannel getTransactionFile() throws TransactionException;

    /**
     * Get Controller that handles transactions.  This creates a log of persistence within the database.
     *
     * @return Transaction Controller implementation.
     */
    TransactionController getTransactionController();

    /**
     * Add a query lock in order to prevent storage de-allocation.  Storage that disappears makes
     * concurrent queries hard.  So, we prevent cleanup of those queries.
     *
     * @since 1.0.2
     */
    default void addQueryLock()
    {

    }

    /**
     * Release query lock.  Actually this just decrements the lock count.  When the lock count hits 0, the
     * observable count down fires and invokes a de-allocation sweep.
     *
     * @since 1.0.2
     */
    default void releaseQueryLock()
    {

    }
}
