package com.onyx.diskmap.base.concurrent;

import java.util.function.Function;

/**
 * Created by tosborn1 on 2/20/17.
 *
 * This implementation igores locking and does not provide any concurrency blocking.
 *
 * The purpose is so that it can be injected into a stateless implementation of a map.
 */
public class EmptyDispatchLock implements DispatchLock {

    /**
     * This method does not perform any blocking it is an empty implemented method that invokes the consumer
     *
     * @param lock     The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object performWithLock(Object lock, Function consumer) {
        return consumer.apply(lock);
    }
}
