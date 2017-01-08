package com.onyx.structure;

import com.onyx.structure.base.DiskSkipList;
import com.onyx.structure.node.Header;
import com.onyx.structure.node.SetHeader;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.Serializers;
import com.onyx.structure.store.*;
import com.onyx.persistence.context.SchemaContext;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class DefaultMapBuilder implements MapBuilder
{
    protected static final Map<String, MapBuilder> mapBuilderByPaths = new HashMap();

    protected Store storage = null;

    // Contains all initialized maps
    protected Map<String, Map> maps = new WeakHashMap<>();

    // Contains all initialized maps
    protected Map<String, Set> sets = new WeakHashMap();

    // Contains all initialized maps
    protected Map<Header, Set> setsByHeader = new WeakHashMap();

    // Contains all initialized maps
    protected Map<Long, Set> setsById = Collections.synchronizedMap(new WeakHashMap());

    // Contains all initialized maps
    protected Map<Header, Map> mapsByHeader = new WeakHashMap();

    /**
     * Constructor
     *
     * @param filePath
     */
    public DefaultMapBuilder(String filePath)
    {
        this(filePath, StoreType.MEMORY_MAPPED_FILE, null);
    }

    /**
     * Constructor
     *
     * @param filePath
     */
    public DefaultMapBuilder(String filePath, SchemaContext context)
    {
        this(filePath, StoreType.MEMORY_MAPPED_FILE, context);
    }


    /**
     * Constructor
     *
     * @param filePath
     */
    @SuppressWarnings("unused")
    public DefaultMapBuilder(String filePath, StoreType type)
    {
        this(filePath, type, null);
    }

    /**
     * Constructor for a database
     *
     * @param filePath File path to hold the disk structure data
     * @param type Storage type
     * @param context Database Schema context
     * @since 1.0.0
     */
    public DefaultMapBuilder(String filePath, StoreType type, SchemaContext context)
    {
        this("", filePath, type, context);
    }

    /**
     * Constructor
     *
     * @param filePath File path to hold the disk structure data
     * @since 1.0.0
     */
    public DefaultMapBuilder(String fileSystemPath, String filePath, StoreType type, SchemaContext context)
    {
        String path = "";

        if(fileSystemPath == null || fileSystemPath.equals(""))
            path = filePath;
        else
            path = fileSystemPath + File.separator + filePath;

        mapBuilderByPaths.put(path, this);

        if(type == StoreType.MEMORY_MAPPED_FILE && isMemmapSupported())
        {
            this.storage = new MemoryMappedStore(path, this, context);
        }
        if(type == StoreType.FILE || (type == StoreType.MEMORY_MAPPED_FILE && !isMemmapSupported()))
        {
            this.storage = new FileChannelStore(path, this, context);
        }
        else if(type == StoreType.IN_MEMORY)
        {
            String storeId = String.valueOf(storeIdCounter.addAndGet(1));
            this.storage = new InMemoryStore(this, context, storeId);
            mapBuilderByPaths.put(storeId, this);
        }

        if(this.storage != null)
            this.storage.init();
    }

    static AtomicInteger storeIdCounter = new AtomicInteger(0);

    /**
     * Method returns an instance of a hash set
     * @param name Name of the hash set
     * @since 1.0.2
     * @return HashSet instance
     */
    public synchronized Set getHashSet(String name) {

        // If it is already initialized, return it
        if (sets.containsKey(name)) {
            return sets.get(name);
        }

        final DiskMap underlyingHashMap = (DiskMap)getHashMap(name + "$Map");
        return newDiskSet(underlyingHashMap);
    }

    /**
     * Method returns an instance of a hash set
     * @param header Reference of the hash set
     * @since 1.0.2
     * @return HashSet instance
     */
    public Set getHashSet(Header header) {

        return setsByHeader.compute(header, (header1, set) -> {
            if(set != null)
                return set;

            final DiskMap underlyingDiskMap = (DiskMap)getDiskMap(header1);
            return newDiskSet(underlyingDiskMap);
        });
    }

    @Override
    public Set getLongSet(long setId) {
        return setsById.computeIfAbsent(setId, aLong -> {
            SetHeader setHeader = (SetHeader)storage.read(setId, SetHeader.SET_HEADER_SIZE, SetHeader.class);
            final LongDiskSet diskSet = new LongDiskSet(storage, setHeader);
            return diskSet;
        });
    }

    @Override
    public Set newLongSet() {

        // Create a new header for the new structure we are creating
        SetHeader newHeader = new SetHeader();
        newHeader.position = storage.allocate(SetHeader.SET_HEADER_SIZE);
        newHeader.firstNode = 0;
        storage.write(newHeader, newHeader.position); //Write the new header

        final LongDiskSet diskSet = new LongDiskSet(storage, newHeader);
        setsById.put(newHeader.position, diskSet);
        return diskSet;
    }

    /**
     * Get Disk Map with header reference
     * @param header reference within storage
     * @return Instantiated disk structure
     * @since 1.0.0
     */
    public Map getDiskMap(Header header)
    {
        return mapsByHeader.compute(header, (header1, map) -> {
            if(map != null)
                return map;

            return newDiskMap(storage, header);
        });
    }
    /**
     * Instantiate the disk set with an underlying disk structure
     * @param underlyingDiskMap The thing that drives the hashing and the storge of the data
     * @return Instantiated Disk Set instance
     * @since 1.0.2
     */
    protected Set newDiskSet(DiskMap underlyingDiskMap)
    {
        return new DefaultDiskSet<>(underlyingDiskMap, underlyingDiskMap.getReference());
    }

    public synchronized Map getSkipListMap(String name)
    {
        // If it is already initialized, return it
        if (maps.containsKey(name)) {
            return maps.get(name);
        }

        // Get the first header.  All header records are stored in a linked list.  They cannot be deleted.
        // Starts at eight becuase the first 8 bytes contains the file size
        Header header = (Header) storage.read(8, Header.HEADER_SIZE, Header.class);

        // If there is no record, lets instantiate one
        if (header != null) {
            while (true) {
                // IF there is an id, continue
                if (header.idPosition > 0) {
                    // Get the id, which is a string
                    String targetName = (String) storage.read(header.idPosition, header.idSize, String.class);
                    if (targetName != null && targetName.equals(name)) {
                        // We found a match, store it in the structure cache and return it
                        final DiskSkipList retVal = newSkipListMap(storage, header);
                        maps.put(name, retVal);
                        return retVal;
                    }

                    // If there is a next header, read it and continue
                    if (header.next > 0) {
                        header = (Header) storage.read(header.next, Header.HEADER_SIZE, Header.class);
                    } else {
                        break;
                    }
                }
            }
        }
        // Buffer for the id
        final ObjectBuffer buffer = new ObjectBuffer(storage.getSerializers());
        try {
            buffer.writeObject(name);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create a new header for the new structure we are creating
        Header newHeader = new Header();
        newHeader.position = storage.allocate(Header.HEADER_SIZE);
        newHeader.idSize = buffer.getSize();
        newHeader.idPosition = storage.allocate(newHeader.idSize);

        // There is a parent record
        if (header != null) {
            // Set the next record
            updateHeaderNext(header, newHeader.position);
        }

        // Write the id
        storage.write(buffer, newHeader.idPosition);
        storage.write(newHeader, newHeader.position); //Write the new header

        // Create a new disk structure and return it
        final Map retVal = newSkipListMap(storage, newHeader);
        maps.put(name, retVal);
        return retVal;
    }

    /**
     * Method get returns an instance of a hashmap
     *
     * @param name Instance Name
     * @return An instantiated or existing hash structure
     */
    public synchronized Map getHashMap(String name) {
//zzz        return getSkipListMap(name);
//        /*
        // If it is already initialized, return it
        if (maps.containsKey(name)) {
            return maps.get(name);
        }

        // Get the first header.  All header records are stored in a linked list.  They cannot be deleted.
        // Starts at eight becuase the first 8 bytes contains the file size
        Header header = (Header) storage.read(8, Header.HEADER_SIZE, Header.class);

        // If there is no record, lets instantiate one
        if (header != null) {
            while (true) {
                // IF there is an id, continue
                if (header.idPosition > 0) {
                    // Get the id, which is a string
                    String targetName = (String) storage.read(header.idPosition, header.idSize, String.class);
                    if (targetName != null && targetName.equals(name)) {
                        // We found a match, store it in the structure cache and return it
                        final DiskMap retVal = newDiskMap(storage, header);
                        maps.put(name, retVal);
                        return retVal;
                    }

                    // If there is a next header, read it and continue
                    if (header.next > 0) {
                        header = (Header) storage.read(header.next, Header.HEADER_SIZE, Header.class);
                    } else {
                        break;
                    }
                }
            }
        }
        // Buffer for the id
        final ObjectBuffer buffer = new ObjectBuffer(storage.getSerializers());
        try {
            buffer.writeObject(name);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create a new header for the new structure we are creating
        Header newHeader = new Header();
        newHeader.position = storage.allocate(Header.HEADER_SIZE);
        newHeader.idSize = buffer.getSize();
        newHeader.idPosition = storage.allocate(newHeader.idSize);

        // There is a parent record
        if (header != null) {
            // Set the next record
            updateHeaderNext(header, newHeader.position);
        }

        // Write the id
        storage.write(buffer, newHeader.idPosition);
        storage.write(newHeader, newHeader.position); //Write the new header

        // Create a new disk structure and return it
        final DiskMap retVal = newDiskMap(storage, newHeader);
        maps.put(name, retVal);
        return retVal;
        //*/
    }

    /**
     * Only update the first position for a header
     *
     * @param header
     * @param next
     */
    public void updateHeaderNext(Header header, long next)
    {
        header.next = next;
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(next);
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, storage.getSerializers());
        storage.write(objectBuffer, header.position + Long.BYTES + Long.BYTES);
    }

    public void close()
    {
        storage.close();
    }

    public void commit()
    {
        storage.commit();
    }

    /**
     * Check if large files can be mapped into memory.
     * For example 32bit JVM can only address 2GB and large files can not be mapped,
     * so for 32bit JVM this function returns false.
     *
     */
    protected static boolean isMemmapSupported() {
        String prop = System.getProperty("os.arch");
        if(prop!=null && prop.contains("64")) return true;
        return false;
    }

    /**
     * Delete file
     */
    public void delete()
    {
        this.close();
        this.storage.delete();
    }

    /**
     * Getter for serializers
     *
     * @return
     */
    public Serializers getSerializers()
    {
        return this.storage.getSerializers();
    }

    protected DiskMap newDiskMap(Store store, Header header)
    {
//zzz        return new DiskSkipList<>(store, header);
///*
        DefaultDiskMap newDiskMap = null;
        if(store instanceof InMemoryStore) {
            newDiskMap = new DefaultDiskMap(store, header, true);
        }
        else {
            newDiskMap = new DefaultDiskMap(store, header);
        }

        return newDiskMap;
        //*/
    }

    protected DiskSkipList newSkipListMap(Store store, Header header)
    {
        return new DiskSkipList(store, header);
    }

    public synchronized Set newHashSet()
    {
        // Create a new header for the new structure we are creating
        Header newHeader = new Header();
        newHeader.position = storage.allocate(Header.HEADER_SIZE);
        newHeader.idSize = 0;
        storage.write(newHeader, newHeader.position); //Write the new header

        // Create a new disk structure and return it
        final DiskMap underlyingDiskMap = newDiskMap(storage, newHeader);
        Set set = newDiskSet(underlyingDiskMap);
        setsByHeader.put(newHeader, set);
        return set;
    }

    public synchronized Set newLongHashSet()
    {
        // Create a new header for the new structure we are creating
        Header newHeader = new Header();
        newHeader.position = storage.allocate(Header.HEADER_SIZE);
        newHeader.idSize = 0;
        storage.write(newHeader, newHeader.position); //Write the new header

        final LongDiskSet diskSet = new LongDiskSet(storage, newHeader);
        return diskSet;
    }


    /**
     * Returns the structure builder used for the specific file path.  There can only be a single structure builder per file.
     * @param path File path for the Map Builder's storage
     * @return MapBuilder registered for that path.  Null if it does not exist.
     */
    public static final MapBuilder getMapBuilder(String path)
    {
        return mapBuilderByPaths.get(path);
    }


    public Store getStore()
    {
        return storage;
    }
}
