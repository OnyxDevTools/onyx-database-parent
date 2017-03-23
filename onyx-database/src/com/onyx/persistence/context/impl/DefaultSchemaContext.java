package com.onyx.persistence.context.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.diskmap.DefaultMapBuilder;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.store.StoreType;
import com.onyx.entity.*;
import com.onyx.exception.EntityClassNotFoundException;
import com.onyx.exception.EntityException;
import com.onyx.exception.TransactionException;
import com.onyx.fetch.ScannerFactory;
import com.onyx.helpers.PartitionHelper;
import com.onyx.index.IndexController;
import com.onyx.index.impl.IndexControllerImpl;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.IdentifierGenerator;
import com.onyx.persistence.annotations.RelationshipType;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.query.CachedResults;
import com.onyx.record.RecordController;
import com.onyx.record.impl.RecordControllerImpl;
import com.onyx.record.impl.SequenceRecordControllerImpl;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.impl.ToManyRelationshipControllerImpl;
import com.onyx.relationship.impl.ToOneRelationshipControllerImpl;
import com.onyx.transaction.TransactionController;
import com.onyx.transaction.impl.TransactionControllerImpl;
import com.onyx.util.EntityClassLoader;
import com.onyx.util.FileUtil;
import com.onyx.util.map.CompatHashMap;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.SynchronizedMap;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * Schema context that defines local stores for data storage and partitioning. This can only be accessed by a single process. Databases must
 * not have multiple process accessed at the same time.
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.context.SchemaContext
 * @since 1.0.0
 * <p>
 * <pre>
 * <code>
 *
 *
 * PersistenceManagerFactory fac = new EmbeddedPersistenceManagerFactory();
 * fac.setDatabaseLocation("/MyDatabaseLocation");
 * fac.setSchemaContext(new DefaultSchemaContext()); //Define Default Schema Context
 * fac.setCredentials("username", "password");
 * fac.initialize();
 *
 * PersistenceManager manager = fac.getPersistenceManager();
 *
 * fac.close();
 *
 * </code>
 * </pre>
 */
@SuppressWarnings("unchecked")
public class DefaultSchemaContext implements SchemaContext {

    // Random generator for generating random temporary file names
    private static final SecureRandom random = new SecureRandom();

    // Reference to self
    @SuppressWarnings("WeakerAccess")
    protected final SchemaContext context;

    // Context id that maps back to the database instance name
    @SuppressWarnings("WeakerAccess")
    protected final String contextId;

    // The purpose of this is to gather the registed instances of SchemaContexts so that we may structure a context to a database instance in
    // event of multiple instances
    public static final CompatMap<String, SchemaContext> registeredSchemaContexts = new SynchronizedMap<>();

    // Directory location for temporary files
    @SuppressWarnings("WeakerAccess")
    protected String temporaryFileLocation;

    private final Set<MapBuilder> temporaryMaps = new HashSet<>();
    /**
     * Constructor.
     *
     * @param contextId Database identifier that must be unique and tied to its process
     */
    public DefaultSchemaContext(final String contextId) {
        Runnable commitThread = () -> dataFiles.forEach((s, db) -> db.commit());
        scheduler.scheduleWithFixedDelay(commitThread, 10, 10, TimeUnit.SECONDS);
        context = this;
        this.contextId = contextId;

        DefaultSchemaContext.registeredSchemaContexts.put(contextId, this);

    }

    /**
     * Database location.
     *
     * @since 1.0.0
     */
    protected String location;

    /**
     * Set Database location.
     *
     * @param location Database local store location
     * @since 1.0.0
     */
    @Override
    public void setLocation(final String location) {
        this.location = location;
    }

    /**
     * Database local store location.
     *
     * @return Database local store location.
     * @since 1.0.0
     */
    public String getLocation() {
        return location;
    }

    /////////////////////////////////////////////////////////////////////
    //
    // Startup and Shutdown
    //
    /////////////////////////////////////////////////////////////////////
    @SuppressWarnings("WeakerAccess")
    protected volatile boolean killSwitch = false;

    /**
     * Get Database kill switch.
     *
     * @return volatile indicator database is in the process of shutting down
     * @since 1.0.0
     */
    @Override
    public boolean getKillSwitch() {
        return killSwitch;
    }

