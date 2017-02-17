package com.onyx.structure;

import com.onyx.structure.node.Header;
import com.onyx.structure.serializer.Serializers;
import com.onyx.structure.store.Store;

import java.util.Map;

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
     * @return Instantiated hashmape.  This defaults to a the Bitmap implementation
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
     *
     * @param loadFactor Value from 1-10.
     *
     * The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated map with storage
     */
    @SuppressWarnings("unused")
    Map getScalableMap(String name, int loadFactor);

    /**
     * Creates a long disk set only for long values
     *
     * @return Disk Set
     */
    Map newHashSet();

    /**
     * Create a new Hash Set.  The underlying map is a Scalable Disk Map
     * @param loadFactor Load factor for set
     * @return Disk Set
     */
    Map newHashSet(int loadFactor);

    /**
     * Get the instance of a map which uses a skip list index
     *
     * @param name Name of the map to uniquely identify it
     * @return Instantiated map with storage
     */
    @SuppressWarnings("unused")
    Map getSkipListMap(Header name);

    /**
     * Get Disk Map with the ability to dynamically change the load factor.  Meaning change how it scales dynamically
     *
     * @param header reference within storage
     *
     *
     * @param loadFactor Value from 1-10.
     *
     * The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated disk structure
     */
    @SuppressWarnings("unused")
    Map getScalableMap(Header header, int loadFactor);

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

    /**
     * Create a new Map reference header
     * @return the created header ripe for instantiating a new map
     */
    Header newMapHeader();

}
