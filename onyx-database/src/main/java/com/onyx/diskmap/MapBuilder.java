package com.onyx.diskmap;

import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.serializer.Serializers;

import java.util.Map;

/**
 * Created by tosborn1 on 7/30/15.
 * <p>
 * This is the contract a map builder must obey.
 * This class is responsible for building all maps.  There is one map builder per file and or storage
 *
 * This contract has been consoladated in v1.2.0.  The different map implementations has been reduced only to use
 * the best rather than having an option.  The DefaultMapBuilder will decide which map impmentation is better
 * based on the loadFactor.
 *
 * @since 1.0.0
 */
public interface MapBuilder {

    /**
     * Get the instance of a map.  Based on the loadFactor it may be a multi map with a hash index followed by a skip list
     * or a multi map with a hash matrix followed by a skip list.
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
     * @since 1.1.0
     *
     * Note, this was changed to use what was being referred to as a DefaultDiskMap which was a parent of AbstractBitmap.
     * It is now an implemenation of an inter changable index followed by a skip list.
     *
     */
    Map getHashMap(String name, int loadFactor);

    /**
     * Get Hash Map by Name.  This will default the map with a loadFactor of 10.  In that case, it will return an
     * instance of the hash matrix followed by a skip list.
     *
     * @param name Unique Map name
     * @return Disk Map implementation
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    Map getHashMap(String name);

    /**
     * Get Skip list map.  This will return a map with a bare bones skip list index.  This usually is for unit
     * testing purposes.  It is not recommended since it is not as scalable as the multi index maps.
     *
     * @param name Skip list map name
     * @return Implementation of a disk map using a skip list index
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    Map getSkipListMap(String name);

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
     *
     * @since 1.0.0
     */
    Map getHashMap(Header header, int loadFactor);

    /**
     * Create a hash map with a given header.  This should not be invoked unless it is used to grab a stateless
     * instance of a disk map.  Stateless meaning, the header has already been setup.  Note, this is not thread safe
     * If you were to invoke this with the same header and use the maps concurrently you WILL corrupt your data.
     * You must use alternative means of thread safety.
     *
     * Since this implementation is stateless, it does not provide caching nor thread safety.
     *
     * @param header Head of the disk map
     * @param loadFactor Load factor in which the map was instantiated with.
     * @return Stateless instance of a disk map
     *
     * @since 1.2.0
     */
    DiskMap newHashMap(Header header, @SuppressWarnings("SameParameterValue") int loadFactor);

    /**
     * Close Map Builder.  Flush the file writes
     * @since 1.0.0
     */
    void close();

    /**
     * Commit Map Builder file synchronize file writes
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    void commit();

    /**
     * Delete file
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    void delete();

    /**
     * Getter for serializers
     *
     * @return Custom serializes shared within the data structures
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    Serializers getSerializers();

    /**
     * Create a new Map reference header
     * @return the created header ripe for instantiating a new map
     *
     * @since 1.2.0
     */
    Header newMapHeader();

    /**
     * Clear storage and rest it to its original state.
     * This is used to recycle and reuse.
     *
     * @since 1.3.0
     */
    void reset();
}
