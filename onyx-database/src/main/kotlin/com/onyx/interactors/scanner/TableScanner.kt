package com.onyx.interactors.scanner

import com.onyx.exception.OnyxException
import com.onyx.scan.PartitionReference

/**
 * Created by timothy.osborn on 1/6/15.
 *
 * Contact used to scan entities
 */
interface TableScanner {

    /**
     * Full scan
     *
     * @return Matching references meeting criteria
     * @throws OnyxException Cannot scan entity
     */
    @Throws(OnyxException::class)
    fun scan(): Map<PartitionReference, PartitionReference>

    /**
     * Scan with indexes
     *
     * @param existingValues Existing references to start from
     * @return Matching references meeting criteria
     * @throws OnyxException Cannot scan entity
     */
    @Throws(OnyxException::class)
    fun scan(existingValues: Map<PartitionReference, PartitionReference>): Map<PartitionReference, PartitionReference>

}
