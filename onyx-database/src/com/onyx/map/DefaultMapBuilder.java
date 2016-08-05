package com.onyx.map;

import com.onyx.map.node.Header;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.Serializers;
import com.onyx.map.store.*;
import com.onyx.persistence.context.SchemaContext;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class DefaultMapBuilder implements MapBuilder
{

    protected Store storage = null;

    // Contains all initialized maps
    protected Map<String, Map> maps = new HashMap();

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
    public DefaultMapBuilder(String filePath, StoreType type)
    {
        this(filePath, type, null);
    }

    /**
     * Constructor
     *
     * @param filePath
     */
    public DefaultMapBuilder(String filePath, StoreType type, SchemaContext context)
    {
        this("", filePath, type, context);
    }

    /**
     * Constructor
     *
     * @param filePath
     */
    public DefaultMapBuilder(String fileSystemPath, String filePath, StoreType type, SchemaContext context)
    {
        String path = null;

        if(fileSystemPath == null || fileSystemPath.equals(""))
            path = filePath;
        else
            path = fileSystemPath + File.separator + filePath;

        if(type == StoreType.FILE || !isMemmapSupported())
        {
            this.storage = new FileChannelStore(path, this, context);
        }
        else if(type == StoreType.MEMORY_MAPPED_FILE)
        {
            this.storage = new MemoryMappedStore(path, this, context);
        }
        else if(type == StoreType.IN_MEMORY)
        {
            this.storage = new InMemoryStore(this, context);
        }

        if(this.storage != null)
            this.storage.init();
    }

    /**
     * Method get returns an instance of a hashmap
     *
     * @param name
     * @return
     */
    public synchronized Map getHashMap(String name) {
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
                        // We found a match, store it in the map cache and return it
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

        // Create a new header for the new map we are creating
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

        // Create a new disk map and return it
        final DiskMap retVal = newDiskMap(storage, newHeader);
        maps.put(name, retVal);
        return retVal;
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
        if(store instanceof InMemoryStore)
            return new DefaultDiskMap(store, header, true);
        else
            return new DefaultDiskMap(store, header);
    }
}
