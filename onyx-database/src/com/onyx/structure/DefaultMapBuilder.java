package com.onyx.structure;

import com.onyx.persistence.context.SchemaContext;
import com.onyx.structure.base.DiskSkipList;
import com.onyx.structure.base.ScaledDiskMap;
import com.onyx.structure.node.Header;
import com.onyx.structure.serializer.Serializers;
import com.onyx.structure.store.*;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * This class is responsible for building all maps.  There is one map builder per file and or storage
 */
@SuppressWarnings("unchecked")
public class DefaultMapBuilder implements MapBuilder {

    private Store storage = null;

    private static AtomicInteger storeIdCounter = new AtomicInteger(0);

    // Contains all initialized maps
    private Map<String, Map> maps = Collections.synchronizedMap(new WeakHashMap());

    // Contains all initialized maps
    private Map<Header, Map> mapsByHeader = Collections.synchronizedMap(new WeakHashMap());

    // Internal map that runs on storage
    private Map<String, Long> internalMaps = null;

    // Internal serializer maps.  Created by default
    private Map mapByName;
    private Map mapById;

    /**
     * Constructor
     *
     * @param filePath Where the file is located to store the maps
     */
    public DefaultMapBuilder(String filePath) {
        this(filePath, StoreType.MEMORY_MAPPED_FILE, null);
    }

    /**
     * Constructor
     *
     * @param filePath Where the location of the builder feeds off of
     * @param context  If this is attached to an OnyxDB this will be populated
     */
    public DefaultMapBuilder(String filePath, SchemaContext context) {
        this(filePath, StoreType.MEMORY_MAPPED_FILE, context);
    }

