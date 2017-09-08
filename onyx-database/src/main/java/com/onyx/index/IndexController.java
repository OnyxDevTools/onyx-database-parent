package com.onyx.index;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.OnyxException;

import java.util.Map;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Contract on how an index interacts
 */
public interface IndexController
{
    /**
     * Save an index key with the record reference
     *
     * @param indexValue Index value to save
     * @param oldReferenceId Old entity reference for the index
     * @param newReferenceId New entity reference for the index
     */
    void save(Object indexValue, long oldReferenceId, long newReferenceId) throws OnyxException;

    /**
     * Delete an index key with a record reference
     *
     * @param reference Entity reference
     */
    @SuppressWarnings("RedundantThrows")
    void delete(long reference) throws OnyxException;

    /**
     * Find all index references
     *
     * @param indexValue Index value to find values for
     * @return References matching that index value
     */
    @SuppressWarnings("RedundantThrows")
    Map findAll(Object indexValue) throws OnyxException;

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
    @SuppressWarnings("RedundantThrows")
    Set<Long> findAllAbove(Object indexValue, boolean includeValue) throws OnyxException;

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
    @SuppressWarnings("RedundantThrows")
    Set<Long> findAllBelow(Object indexValue, boolean includeValue) throws OnyxException;

    /**
     * Get Index descriptor
     *
     * @return Index descriptor for entity
     */
    IndexDescriptor getIndexDescriptor();

    /**
     * Find all index references
     *
     * @return All index references
     */
    @SuppressWarnings("RedundantThrows")
    Set<Object> findAllValues() throws OnyxException;

    /**
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     */
    @SuppressWarnings("RedundantThrows")
    void rebuild() throws OnyxException;

}
