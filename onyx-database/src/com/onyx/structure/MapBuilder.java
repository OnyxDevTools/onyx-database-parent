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
     * @param name
     * @return
     */
    Map getHashMap(String name);

    /**
     * Method returns an instance of a hash set
     * @param name Name of the hash set
     * @since 1.0.2
     * @return DiskSet instance
     */
    Set getHashSet(String name);

    /**
     * Method returns an instance of a hash set
     * @param header Reference of the hash set
     * @since 1.0.2
     * @return HashSet instance
     */
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
     * Instantiates and creates a new HashSet
     * @return Instantiated hash set
     * @since 1.0.2
     */
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
     * @param header
     * @param next
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
     * @return
     */
     Serializers getSerializers();

    Store getStore();
}
