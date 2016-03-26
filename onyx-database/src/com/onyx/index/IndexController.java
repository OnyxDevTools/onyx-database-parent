package com.onyx.index;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.EntityException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/10/15.
 */
public interface IndexController
{
    /**
     * Save an index value with the record reference
     *
     * @param indexValue
     * @param oldReferenceId
     * @param newReferenceId
     * @throws EntityException
     */
    void save(Object indexValue, long oldReferenceId, long newReferenceId) throws EntityException;

    /**
     * Delete an index value with a record reference
     *
     * @param reference
     * @throws EntityException
     */
    void delete(long reference) throws EntityException;

    /**
     * Find all index references
     *
     * @param indexValue
     * @return
     * @throws EntityException
     */
    Set<Long> findAll(Object indexValue) throws EntityException;

    /**
     * Get Index descriptor
     *
     * @return
     */
    IndexDescriptor getIndexDescriptor();

    /**
     * Find all index references
     *
     * @return
     * @throws EntityException
     */
    Set<Object> findAllValues() throws EntityException;


    /**
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     * @throws EntityException
     */
    void rebuild() throws EntityException;

}
