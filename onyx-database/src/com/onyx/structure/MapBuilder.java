package com.onyx.structure;

import com.onyx.structure.node.Header;
import com.onyx.structure.serializer.Serializers;
import com.onyx.structure.store.Store;

import java.util.Map;
import java.util.Set;

/**
 * Created by tosborn1 on 7/30/15.
 */
public interface MapBuilder
{
    /**
     * Method get returns an instance of a hashmap
     *
     * @param name Name of the hashmap
     * @return Instantiated hashmap.  This defaults to a the Bitmap implementation
     */
    Map getHashMap(String name);

    /**
     * Get the instance of a map which uses a skip list index
     * @param name Name of the map to uniquely identify it
     * @return Instantiated map with storage
     */
    @SuppressWarnings("unused")
    Map getSkipListMap(String name);

    /**
     * Method returns an instance of a hash set
     * @param name Name of the hash set
     * @since 1.0.2
     * @return DiskSet instance
     */
    @SuppressWarnings("unused")
    Set getHashSet(String name);

    /**
     * Method returns an instance of a hash set
     * @param header Reference of the hash set
     * @since 1.0.2
     * @return HashSet instance
     */
    @SuppressWarnings("unused")
    Set getHashSet(Header header);

    /**
     * Get Long Set.  This gets a disk set that can only support persisting long values
     * @param setId The id of the set
     * @return Instantiated DiskSet
     */
    Set getLongSet(long setId);

    /**
     * Creates a long disk set
     * @return Instantiated disk set
     */
    Set newLongSet();

    /**
     * Creates a long disk set only for long values
     * @return Disk Set
     */
    Set newLongHashSet();

    /**
     * Instantiates and creates a new HashSet
     * @return Instantiated hash set
     * @since 1.0.2
     */
    @SuppressWarnings("unused")
    Set newHashSet();

    /**
     * Get Disk Map with header reference
     * @param header reference within storage
     * @return Instantiated disk structure
     */
    Map getDiskMap(Header header);

    /**
     * Only update the first position for a header
     *
     * @param header Header reference of the map
     * @param next Next data structure in the linked list
     */
    void updateHeaderNext(Header header, long next);

    /**
     * Close Map Builder.  Flush the file writes
     */
    void close();

    /**
     * Commit Map Builder file synchronize file writes
     */
    void commit();

    /**
     * Delete file
     */
    void delete();

    /**
     * Getter for serializers
     *
     * @return Custom serializes shared within the data structures
     */
     Serializers getSerializers();

    /**
     * Get the underlying storage mechanism
     * @return The Store used for all data structures
     */
     Store getStore();
}
