package com.onyx.persistence.context.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.diskmap.DefaultMapBuilder;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.store.StoreType;
import com.onyx.persistence.query.impl.DefaultQueryCacheController;

import java.util.function.Function;

/**
 * The purpose of this class is to resolve all the the metadata, storage mechanism,  and modeling regarding the structure of the database.
 *
 * In this case it is an in-memory data storage.  No data will be persisted.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 * CacheManagerFactory factory = new CacheManagerFactory();
 * factory.initialize(); // Create context and connection
 *
 * SchemaContext context = factory.getSchemaContext(); // Returns CacheSchemaContext instance
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close();  // Close connection to database
 *
 * </code>
 * </pre>
 *
 */
public class CacheSchemaContext extends DefaultSchemaContext
{

    /**
     * Constructor
     */
    public CacheSchemaContext(String contextId, String location)
    {
        super(contextId, location);
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
            MapBuilder builder = new DefaultMapBuilder(location, StoreType.IN_MEMORY, this.context, true);
            temporaryDiskMapQueue.add(builder);
            temporaryMaps.add(builder);
        }
    }

    /**
     * Method for creating a new data storage factory
     * @since 1.0.0
     */
    private final Function createDataFile = new Function<String, MapBuilder>() {
        @Override
        public MapBuilder apply(String path)
        {
            return new DefaultMapBuilder(location + "/" + path, StoreType.IN_MEMORY, context);
        }
    };

    /**
     * Return the corresponding data file for the descriptor
     *
     * @since 1.0.0
     * @param descriptor Record Entity Descriptor
     *
     * @return Data storage mechanism factory
     */
    @SuppressWarnings("unchecked")
    @Override
    public synchronized MapBuilder getDataFile(EntityDescriptor descriptor)
    {
        return dataFiles.computeIfAbsent(descriptor.getFileName() + ((descriptor.getPartition() == null) ? "" : descriptor.getPartition().getPartitionValue()), createDataFile);
    }

    /**
     * Create Temporary Map Builder.
     *
     * @return Create new storage mechanism factory
     * @since 1.3.0 Changed to use a pool of map builders.
     * The intent of this is to increase performance.  There was a performance
     * issue with map builders being destroyed invoking the DirectBuffer cleanup.
     * That was not performant.
     */
    @Override
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
    @Override
    public void releaseMapBuilder(MapBuilder builder) {
        builder.reset();
        temporaryDiskMapQueue.offer(builder);
    }

    /**
     * Start the context and initialize storage or any other IO mechanisms used within the schema context.
     *
     * @since 1.0.0
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void start() {
        createTemporaryDiskMapPool();

        this.queryCacheController = new DefaultQueryCacheController(this);

        killSwitch = false;
        initializeSystemEntities();
        initializePartitionSequence();
        initializeEntityDescriptors();
    }

}
