package com.onyx.interactors.scanner

import com.onyx.exception.OnyxException
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.record.data.Reference

/**
 * Created by timothy.osborn on 1/6/15.
 *
 * Contact used to scan entities
 */
interface TableScanner {

    var isLast:Boolean
    var collector: QueryCollector<Any>?

    /**
     * Full scan
     *
     * @return Matching references meeting criteria
     * @throws OnyxException Cannot scan entity
     */
    @Throws(OnyxException::class)
    fun scan(): MutableSet<Reference>

    /**
     * Scan with indexes
     *
     * @param existingValues Existing references to start from
     * @return Matching references meeting criteria
     * @throws OnyxException Cannot scan entity
     */
    @Throws(OnyxException::class)
    fun  scan(existingValues: Set<Reference>): MutableSet<Reference>

}