    /**
     * Start the context and initialize storage or any other IO mechanisms used within the schema context.
     *
     * @since 1.0.0
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void start() {
        temporaryFileLocation = this.location + File.separator + "temporary";
        new File(temporaryFileLocation).mkdirs();

        createTemporaryDiskMapPool();

        killSwitch = false;
        initializeSystemEntities();
        initializePartitionSequence();
        initializeEntityDescriptors();
    }

    /**
     * This method will create a pool of map builders.  They are used to run queries
     * and then be recycled and put back on the queue.
     *
     * @since 1.3.0
     */
    @SuppressWarnings("WeakerAccess")
    protected void createTemporaryDiskMapPool() {
        for (int i = 0; i < 32; i++) {
            String stringBuilder = temporaryFileLocation +
                    File.separator +
                    String.valueOf(System.currentTimeMillis()) +
                    new BigInteger(20, random).toString(32);

            final File file = new File(stringBuilder);
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
                file.deleteOnExit(); // Must delete since there is no more functionality to delete from references
            } catch (IOException ignore) {
            }
            MapBuilder builder = new DefaultMapBuilder(file.getPath(), StoreType.MEMORY_MAPPED_FILE, this.context, true);
            temporaryDiskMapQueue.add(builder);
            temporaryMaps.add(builder);
        }
    }

    /**
     * The purpose of this is to auto number the partition ids
     */
    @SuppressWarnings("WeakerAccess")
    protected void initializePartitionSequence() {

        try {
            // Get the max partition index
            final IndexController indexController = this.getIndexController(descriptors.get(
                    SystemPartitionEntry.class.getName()).getIndexes().get("index"));
            final Set values = indexController.findAllValues();

            final Iterator it = values.iterator();
            long max = 0;

            while (it.hasNext()) {
                final long val = (long) it.next();

                if (val > max) {
                    max = val;
                }
            }

            partitions.set(max);
        } catch (EntityException e) {
            e.printStackTrace();
        }
    }

    /**
     * The purpose of this is to iterate through the system entities and pre-cache all of the entity descriptors
     * So that we can detect schema changes earlier.  For instance an index change can start re-building the index at startup.
     */
    @SuppressWarnings("WeakerAccess")
    protected void initializeEntityDescriptors() {

        try {
            // Added criteria for greater than 7 so that we do not disturb the system entities
            QueryCriteria nonSystemEntities = new QueryCriteria("name", QueryCriteriaOperator.NOT_STARTS_WITH, "com.onyx.entity.System");

            Query query = new Query(SystemEntity.class, nonSystemEntities);
            query.setSelections(Collections.singletonList("name"));
            List<Map> results = systemPersistenceManager.executeQuery(query);

            for (Map obj : results) {
                String entityName = (String) obj.get("name");
                getBaseDescriptorForEntity(Class.forName(entityName));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This method initializes the metadata needed to get started.  It creates the base level information about the system metadata so that we no longer have to lazy load them
     */
    @SuppressWarnings("WeakerAccess")
    protected void initializeSystemEntities() {
        try {

            descriptors.put(SystemEntity.class.getName(), new EntityDescriptor(SystemEntity.class));
            descriptors.put(SystemAttribute.class.getName(), new EntityDescriptor(SystemAttribute.class));
            descriptors.put(SystemRelationship.class.getName(), new EntityDescriptor(SystemRelationship.class));
            descriptors.put(SystemIndex.class.getName(), new EntityDescriptor(SystemIndex.class));
            descriptors.put(SystemIdentifier.class.getName(), new EntityDescriptor(SystemIdentifier.class));
            descriptors.put(SystemPartition.class.getName(), new EntityDescriptor(SystemPartition.class));
            descriptors.put(SystemPartitionEntry.class.getName(), new EntityDescriptor(SystemPartitionEntry.class));

            SystemEntity systemEntity = new SystemEntity(descriptors.get(SystemEntity.class.getName()));
            SystemEntity systemAttributeEntity = new SystemEntity(descriptors.get(SystemAttribute.class.getName()));
            SystemEntity systemRelationshipEntity = new SystemEntity(descriptors.get(SystemRelationship.class.getName()));
            SystemEntity systemIndexEntity = new SystemEntity(descriptors.get(SystemIndex.class.getName()));
            SystemEntity systemIdentifierEntity = new SystemEntity(descriptors.get(SystemIdentifier.class.getName()));
            SystemEntity systemPartitionEntity = new SystemEntity(descriptors.get(SystemPartition.class.getName()));
            SystemEntity systemPartitionEntryEntity = new SystemEntity(descriptors.get(SystemPartitionEntry.class.getName()));

            systemEntity.setPrimaryKey(1);
            systemAttributeEntity.setPrimaryKey(2);
            systemRelationshipEntity.setPrimaryKey(3);
            systemIndexEntity.setPrimaryKey(4);
            systemIdentifierEntity.setPrimaryKey(5);
            systemPartitionEntity.setPrimaryKey(6);
            systemPartitionEntryEntity.setPrimaryKey(7);

            defaultSystemEntities.put(SystemEntity.class.getName(), systemEntity);
            defaultSystemEntities.put(SystemAttribute.class.getName(), systemAttributeEntity);
            defaultSystemEntities.put(SystemRelationship.class.getName(), systemRelationshipEntity);
            defaultSystemEntities.put(SystemIndex.class.getName(), systemIndexEntity);
            defaultSystemEntities.put(SystemIdentifier.class.getName(), systemIdentifierEntity);
            defaultSystemEntities.put(SystemPartition.class.getName(), systemPartitionEntity);
            defaultSystemEntities.put(SystemPartitionEntry.class.getName(), systemPartitionEntryEntity);

            this.systemEntityByIDMap.put(1, systemEntity);
            this.systemEntityByIDMap.put(2, systemAttributeEntity);
            this.systemEntityByIDMap.put(3, systemRelationshipEntity);
            this.systemEntityByIDMap.put(4, systemIndexEntity);
            this.systemEntityByIDMap.put(5, systemIdentifierEntity);
            this.systemEntityByIDMap.put(6, systemPartitionEntity);
            this.systemEntityByIDMap.put(7, systemPartitionEntryEntity);

            List<SystemEntity> systemEntities = Arrays.asList(systemEntity, systemAttributeEntity, systemRelationshipEntity, systemIndexEntity, systemIdentifierEntity, systemPartitionEntity, systemPartitionEntryEntity);

            systemPersistenceManager.saveEntities(systemEntities);

        } catch (EntityException e) {
            e.printStackTrace();
        }
    }

    PersistenceManager systemPersistenceManager = null;

    /**
     * Setter for default persistence manager.
     * <p>
     * <p>This is not meant to be a public API. This is called within the persistence manager factory. It is used to access system data.</p>
     *
     * @param defaultPersistenceManager Default Persistence Manager used to access system level entities
     * @since 1.0.0
     */
    public void setSystemPersistenceManager(final PersistenceManager defaultPersistenceManager) {
        this.systemPersistenceManager = defaultPersistenceManager;
        this.transactionController = new TransactionControllerImpl(this, this.systemPersistenceManager);
    }

    /**
     * System entity persistence manager.
     *
     * @return System entity persistence manager.
     * @since 1.0.0
     */
    @Override
    public PersistenceManager getSystemPersistenceManager() {
        return this.systemPersistenceManager;
    }

    /**
     * Returns what persistence manager should be de-serialized when attaching this context to an object through the network. Or lack of
     * network
     *
     * @return The system persistence manager. AKA, the local embedded one
     */
    @Override
    public PersistenceManager getSerializedPersistenceManager() {
        return this.systemPersistenceManager;
    }

    /**
     * Get Context ID.
     *
     * @return context id that maps back to the Persistence Manager Factory instance name
     */
    public String getContextId() {
        return contextId;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Journaling Logic
    //
    //////////////////////////////////////////////////////////////////////////////////////////
    // Maximum WAL File longSize
    private static final int MAX_JOURNAL_SIZE = 1024 * 1024 * 20;

    // Journal File index in directory
    private final AtomicLong journalFileIndex = new AtomicLong(0L);

    // Last Wal File Channel
    private FileChannel lastWalFileChannel = null;

    // Re-entrant lock for creation of WAL File
    private final Boolean transactionFileLock = Boolean.TRUE;

    /**
     * Get WAL Transaction File. This will get the appropriate file channel and return it
     *
     * @return Open File Channel
     * @throws TransactionException Cannot write transaction
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public FileChannel getTransactionFile() throws TransactionException {
        synchronized (transactionFileLock) {

            try {
                if (lastWalFileChannel == null) {

                    // Create the journaling directory if it does'nt exist
                    final String directory = getWALDirectory();

                    File journalingDirector = new File(directory);
                    if (!journalingDirector.exists()) {
                        journalingDirector.mkdirs();
                    }

                    // Grab the last used WAL File
                    final String[] directoryListing = new File(directory).list();
                    Arrays.sort(directoryListing);

                    File lastWalFile;

                    if (directoryListing.length > 0) {
                        String fileName = directoryListing[directoryListing.length - 1];
                        fileName = fileName.replace(".wal", "");

                        journalFileIndex.addAndGet(Integer.valueOf(fileName));
                    }

                    lastWalFile = new File(directory + journalFileIndex.get() + ".wal");

                    if (!lastWalFile.exists()) {
                        lastWalFile.createNewFile();
                    }

                    // Open file channel
                    lastWalFileChannel = FileUtil.openFileChannel(lastWalFile.getPath());
                }

                // If the last wal file exceeds longSize limit threshold, create a new one
                if (lastWalFileChannel.size() > MAX_JOURNAL_SIZE) {

                    // Close the previous
                    lastWalFileChannel.force(true);
                    lastWalFileChannel.close();

                    final String directory = getWALDirectory();
                    final File lastWalFile = new File(directory + journalFileIndex.addAndGet(1) + ".wal");
                    lastWalFile.createNewFile();

                    lastWalFileChannel = FileUtil.openFileChannel(lastWalFile.getPath());
                }

                return lastWalFileChannel;

            } catch (IOException e) {
                throw new TransactionException(TransactionException.TRANSACTION_FAILED_TO_OPEN_FILE);
            }
        }
    }

    private TransactionController transactionController = null;

    /**
     * Get Controller that handles transactions. This creates a log of persistence within the database.
     *
     * @return Transaction Controller implementation.
     */
    public TransactionController getTransactionController() {
        return this.transactionController;
    }

    /**
     * Get Directory where wal files are located.
     *
     * @return get Directory where wal files are located.
     */
    private String getWALDirectory() {
        return this.location + File.separator + "wal" + File.separator;
    }

    /**
     * Shutdown schema context. Close files, connections or any other IO mechanisms used within the context
     *
     * @since 1.0.0
     */
    public void shutdown() {
        killSwitch = true;

        // Shutdown all databases
        for (final MapBuilder db : dataFiles.values()) {

            try {
                db.commit();
                db.close();
            } catch (Exception ignore) {
            }

        }

        // Shutdown all databases temporary disk map builders
        while (true) {
            try {
                temporaryDiskMapQueue.remove();
            } catch (Exception ignore) {
                break;
            }
        }

        // Added to ensure all builders are closed whether they are checked out or not
        for(MapBuilder builder : temporaryMaps)
        {
            builder.close();
            builder.delete();
        }

        // Close transaction file
        if (lastWalFileChannel != null) {

            try {
                FileUtil.closeFileChannel(lastWalFileChannel);
            } catch (IOException ignore) {
            }
        }

        ScannerFactory.getInstance(this).reset();

        dataFiles.clear(); // Clear all data files
        descriptors.clear(); // Clear all descriptors
        recordControllers.clear(); // Clear all Record Controllers
        relationshipControllers.clear(); // Clear all relationship controllers
        indexControllers.clear(); // Clear all index controllers

        scheduler.shutdown();

    }

    ///////////////////////////////////////////////////////////////
    //
    // Data File collection
    //
    ///////////////////////////////////////////////////////////////
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Map of data files.
     *
     * @since 1.0.0
     */
    final CompatMap<String, MapBuilder> dataFiles = new SynchronizedMap<>();

    /**
     * @since 1.0.0 Method for creating a new data file
     */
    private final Function createDataFile = new Function<String, MapBuilder>() {
        @Override
        public MapBuilder apply(final String path) {
            return new DefaultMapBuilder(location + "/" + path, context);
        }
    };

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param descriptor Record Entity Descriptor
     * @return Underlying data storage factory
     * @since 1.0.0
     */
    public MapBuilder getDataFile(final EntityDescriptor descriptor) {
        return dataFiles.computeIfAbsent(descriptor.getFileName() +
                ((descriptor.getPartition() == null) ? "" : descriptor.getPartition().getPartitionValue()), createDataFile);
    }

    /**
     * Return the corresponding data storage mechanism for the entity matching the descriptor that pertains to a partitionID.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param baseDescriptor Record Entity Descriptor
     * @param partitionId    Partition the records belong to
     * @return Underlying data storage factory
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public MapBuilder getPartitionDataFile(final EntityDescriptor baseDescriptor, final long partitionId) throws EntityException {
        if (location == null) {
            return null;
        }

        if (partitionId == 0) {
            return getDataFile(baseDescriptor);
        }

        final Query query = new Query(SystemPartitionEntry.class, new QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId));
        final List<SystemPartitionEntry> partitions = systemPersistenceManager.executeQuery(query);
        final SystemPartitionEntry partition = partitions.get(0);

        return getDataFile(getDescriptorForEntity(baseDescriptor.getClazz(), partition.getValue()));
    }

    /**
     * Get Partition Entry for entity.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param classToGet     Entity type for record
     * @param partitionValue Partition Value
     * @return System Partition Entry for class with partition key
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public SystemPartitionEntry getPartitionWithValue(final Class classToGet, final Object partitionValue) throws EntityException {
        final Query query = new Query(SystemPartitionEntry.class,
                new QueryCriteria("id", QueryCriteriaOperator.EQUAL, classToGet.getName() + String.valueOf(partitionValue)));
        final List<SystemPartitionEntry> partitions = systemPersistenceManager.executeQuery(query);

        if (partitions.size() == 0) {
            return null;
        } else {
            return partitions.get(0);
        }
    }

    /**
     * Get System Partition with Id.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param partitionId Partition ID
     * @return System Partition Entry for class with partition id
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public SystemPartitionEntry getPartitionWithId(final long partitionId) throws EntityException {
        final Query query = new Query(SystemPartitionEntry.class, new QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId));
        final List<SystemPartitionEntry> partitions = systemPersistenceManager.executeQuery(query);

        if (partitions.size() == 0) {
            return null;
        } else {
            return partitions.get(0);
        }
    }

    ///////////////////////////////////////////////////////////////
    //
    // Record Controllers
    //
    ///////////////////////////////////////////////////////////////
    /**
     * Map of record controllers.
     *
     * @since 1.0.0
     */
    private final CompatMap<EntityDescriptor, RecordController> recordControllers = new SynchronizedMap<>();

    /**
     * Method for creating a new record controller.
     *
     * @since 1.0.0
     */
    private final Function createRecordController = new Function<EntityDescriptor, RecordController>() {
        @Override
        public RecordController apply(final EntityDescriptor descriptor) {
            if (descriptor.getIdentifier().getGenerator() == IdentifierGenerator.SEQUENCE) {
                return new SequenceRecordControllerImpl(descriptor, context);
            }

            return new RecordControllerImpl(descriptor, context);
        }
    };

    /**
     * Get Record Controller.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param descriptor Record's Entity Descriptor
     * @return get Record Controller.
     * @since 1.0.0
     */
    public RecordController getRecordController(final EntityDescriptor descriptor) {
        return recordControllers.computeIfAbsent(descriptor, createRecordController);
    }

    //////////////////////////////////////////////////////////////////
    //
    // Entity Descriptors
    //
    //////////////////////////////////////////////////////////////////
    // Contains the initialized entity descriptors
    private final CompatMap<String, EntityDescriptor> descriptors = new CompatHashMap();

    private final AtomicLong partitions = new AtomicLong(0);

    private final ReadWriteLock entityDescriptorLock = new ReentrantReadWriteLock(false);

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param entity      Entity Instance
     * @param partitionId Partition Field Value
     * @return Record's entity descriptor
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public EntityDescriptor getDescriptorForEntity(final Object entity, Object partitionId) throws EntityException {
        if (partitionId == null) {
            partitionId = "";
        }

        final String entityKey = entity.getClass().getName() + String.valueOf(partitionId);

        entityDescriptorLock.readLock().lock();

        try {
            EntityDescriptor descriptor = descriptors.get(entityKey);

            if (descriptor != null) {
                return descriptor;
            }
        } finally {
            entityDescriptorLock.readLock().unlock();
        }

        entityDescriptorLock.writeLock().lock();

        try {

            EntityDescriptor descriptor = new EntityDescriptor(entity.getClass());

            if (descriptor.getPartition() != null) {
                descriptor.getPartition().setPartitionValue(String.valueOf(partitionId));
            }

            descriptors.put(entityKey, descriptor);

            // Get the latest System Entity
            SystemEntity systemEntity = this.getSystemEntityByName(descriptor.getClazz().getName());

            // If it does not exist, lets create a new one
            if (systemEntity == null) {
                systemEntity = new SystemEntity(descriptor);
                systemPersistenceManager.saveEntity(systemEntity);
            }

            checkForValidDescriptorPartition(descriptor, systemEntity);
            checkForEntityChanges(descriptor, systemEntity);
            EntityClassLoader.writeClass(descriptor, context.getLocation(), context);

            return descriptor;
        } finally {
            entityDescriptorLock.writeLock().unlock();
        }
    }

    /**
     * This method will detect to see if there are any entity changes.  If so, it will create a new SystemEntity record
     * to reflect the new version and serializer
     *
     * @param descriptor   Base Entity Descriptor
     * @param systemEntity Current system entity key to base the comparison on the new entity descriptor
     * @return Newly created system entity if it was created otherwise the existing one
     * @throws EntityException default exception
     * @since 1.1.0
     */
    @SuppressWarnings("UnusedReturnValue")
    private SystemEntity checkForEntityChanges(EntityDescriptor descriptor, SystemEntity systemEntity) throws EntityException {
        // Re-Build indexes if necessary
        descriptor.checkIndexChanges(systemEntity, rebuildIndexConsumer);

        // Check to see if the relationships were not changed from a to many to a to one
        descriptor.checkValidRelationships(systemEntity);

        //noinspection EqualsBetweenInconvertibleTypes
        if (!descriptor.equals(systemEntity)) {
            systemEntity = new SystemEntity(descriptor);
            systemPersistenceManager.saveEntity(systemEntity);
        }

        defaultSystemEntities.put(systemEntity.getName(), systemEntity);
        systemEntityByIDMap.put(systemEntity.getPrimaryKey(), systemEntity);

        return systemEntity;
    }

    /**
     * Checks to see if a partition already exists for the corresponding entity descriptor.  If it does not, lets create it.
     *
     * @param descriptor   Entity descriptor to base the new partition on or to cross reference the old one
     * @param systemEntity System entity to get from the database and compare partition on
     * @since 1.1.0
     */
    private void checkForValidDescriptorPartition(EntityDescriptor descriptor, SystemEntity systemEntity) throws EntityException {
        // Check to see if the partition already exists
        if ((systemEntity.getPartition() != null) && (descriptor.getPartition() != null)) {
            for (int i = 0; i < systemEntity.getPartition().getEntries().size(); i++) {

                if (systemEntity.getPartition().getEntries().get(i).getValue().equals(
                        descriptor.getPartition().getPartitionValue())) {
                    // It does yay, lets return
                    return;
                }
            }
        }

        // Add a new partition entry if it does not exist
        if (descriptor.getPartition() != null) {
            if (systemEntity.getPartition() == null) {
                systemEntity.setPartition(new SystemPartition(descriptor.getPartition(), systemEntity));
            }

            SystemPartitionEntry entry = new SystemPartitionEntry(descriptor, descriptor.getPartition(),
                    systemEntity.getPartition(), partitions.incrementAndGet());
            systemEntity.getPartition().getEntries().add(entry);
            systemPersistenceManager.saveEntity(entry);
        }
    }

    // System Entities
    private final CompatMap<String, SystemEntity> defaultSystemEntities = new CompatHashMap();

    /**
     * Get System Entity By Name.
     *
     * @param name System Entity Name
     * @return Latest System Entity matching that name
     * @throws EntityException Default Exception
     */
    public synchronized SystemEntity getSystemEntityByName(final String name) throws EntityException {
        return defaultSystemEntities.computeIfAbsent(name,
                (s) -> {

                    final Query query = new Query(SystemEntity.class, new QueryCriteria("name", QueryCriteriaOperator.EQUAL, s));
                    query.setMaxResults(1);
                    query.setQueryOrders(Collections.singletonList(new QueryOrder("primaryKey", false)));

                    List<SystemEntity> results = null;

                    try {
                        results = systemPersistenceManager.executeQuery(query);
                    } catch (EntityException e) {
                        return null;
                    }

                    if (results.size() > 0) {
                        Collections.sort(results.get(0).getAttributes(), (o1, o2) -> o1.getName().compareTo(o2.getName()));
                        Collections.sort(results.get(0).getRelationships(), (o1, o2) -> o1.getName().compareTo(o2.getName()));
                        Collections.sort(results.get(0).getIndexes(), (o1, o2) -> o1.getName().compareTo(o2.getName()));

                        return results.get(0);
                    }

                    return null;
                });
    }

    // System Entities
    private final CompatMap<Integer, SystemEntity> systemEntityByIDMap = new CompatHashMap();

    /**
     * Get System Entity By ID.
     *
     * @param systemEntityId Unique identifier for system entity version
     * @return System Entity matching ID
     */
    public synchronized SystemEntity getSystemEntityById(final int systemEntityId) {
        return systemEntityByIDMap.compute(systemEntityId,
                (id, systemEntity) ->
                {

                    if (systemEntity != null) {
                        Collections.sort(systemEntity.getAttributes(), (o1, o2) -> o1.getName().compareTo(o2.getName()));
                        Collections.sort(systemEntity.getRelationships(), (o1, o2) -> o1.getName().compareTo(o2.getName()));
                        Collections.sort(systemEntity.getIndexes(), (o1, o2) -> o1.getName().compareTo(o2.getName()));

                        return systemEntity;
                    }

                    try {
                        final SystemEntity entity = (SystemEntity) systemPersistenceManager.findById(SystemEntity.class, id);

                        if (entity != null) {
                            Collections.sort(entity.getAttributes(), (o1, o2) -> o1.getName().compareTo(o2.getName()));
                            Collections.sort(entity.getRelationships(), (o1, o2) -> o1.getName().compareTo(o2.getName()));
                            Collections.sort(entity.getIndexes(), (o1, o2) -> o1.getName().compareTo(o2.getName()));
                        }

                        return entity;
                    } catch (EntityException e) {
                        return null;
                    }
                });
    }

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param entityClass Entity Type
     * @param partitionId Partition Id
     * @return Entity Descriptor for class and partition id
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public EntityDescriptor getDescriptorForEntity(final Class entityClass, Object partitionId) throws EntityException {

        if (partitionId == null) {
            partitionId = "";
        }

        if (entityClass == null)
            throw new EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND);

        final String entityKey = entityClass.getName() + String.valueOf(partitionId);

        entityDescriptorLock.readLock().lock();

        try {
            EntityDescriptor descriptor = descriptors.get(entityKey);

            if (descriptor != null) {
                return descriptor;
            }
        } finally {
            entityDescriptorLock.readLock().unlock();
        }

        entityDescriptorLock.writeLock().lock();

        try {

            EntityDescriptor descriptor = new EntityDescriptor(entityClass);

            if (descriptor.getPartition() != null) {
                descriptor.getPartition().setPartitionValue(String.valueOf(partitionId));
            }

            descriptors.put(entityKey, descriptor);

            // Get the latest System Entity
            SystemEntity systemEntity = this.getSystemEntityByName(descriptor.getClazz().getName());

            // If it does not exist, lets create a new one
            if (systemEntity == null) {
                systemEntity = new SystemEntity(descriptor);
                systemPersistenceManager.saveEntity(systemEntity);
            }

            checkForValidDescriptorPartition(descriptor, systemEntity);
            checkForEntityChanges(descriptor, systemEntity);

            EntityClassLoader.writeClass(descriptor, context.getLocation(), context);

            return descriptor;
        } finally {
            entityDescriptorLock.writeLock().unlock();
        }
    }

    /**
     * Get Descriptor and have it automatically determine the partition ID.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param entity Entity Instance
     * @return Record's entity descriptor
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public EntityDescriptor getDescriptorForEntity(final Object entity) throws EntityException {
        if (!(entity instanceof IManagedEntity)) {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.PERSISTED_NOT_FOUND, entity.getClass());
        }

        final Object partitionId = PartitionHelper.getPartitionFieldValue((IManagedEntity) entity, this);

        return getDescriptorForEntity(entity, partitionId);
    }

    /**
     * Get Descriptor For Entity. Initializes EntityDescriptor or returns one if it already exists
     *
     * @param entityClass Entity Type
     * @return Entity Descriptor for class
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public EntityDescriptor getBaseDescriptorForEntity(final Class entityClass) throws EntityException {

        if (entityClass == null)
            return null;
        final String entityKey = entityClass.getName();

        entityDescriptorLock.readLock().lock();

        try {
            EntityDescriptor descriptor = descriptors.get(entityKey);

            if (descriptor != null) {
                return descriptor;
            }
        } finally {
            entityDescriptorLock.readLock().unlock();
        }

        entityDescriptorLock.writeLock().lock();

        try {

            EntityDescriptor descriptor = new EntityDescriptor(entityClass);
            descriptors.put(entityKey, descriptor);

            // Get the latest System Entity
            SystemEntity systemEntity = this.getSystemEntityByName(entityKey);

            // If it does not exist, lets create a new one
            if (systemEntity == null) {
                systemEntity = new SystemEntity(descriptor);
                systemPersistenceManager.saveEntity(systemEntity);
            }

            checkForValidDescriptorPartition(descriptor, systemEntity);
            checkForEntityChanges(descriptor, systemEntity);

            EntityClassLoader.writeClass(descriptor, context.getLocation(), context);

            return descriptor;
        } finally {
            entityDescriptorLock.writeLock().unlock();
        }
    }

    ///////////////////////////////////////////////////////////////
    //
    // Relationship Controllers
    //
    ///////////////////////////////////////////////////////////////
    /**
     * Map of record controllers.
     */
    private final CompatMap<RelationshipDescriptor, RelationshipController> relationshipControllers = new CompatWeakHashMap();

    private final ReadWriteLock relationshipControllerReadWriteLock = new ReentrantReadWriteLock();

    /**
     * Get Relationship Controller that corresponds to the relationship descriptor.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param relationshipDescriptor Relationship Descriptor
     * @return Relationship Controller corresponding to relationship descriptor
     * @throws EntityException Generic Exception
     * @since 1.0.0
     */
    public RelationshipController getRelationshipController(final RelationshipDescriptor relationshipDescriptor)
            throws EntityException {

        RelationshipController retVal;

        relationshipControllerReadWriteLock.readLock().lock();
        try {
            retVal = relationshipControllers.get(relationshipDescriptor);

            if (retVal != null)
                return retVal;
        } finally {
            relationshipControllerReadWriteLock.readLock().unlock();
        }

        if ((relationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY) ||
                (relationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY)) {

            retVal = new ToManyRelationshipControllerImpl(relationshipDescriptor.getEntityDescriptor(), relationshipDescriptor, context);
        } else {
            retVal = new ToOneRelationshipControllerImpl(relationshipDescriptor.getEntityDescriptor(), relationshipDescriptor, context);
        }

        relationshipControllerReadWriteLock.writeLock().lock();
        try {

            relationshipControllers.put(relationshipDescriptor, retVal);
            return retVal;
        } finally {
            relationshipControllerReadWriteLock.writeLock().unlock();
        }
    }

    ///////////////////////////////////////////////////////////////
    //
    // Index Controllers
    //
    ///////////////////////////////////////////////////////////////
    /**
     * Map of record controllers.
     */
    private final CompatMap<IndexDescriptor, IndexController> indexControllers = new SynchronizedMap<>();

    /**
     * Method for creating a new index controller.
     */
    private final Function createIndexController = new Function<IndexDescriptor, IndexController>() {
        @Override
        public IndexController apply(final IndexDescriptor descriptor) {
            try {
                return new IndexControllerImpl(descriptor.getEntityDescriptor(), descriptor, context);
            } catch (EntityException e) {
                return null;
            }
        }
    };

    /**
     * Get Index Controller with Index descriptor.
     * <p>
     * <p>This is not meant to be a public API.</p>
     *
     * @param indexDescriptor Index Descriptor
     * @return Corresponding record controller
     * @since 1.0.0
     */
    public IndexController getIndexController(final IndexDescriptor indexDescriptor) {
        return indexControllers.computeIfAbsent(indexDescriptor, createIndexController);
    }

    final private ArrayBlockingQueue<MapBuilder> temporaryDiskMapQueue = new ArrayBlockingQueue<>(32, false);

    /**
     * Create Temporary Map Builder.
     *
     * @return Create new storage mechanism factory
     * @since 1.3.0 Changed to use a pool of map builders.
     * The intent of this is to increase performance.  There was a performance
     * issue with map builders being destroyed invoking the DirectBuffer cleanup.
     * That was not performant.
     */
    public MapBuilder createTemporaryMapBuilder() {
        try {
            return temporaryDiskMapQueue.take();
        } catch (InterruptedException ignore) {}
        return null;
    }

    /**
     * Recycle a temporary map builder so that it may be re-used
     *
     * @param builder Discarded map builder
     * @since 1.3.0
     */
    public void releaseMapBuilder(MapBuilder builder) {
        builder.reset();
        temporaryDiskMapQueue.offer(builder);
    }

    /**
     * Consumer that initiates a new index rebuild.
     */
    private final Consumer<IndexDescriptor> rebuildIndexConsumer = indexDescriptor ->
    {
        try {

            final SystemEntity systemEntity = getSystemEntityByName(indexDescriptor.getEntityDescriptor().getClazz().getName());

            if (systemEntity.getPartition() != null) {
                final List<SystemPartitionEntry> entries = systemEntity.getPartition().getEntries();
                SystemPartitionEntry entry = null;

                for (SystemPartitionEntry entry1 : entries) {
                    entry = entry1;

                    final EntityDescriptor partitionEntityDescriptor = getDescriptorForEntity(indexDescriptor.getEntityDescriptor()
                            .getClazz().getName(), entry.getValue());
                    indexDescriptor = partitionEntityDescriptor.getIndexes().get(indexDescriptor.getName());

                    final IndexController indexController = getIndexController(indexDescriptor);
                    final Runnable indexBuildThread = () ->
                    {

                        try {
                            indexController.rebuild();
                        } catch (EntityException ignore) {
                        }
                    };
                    indexBuildThread.run();

                }
            } else {
                final IndexController indexController = getIndexController(indexDescriptor);
                final Runnable indexBuildThread = () ->
                {

                    try {
                        indexController.rebuild();
                    } catch (EntityException ignore) {
                    }
                };
                indexBuildThread.run();
            }
        } catch (EntityException ignore) {
        }
    };

    private CompatMap<Query, CachedResults> cachedQueryResults = new SynchronizedMap<>();

    public CachedResults getCachedQueryResults(Query query)
    {
        return cachedQueryResults.get(query);
    }

    public void setCachedQueryResults(Query query, CachedResults cachedResults)
    {
        cachedQueryResults.put(query, cachedResults);
    }

}
