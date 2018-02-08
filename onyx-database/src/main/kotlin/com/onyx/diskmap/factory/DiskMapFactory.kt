package com.onyx.diskmap.factory

import com.onyx.diskmap.data.Header

/**
 * Created by Tim Osborn on 7/30/15.
 *
 *
 * This is the contract a map factory must obey.
 * This class is responsible for building all maps.  There is one map factory per file and or storage
 *
 * This contract has been consolidated in v1.2.0.  The different map implementations has been reduced only to use
 * the best rather than having an option.  The DefaultDiskMapFactory will decide which map implementation is better
 * based on the loadFactor.
 *
 * @since 1.0.0
 */
interface DiskMapFactory {

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
     * It is now an implementation of an inter changeable index followed by a skip list.
     */
    fun <T : Map<*,*>> getHashMap(keyType:Class<*>, name: String, loadFactor: Int): T

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
    fun <T : Map<*,*>> getHashMap(keyType:Class<*>, header: Header, loadFactor: Int): T

    /**
     * Get Hash Map by Name.  This will default the map with a loadFactor of 10.  In that case, it will return an
     * instance of the hash matrix followed by a skip list.
     *
     * @param name Unique Map name
     * @return Disk Map implementation
     * @since 1.2.0
     */
    fun <T : Map<*,*>> getHashMap(keyType:Class<*>, name: String): T = getHashMap(keyType, name, 10)

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
    fun <T : Map<*,*>> newHashMap(keyType:Class<*>, header: Header, loadFactor: Int): T

    /**
     * Get the default index load factor.  This will check to see if the version of database supports a newer default
     * load factor vs maintaining the existing load factor for previous versions.  That was a default of one
     * whereas the new is 1
     *
     * @since 2.3.1
     */
    fun getDefaultLoadFactor():Int

    /**
     * Close Map Builder.  Flush the file writes
     * @since 1.0.0
     */
    fun close():Boolean

    /**
     * Commit Map Builder file synchronize file writes
     * @since 1.0.0
     */
    fun commit()

    /**
     * Delete file
     * @since 1.0.0
     */
    fun delete()

    /**
     * Create a new Map reference header
     * @return the created header ripe for instantiating a new map
     *
     * @since 1.2.0
     */
    fun newMapHeader(): Header

    /**
     * Clear storage and rest it to its original state.
     * This is used to recycle and reuse.
     *
     * @since 1.3.0
     */
    fun reset()
}
