package com.onyx.fetch;

import com.onyx.exception.EntityException;

import java.util.Map;

/**
 * Created by timothy.osborn on 1/6/15.
 */
public interface TableScanner
{

    /**
     * Full scan
     *
     * @return
     * @throws EntityException
     */
    public Map<Long, Long> scan() throws EntityException;

    /**
     * Scan with indexes
     *
     * @param existingValues
     * @return
     * @throws EntityException
     */
    public Map<Long, Long> scan(Map<Long, Long> existingValues) throws EntityException;

}
