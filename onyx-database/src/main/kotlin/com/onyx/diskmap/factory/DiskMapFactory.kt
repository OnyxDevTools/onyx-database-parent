package com.onyx.diskmap.factory

import com.onyx.diskmap.data.Header

/**
 * Created by Tim Osborn on 7/30/15.
 *
 *
 * This is the contract a map factory must obey.
 * This class is responsible for building all maps.  There is one map factory per file and or storage
 *
 * @since 1.0.0
 */
interface DiskMapFactory {

    /**
     * Get the instance of a map.
     *
     * @param name Name of the map to uniquely identify it
     *
     * @return Instantiated map with storage
     * @since 1.1.0
     *
     * Note, this was changed to use what was being referred to as a DefaultDiskMap which was a parent of AbstractBitmap.
     * It is now an implementation of an inter changeable index followed by a skip list.
     */
    fun <T : Map<*,*>> getHashMap(keyType:Class<*>, name: String): T

    /**
     * Get Disk Map with the ability to dynamically change the load factor.  Meaning change how it scales dynamically
     *
     * @param header reference within storage
     * @return Instantiated disk structure
     *
     * @since 1.0.0
     */
    fun <T : Map<*,*>> getHashMap(keyType:Class<*>, header: Header): T

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
