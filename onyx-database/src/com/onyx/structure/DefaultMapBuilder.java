package com.onyx.structure;

import com.onyx.structure.base.DiskSkipList;
import com.onyx.structure.base.LoadFactorMap;
import com.onyx.structure.node.Header;
import com.onyx.structure.serializer.Serializers;
import com.onyx.structure.store.*;
import com.onyx.persistence.context.SchemaContext;

import java.io.File;
import java.util.*;
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
    private Map<String, Set> sets = Collections.synchronizedMap(new WeakHashMap());

    // Contains all initialized maps
    private Map<Header, Set> setsByHeader = Collections.synchronizedMap(new WeakHashMap());

    // Contains all initialized maps
    private Map<Long, Set> setsById = Collections.synchronizedMap(new WeakHashMap());

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
            this.storage = new InMemoryStore(this, context, storeId);
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
            this.storage.init();

    }

    /**
     * Method returns an instance of a hash set
     *
     * @param name Name of the hash set
     * @return HashSet instance
     * @since 1.0.2
     */
    public Set getHashSet(String name) {
        return sets.compute(name, (s, set) -> {
            if (set != null)
                return set;
            final DiskMap underlyingHashMap = (DiskMap) getSkipListMap(name + "$Map");
            return newDiskSet(underlyingHashMap);
        });
    }

    /**
     * Method returns an instance of a hash set
     *
     * @param header Reference of the hash set
     * @return HashSet instance
     * @since 1.0.2
     */
    public Set getHashSet(Header header) {
        return setsByHeader.compute(header, (header1, set) -> {
            if (set != null)
                return set;

            final DiskMap underlyingDiskMap = (DiskMap) getSkipListMap(header1);
            return newDiskSet(underlyingDiskMap);
        });
    }

    /**
     * Method returns an instance of a long set
     *
     * @param setId Reference of the hash set
     * @return HashSet instance
     * @since 1.0.2
     */
    public Set getLongSet(long setId) {
        return setsById.computeIfAbsent(setId, aLong -> {
            final Header setHeader = (Header) storage.read(setId, Header.HEADER_SIZE, Header.class);
            return new LongDiskSet(storage, setHeader);
        });
    }

    /**
     * Creates a new long set
     *
     * @return New long set
     */
    @Override
    public Set newLongSet() {

        // Create a new header for the new structure we are creating
        final Header newHeader = new Header();
        newHeader.position = storage.allocate(Header.HEADER_SIZE);
        newHeader.firstNode = 0;
        storage.write(newHeader, newHeader.position); //Write the new header

        final LongDiskSet diskSet = new LongDiskSet(storage, newHeader);
        setsById.put(newHeader.position, diskSet);
        return diskSet;
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
     * @return Instantiated Load factor map
     */
    @Override
    public Map getLoadFactorMap(Header header) {
        return mapsByHeader.compute(header, (header1, map) -> {
            if (map != null)
                return map;

            return newLoadFactorMap(storage, header);
        });
    }

    /**
     * Get Disk Map with header reference
     *
     * @param header reference within storage
     * @return Instantiated disk structure
     * @since 1.0.0
     */
    public Map getSkipListMap(Header header) {
        return mapsByHeader.compute(header, (header1, map) -> {
            if (map != null)
                return map;

            return newSkipListMap(storage, header);
        });
    }

    /**
     * Instantiate the disk set with an underlying disk structure
     *
     * @param underlyingDiskMap The thing that drives the hashing and the storge of the data
     * @return Instantiated Disk Set instance
     * @since 1.0.2
     */
    private Set newDiskSet(DiskMap underlyingDiskMap) {
        return new DefaultDiskSet<>(underlyingDiskMap, underlyingDiskMap.getReference());
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
    private Map getMapWithType(String name, MapType type) {

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
//            Map retVal = newLoadFactorMap(storage, header);
//            /* zzz
            switch (type) {
                case BITMAP:
                    retVal = newDiskMap(storage, header);
                    break;
                case SKIPLIST:
                    retVal = newSkipListMap(storage, header);
                    break;
                case LOAD:
                    retVal = newLoadFactorMap(storage, header);
                    break;
            }
//            */
            return retVal;
        });
    }

    /**
     * Method get returns an instance of a Load Factor map.  This is a map you can tune to optimized for either speed
     * or storage
     *
     * @param name Instance Name
     * @return An instantiated or existing hash structure
     */
    @Override
    public Map getLoadFactorMap(String name) {
        return getMapWithType(name, MapType.LOAD);
    }

    /**
     * Method get returns an instance of a hashmap.  This is the fastest storage but, it is the heaviest on disk storage
     *
     * @param name Instance Name
     * @return An instantiated or existing hash structure
     */
    public Map getHashMap(String name) {
        return getMapWithType(name, MapType.BITMAP);
    }

    /**
     * Get a map with a skip list index
     *
     * @param name Name of the map to uniquely identify it
     * @return Instantiated map with skip list
     */
    public Map getSkipListMap(String name) {
        return getMapWithType(name, MapType.SKIPLIST);
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
     * @return Instantiated disk map
     */
    private DiskMap newLoadFactorMap(Store store, Header header) {
        return new LoadFactorMap(store, header);
    }

    /**
     * Create a new Disk Map.  This uses both a skip list
     *
     * @param store  File Storage
     * @param header Reference Node
     * @return Instantiated disk map
     */
    private DiskMap newSkipListMap(Store store, Header header) {
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

    /**
     * Create a new Hash Set of longs
     *
     * @return Instantiated hash set
     */
    public Set newLongHashSet() {
        // Create a new header for the new structure we are creating
        final Header newHeader = new Header();
        newHeader.position = storage.allocate(Header.HEADER_SIZE);
        storage.write(newHeader, newHeader.position); //Write the new header
        return new LongDiskSet(storage, newHeader);
    }

}
