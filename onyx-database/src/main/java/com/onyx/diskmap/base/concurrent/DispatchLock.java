package com.onyx.diskmap.base.concurrent;

import java.util.function.Function;

/**
 * Created by timothy.osborn on 4/7/15.
 *
 * This class allows read-write locks only for various levels.  There are 10 levels starting with 0 based counting.
 *
 * If you lock level 1 it will have no impact on the other levels.
 *
 * @since 1.2.0 This was changed to use a stamp lock for additional performance gains.  Since it cannot be used recursively.
 */
public interface DispatchLock
{

    /**
     * This method performs a lambda function by locking on whatever object you pass in.
     *
     * @param lock The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    Object performWithLock(Object lock, Function consumer);
}