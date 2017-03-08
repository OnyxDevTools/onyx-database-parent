package com.onyx.diskmap;

import com.onyx.diskmap.base.*;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.serializer.Serializers;
import com.onyx.diskmap.store.*;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.SynchronizedMap;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by timothy.osborn on 3/25/15.
 * <p>
 * This class is responsible for building all maps.  There is one map builder per file and or storage
 */
@SuppressWarnings("unchecked")
public class DefaultMapBuilder implements MapBuilder {

    @SuppressWarnings("WeakerAccess")
    protected Store storage = null;

    private static final AtomicInteger storeIdCounter = new AtomicInteger(0);

    private static volatile Boolean memMapIsSupprted = null;

    // Contains all initialized maps
    @SuppressWarnings("WeakerAccess")
    protected final CompatMap<String, CompatMap> maps = new SynchronizedMap(new CompatWeakHashMap<>());

    // Contains all initialized maps
    private final CompatMap<Header, CompatMap> mapsByHeader = new SynchronizedMap(new CompatWeakHashMap());

    // Internal map that runs on storage
    @SuppressWarnings("WeakerAccess")
    protected CompatMap<String, Long> internalMaps = null;

    /**
     * Default Constructor
     */
    @SuppressWarnings("unused")
    public DefaultMapBuilder() {
    }

    /**
     * Constructor
     *
     * @param filePath Where the file is located to store the maps
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public DefaultMapBuilder(String filePath) {
        this(filePath, StoreType.MEMORY_MAPPED_FILE, null);
    }

    /**
     * Constructor
     *
     * @param filePath Where the location of the builder feeds off of
     * @param context  If this is attached to an OnyxDB this will be populated
     * @since 1.0.0
     */
    public DefaultMapBuilder(String filePath, SchemaContext context) {
        this(filePath, StoreType.MEMORY_MAPPED_FILE, context);
    }

    /**
     * Constructor
     *
     * @param filePath Where the location of the builder feeds off of
     * @param type     Type of storage mechanism
     *
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public DefaultMapBuilder(String filePath, StoreType type) {
        this(filePath, type, null);
    }

    /**
     * Constructor for a database
     *
     * @param filePath File path to hold the disk structure data
     * @param type     Storage type
     * @param context  Database Schema context
     * @since 1.0.0
     */
    public DefaultMapBuilder(String filePath, StoreType type, SchemaContext context) {
        this("", filePath, type, context, false);
    }

    /**
     * Constructor with option to force or not
     *
     * @param filePath      File path to hold the disk structure data
     * @param type          Storage type
     * @param context       Database Schema context
     * @param deleteOnClose Whether to delete storage volumes upon close.  This is used for temporary maps
     *
     * @since 1.2.0
     */
    public DefaultMapBuilder(String filePath, @SuppressWarnings("SameParameterValue") StoreType type, SchemaContext context, @SuppressWarnings("SameParameterValue") boolean deleteOnClose) {
        this("", filePath, type, context, deleteOnClose);
    }