    /**
     * Constructor
     *
     * @param filePath Where the location of the builder feeds off of
     * @param type     Type of storage mechanism
     */
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
        this("", filePath, type, context);
    }

    /**
     * Constructor
     *
     * @param filePath File path to hold the disk structure data
     * @since 1.0.0
     */
    public DefaultMapBuilder(String fileSystemPath, String filePath, StoreType type, SchemaContext context) {
        String path;

        if (fileSystemPath == null || fileSystemPath.equals(""))
            path = filePath;
        else
            path = fileSystemPath + File.separator + filePath;

        if (type == StoreType.MEMORY_MAPPED_FILE && isMemmapSupported()) {
            this.storage = new MemoryMappedStore(path, this, context);
        }
        if (type == StoreType.FILE || (type == StoreType.MEMORY_MAPPED_FILE && !isMemmapSupported())) {
            this.storage = new FileChannelStore(path, this, context);
        } else if (type == StoreType.IN_MEMORY) {
            String storeId = String.valueOf(storeIdCounter.addAndGet(1));
            this.storage = new InMemoryStore(context, storeId);
        }

        // Create default maps
        int initialSize = 8;
        int mapByIdLocation = 53;
        int internalMapLocation = 98;

        if (storage.getFileSize() == initialSize) {
            mapByName = newSkipListMap();
            mapById = newSkipListMap();
            internalMaps = newSkipListMap();
        } else {
            mapByName = getSkipListMap((Header) storage.read(initialSize, Header.HEADER_SIZE, Header.class));
            mapById = getSkipListMap((Header) storage.read(mapByIdLocation, Header.HEADER_SIZE, Header.class));
            internalMaps = getSkipListMap((Header) storage.read(internalMapLocation, Header.HEADER_SIZE, Header.class));
        }

        if (this.storage != null)
            this.storage.init(this);

    }

    /**
     * Create a new Hash Set.  The underlying map is a Scalable Disk Map.  It sets the default load factor to 5
     * @return The map
     */
    public Map newHashSet() {
        int defaultLoadFactor = 3;
        return newHashSet(defaultLoadFactor);
    }

    /**
     * Create a new Hash Set.  The underlying map is a Scalable Disk Map
     * @param loadFactor Load factor for set
     * @return The map
     */
    public Map newHashSet(int loadFactor) {
        // Create a new header for the new structure we are creating
        final Header newHeader = new Header();
        newHeader.position = storage.allocate(Header.HEADER_SIZE);
        newHeader.firstNode = 0;
        storage.write(newHeader, newHeader.position); //Write the new header
        return new DefaultDiskSet(storage, newHeader, false, false);
    }

    /**
     * Get Disk Map with header reference
     *
     * @param header reference within storage
     * @return Instantiated disk structure
     * @since 1.0.0
     */
    public Map getDiskMap(Header header) {
        return mapsByHeader.compute(header, (header1, map) -> {
            if (map != null)
                return map;

            return newDiskMap(storage, header);
        });
    }

    /**
     * Get a load factor map with header
     *
     * @param header reference within storage
     *
     * @param loadFactor Value from 1-10.
     *
     * The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated Load factor map
     */
    @Override
    public Map getScalableMap(Header header, int loadFactor) {
        return mapsByHeader.compute(header, (header1, map) -> {
            if (map != null)
                return map;

            return newScalableMap(storage, header, loadFactor, true);
        });
    }

    /**
     * Get a skip list  map with header
     *
     * @param header reference within storage
     * @return Instantiated Skip List map
     */
    @Override
    public Map getSkipListMap(Header header) {
        return mapsByHeader.compute(header, (header1, map) -> {
            if (map != null)
                return map;

            return newSkipListMap(storage, header);
        });
    }


    /**
     * Get a default map.  This is only good for pre defined maps
     *
     * This was added in order to get around the initialization that the previous linked list performed
     * @param name Name of the map
     * @return The previously defined map
     * @since 1.2.0
     */
    public Map getDefaultMapByName(String name) {
        switch (name) {
            case FileChannelStore.SERIALIZERS_MAP_ID:
                return mapById;
            case FileChannelStore.SERIALIZERS_MAP_NAME:
                return mapByName;
            default:
                return internalMaps;
        }
    }

    /**
     * Default Map factory.  This creates or gets a map based on the name and puts it into a map
     *
     * @param name identifier of a map
     * @param type Type of map
     * @return Created map
     */
    private Map getMapWithType(String name, MapType type, int loadFactor) {

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

            Map retVal = null;
            switch (type) {
                case BITMAP:
                    retVal = newDiskMap(storage, header);
                    break;
                case SKIPLIST:
                    retVal = newSkipListMap(storage, header);
                    break;
                case LOAD:
                    retVal = newScalableMap(storage, header, loadFactor, true);
                    break;
            }
            return retVal;
        });
    }

    /**
     * Create a new Map reference header
     * @return the created header ripe for instantiating a new map
     */
    public Header newMapHeader()
    {
        Header header = new Header();
        header.position = storage.allocate(Header.HEADER_SIZE);
        storage.write(header, header.position);
        return header;
    }

    /**
     * Method get returns an instance of a Load Factor map.  This is a map you can tune to optimized for either speed
     * or storage
     *
     * @param name Instance Name
     * @return An instantiated or existing hash structure
     */
    @Override
    public Map getScalableMap(String name, int loadFactor) {
        return getMapWithType(name, MapType.LOAD, loadFactor);
    }

    /**
     * Method get returns an instance of a hashmap.  This is the fastest storage but, it is the heaviest on disk storage
     *
     * @param name Instance Name
     * @return An instantiated or existing hash structure
     */
    public Map getHashMap(String name) {
        return getMapWithType(name, MapType.BITMAP, 10);
    }

    /**
     * Get a map with a skip list index
     *
     * @param name Name of the map to uniquely identify it
     * @return Instantiated map with skip list
     */
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
     */
    private static boolean isMemmapSupported() {
        String prop = System.getProperty("os.arch");
        return prop != null && prop.contains("64");
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
    public Store getStore() {
        return storage;
    }

    /////////////////////////////////////////////////////////////////////////////
    //
    // Map instantiation
    //
    /////////////////////////////////////////////////////////////////////////////

    /**
     * Create a new Disk Map.  This uses the default Bitmap index
     *
     * @param store  File Storage
     * @param header Reference Node
     * @return Instantiated disk map
     */
    private DiskMap newDiskMap(Store store, Header header) {
        return new DefaultDiskMap(store, header);
    }

    /**
     * Create a new Disk Map.  This uses both a skip list and a Bitmap index
     *
     * @param store  File Storage
     * @param header Reference Node
     *
     * @param loadFactor Value from 1-10.
     *
     * The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated disk map
     */
    private DiskMap newScalableMap(Store store, Header header, int loadFactor, boolean enableCaching) {
//        if(loadFactor < 4)
//            return new LowLoadFactorMap(store, header, loadFactor);
        return new ScaledDiskMap(store, header, loadFactor, enableCaching);
    }

    /**
     * Create a new Disk Map.  This uses both a skip list
     *
     * @param store  File Storage
     * @param header Reference Node
     * @return Instantiated disk map
     */
    public DiskMap newSkipListMap(Store store, Header header) {
        return new DiskSkipList(store, header);
    }

    /**
     * Create a skip list map with header and
     *
     * @return New Map
     */
    private DiskMap newSkipListMap() {
        Header header = new Header();
        header.position = storage.allocate(Header.HEADER_SIZE);
        storage.write(header, header.position);
        return newSkipListMap(storage, header);
    }

}
