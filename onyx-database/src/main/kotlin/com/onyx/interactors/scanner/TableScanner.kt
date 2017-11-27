package com.onyx.interactors.scanner

import com.onyx.exception.OnyxException
import com.onyx.interactors.record.data.Reference

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
    fun scan(): MutableMap<Reference, Reference>

    /**
     * Scan with indexes
     *
     * @param existingValues Existing references to start from
     * @return Matching references meeting criteria
     * @throws OnyxException Cannot scan entity
     */
    @Throws(OnyxException::class)
    fun  scan(existingValues: MutableMap<Reference, Reference>): MutableMap<Reference, Reference>

}