    /**
     * Constructor
     *
     * @param filePath File path to hold the disk structure data
     * @since 1.0.0
     */
    @SuppressWarnings("WeakerAccess")
    public DefaultMapBuilder(@SuppressWarnings("SameParameterValue") String fileSystemPath, String filePath, StoreType type, SchemaContext context, boolean deleteOnClose) {
        String path;

        if (fileSystemPath == null || fileSystemPath.equals(""))
            path = filePath;
        else
            path = fileSystemPath + File.separator + filePath;

        if (type == StoreType.MEMORY_MAPPED_FILE && isMemmapSupported()) {
            this.storage = new MemoryMappedStore(path, context, deleteOnClose);
        } else if (type == StoreType.FILE || (type == StoreType.MEMORY_MAPPED_FILE && !isMemmapSupported())) {
            this.storage = new FileChannelStore(path, context, deleteOnClose);
        } else if (type == StoreType.IN_MEMORY) {
            String storeId = String.valueOf(storeIdCounter.addAndGet(1));
            this.storage = new InMemoryStore(context, storeId);
        }

        // Create default maps.  Thes locations indicate the spacing between initial map allocations.  In this case,
        // it indicates a load factor map with a load factor of 1.  If the allocation changes such as the header
        // or the default allocation within the maps' constructors and/or the serializer map types, this will need
        // to be changed.
        int initialSize = 8;
        int mapByIdLocation = 156;
        int internalMapLocation = 304;

        Map mapByName;
        Map mapById;
        if (storage.getFileSize() == initialSize) {
            mapByName = newScalableMap(this.storage, newMapHeader(), 1);
            mapById = newScalableMap(this.storage, newMapHeader(), 1);
            internalMaps = newScalableMap(this.storage, newMapHeader(), 1);
        } else {
            mapByName = getHashMap((Header) storage.read(initialSize, Header.HEADER_SIZE, Header.class), 1);
            mapById = getHashMap((Header) storage.read(mapByIdLocation, Header.HEADER_SIZE, Header.class), 1);
            internalMaps = getHashMap((Header) storage.read(internalMapLocation, Header.HEADER_SIZE, Header.class), 1);
        }

        if (this.storage != null)
            this.storage.init(mapById, mapByName);

    }

    /**
     * Get Disk Map with the ability to dynamically change the load factor.  Meaning change how it scales dynamically
     *
     * @param header     reference within storage
     *
     *
     * @param loadFactor Value from 1-10.
     *
     *                   The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     *                   1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     *                   does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     *                   to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated disk structure
     *
     * @since 1.0.0
     */
    @Override
    public CompatMap getHashMap(Header header, int loadFactor) {
        return mapsByHeader.compute(header, (header1, map) -> {
            if (map != null)
                return map;
            return newScalableMap(storage, header, loadFactor);
        });
    }

    /**
     * Default Map factory.  This creates or gets a map based on the name and puts it into a map
     *
     * @param name identifier of a map
     * @param type Type of map
     * @return Created map
     *
     * @since 1.2.0
     */
    @SuppressWarnings("WeakerAccess")
    protected CompatMap getMapWithType(String name, MapType type, int loadFactor) {

        return maps.compute(name, (s, map) -> {
            if (map != null)
                return map;

            Header header = null;
            Long headerReference = internalMaps.get(name);
            if (headerReference != null)
                header = (Header) storage.read(headerReference, Header.HEADER_SIZE, Header.class);

            // Create a new header for the new structure we are creating
            if (header == null) {
                header = new Header();
                header.position = storage.allocate(Header.HEADER_SIZE);
                storage.write(header, header.position);
                internalMaps.put(name, header.position);
            }

            CompatMap retVal = null;
            switch (type) {
                case SKIPLIST:
                    retVal = newSkipListMap(storage, header);
                    break;
                case LOAD:
                    retVal = newScalableMap(storage, header, loadFactor);
                    break;
            }
            return retVal;
        });
    }

    /**
     * Create a new Map reference header
     * @return the created header ripe for instantiating a new map
     *
     * @since 1.2.0
     */
    public Header newMapHeader() {
        Header header = new Header();
        header.position = storage.allocate(Header.HEADER_SIZE);
        storage.write(header, header.position);
        return header;
    }

    /**
     * Get the instance of a map.  Based on the loadFactor it may be a multi map with a hash index followed by a skip list
     * or a multi map with a hash matrix followed by a skip list.
     *
     * @param name       Name of the map to uniquely identify it
     *
     * @param loadFactor Value from 1-10.
     *
     *                   The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     *                   1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     *                   does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     *                   to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated map with storage
     * @since 1.1.0
     *
     * Note, this was changed to use what was being referred to as a DefaultDiskMap which was a parent of AbstractBitmap.
     * It is now an implemenation of an inter changable index followed by a skip list.
     *
     */
    @Override
    public Map getHashMap(String name, int loadFactor) {
        return getMapWithType(name, MapType.LOAD, loadFactor);
    }

