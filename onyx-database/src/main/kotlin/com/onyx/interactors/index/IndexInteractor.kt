package com.onyx.interactors.index

import com.onyx.descriptor.IndexDescriptor
import com.onyx.exception.OnyxException

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Contract on how an index interacts
 */
interface IndexInteractor {

    /**
     * Save an index key with the record reference
     *
     * @param indexValue Index value to save
     * @param oldReferenceId Old entity reference for the index
     * @param newReferenceId New entity reference for the index
     */
    @Throws(OnyxException::class)
    fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long)

    /**
     * Delete an index key with a record reference
     *
     * @param reference Entity reference
     */
    @Throws(OnyxException::class)
    fun delete(reference: Long)

    /**
     * Find all index references
     *
     * @param indexValue Index value to find values for
     * @return References matching that index value
     */
    @Throws(OnyxException::class)
    fun findAll(indexValue: Any): Map<Long, *>

    /**
     * Find all the references above and perhaps equal to the key parameter
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare above and equal or not.
     * @return A set of record references
     *
     * @throws OnyxException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    fun findAllAbove(indexValue: Any, includeValue: Boolean): Set<Long>

    /**
     * Find all the references blow and perhaps equal to the key parameter
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare below and equal or not.
     * @return A set of record references
     *
     * @throws OnyxException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    fun findAllBelow(indexValue: Any, includeValue: Boolean): Set<Long>

    /**
     * Get Index descriptor
     *
     * @return Index descriptor for entity
     */
    val indexDescriptor: IndexDescriptor

    /**
     * Find all index references
     *
     * @return All index references
     */
    @Throws(OnyxException::class)
    fun findAllValues(): Set<Any>

    /**
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     */
    @Throws(OnyxException::class)
    fun rebuild()

}
