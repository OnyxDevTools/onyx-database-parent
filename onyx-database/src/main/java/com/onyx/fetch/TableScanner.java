package com.onyx.fetch;

import com.onyx.exception.OnyxException;

import java.util.Map;

/**
 * Created by timothy.osborn on 1/6/15.
 *
 * Contact used to scan entities
 */
public interface TableScanner
{

    /**
     * Full scan
     *
     * @return Matching references meeting criteria
     * @throws OnyxException Cannot scan entity
     */
    Map<Long, Long> scan() throws OnyxException;

    /**
     * Scan with indexes
     *
     * @param existingValues Existing references to start from
     * @return Matching references meeting criteria
     * @throws OnyxException Cannot scan entity
     */
    Map<Long, Long> scan(Map<Long, Long> existingValues) throws OnyxException;

}