    /**
     * Get a map with a skip list index
     *
     * @param name Name of the map to uniquely identify it
     * @return Instantiated map with skip list
     */
    @Override
    public Map getSkipListMap(String name) {
        return getMapWithType(name, MapType.SKIPLIST, 10);
    }

    /**
     * Close the file stores
     */
    public void close() {
        storage.close();
    }

    /**
     * Force the storage to persist
     */
    public void commit() {
        storage.commit();
    }

    /**
     * Check if large files can be mapped into memory.
     * For example 32bit JVM can only address 2GB and large files can not be mapped,
     * so for 32bit JVM this function returns false.
     *
     * @since 1.2.2 Add additional check to ensure DirectBuffers exist
     */
    private static boolean isMemmapSupported() {
        if (memMapIsSupprted != null)
            return memMapIsSupprted;
        memMapIsSupprted = false;
        String prop = System.getProperty("os.arch");
        try {
            memMapIsSupprted = prop != null && prop.contains("64") && Class.forName("sun.nio.ch.DirectBuffer") != null;
        } catch (ClassNotFoundException e) {
            memMapIsSupprted = false;
        }
        return memMapIsSupprted;
    }

    /**
     * Delete file
     */
    public void delete() {
        this.close();
        this.storage.delete();
    }

    /**
     * Getter for serializers
     *
     * @return Serializers the storage file uses to serialize
     */
    public Serializers getSerializers() {
        return this.storage.getSerializers();
    }

    /**
     * Get the file store in order to re-attach a data structure
     *
     * @return The store the map builder uses
     */
    @SuppressWarnings("unused")
    public Store getStore() {
        return storage;
    }

    /////////////////////////////////////////////////////////////////////////////
    //
    // Map instantiation
    //
    /////////////////////////////////////////////////////////////////////////////

    /**
     * Create a new Disk Map.  This uses both a skip list and a Bitmap index
     *
     * @param store      File Storage
     * @param header     Reference Node
     * @param loadFactor Value from 1-10.
     *                   <p>
     *                   The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     *                   1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     *                   does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     *                   to change.  Always plan for scale when designing your data model.
     * @return Instantiated disk map
     */
    @SuppressWarnings("WeakerAccess")
    protected DiskMap newScalableMap(Store store, Header header, int loadFactor) {
        if (loadFactor < 5)
            return new DiskMultiHashMap(store, header, loadFactor);
        return new DiskMultiMatrixHashMap(store, header, loadFactor);
    }

    /**
     * Create a new Disk Map.  This uses both a skip list
     *
     * @param store  File Storage
     * @param header Reference Node
     * @return Instantiated disk map
     */
    private DiskMap newSkipListMap(Store store, Header header) {
        return new DiskSkipListMap(store, header);
    }

    /**
     * Create a hash map with a given header.  This should not be invoked unless it is used to grab a stateless
     * instance of a disk map.  Stateless meaning, the header has already been setup.  Note, this is not thread safe
     * If you were to invoke this with the same header and use the maps concurrently you WILL corrupt your data.
     * You must use alternative means of thread safety.
     *
     * Since this implementation is stateless, it does not provide caching nor thread safety.
     *
     * @param header     Head of the disk map
     * @param loadFactor Load factor in which the map was instantiated with.
     * @return Stateless instance of a disk map
     *
     * @since 1.2.0
     */
    public DiskMap newHashMap(Header header, int loadFactor) {
        return new DiskMultiHashMap(storage, header, loadFactor, false);
    }

    /**
     * Get Hash Map by Name.  This will default the map with a loadFactor of 10.  In that case, it will return an
     * instance of the hash matrix followed by a skip list.
     *
     * @param name Unique Map name
     * @return Disk Map implementation
     * @since 1.2.0
     */
    public Map getHashMap(String name) {
        return getHashMap(name, 10);
    }

}
