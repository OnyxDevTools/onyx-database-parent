package com.onyx.structure;

import com.onyx.structure.node.Header;
import com.onyx.structure.serializer.Serializers;
import com.onyx.structure.store.Store;

import java.util.Map;
import java.util.Set;

/**
 * Created by tosborn1 on 7/30/15.
 * <p>
 * This is the contract a map builder must obey.
 * This class is responsible for building all maps.  There is one map builder per file and or storage
 */
public interface MapBuilder {
    /**
     * Method get returns an instance of a hashmap
     *
     * @param name Name of the hashmap
     * @return Instantiated hashmap.  This defaults to a the Bitmap implementation
     */
    Map getHashMap(String name);

    /**
     * Get the instance of a map which uses a skip list index
     *
     * @param name Name of the map to uniquely identify it
     * @return Instantiated map with storage
     */
    @SuppressWarnings("unused")
    Map getSkipListMap(String name);

    /**
     * Get the instance of a map which uses a skip list index
     *
     * @param name Name of the map to uniquely identify it
     * @return Instantiated map with storage
     */
    @SuppressWarnings("unused")
    Map getLoadFactorMap(String name);

    /**
     * Method returns an instance of a hash set
     *
     * @param name Name of the hash set
     * @return DiskSet instance
     * @since 1.0.2
     */
    @SuppressWarnings("unused")
    Set getHashSet(String name);

    /**
     * Method returns an instance of a hash set
     *
     * @param header Reference of the hash set
     * @return HashSet instance
     * @since 1.0.2
     */
    @SuppressWarnings("unused")
    Set getHashSet(Header header);

    /**
     * Get Long Set.  This gets a disk set that can only support persisting long values
     *
     * @param setId The id of the set
     * @return Instantiated DiskSet
     */
    Set getLongSet(long setId);

    /**
     * Creates a long disk set
     *
     * @return Instantiated disk set
     */
    Set newLongSet();

    /**
     * Creates a long disk set only for long values
     *
     * @return Disk Set
     */
    Set newLongHashSet();

    /**
     * Get Disk Map with header reference
     *
     * @param header reference within storage
     * @return Instantiated disk structure
     */
    Map getDiskMap(Header header);

    /**
     * Get Disk Map with the ability to dynamically change the load factor.  Meaning change how it scales dynamically
     *
     * @param header reference within storage
     * @return Instantiated disk structure
     */
    @SuppressWarnings("unused")
    Map getLoadFactorMap(Header header);

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
     *
     * @return The Store used for all data structures
     */
    Store getStore();

    /**
     * Used by internal maps within database classes in order to setup metadata.  These must be pre-defined maps.
     * @param name Name of the map
     * @return The default map
     *
     * @since 1.2.0
     */
    Map getDefaultMapByName(String name);
}
