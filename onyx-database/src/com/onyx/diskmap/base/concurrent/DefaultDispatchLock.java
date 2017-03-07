package com.onyx.diskmap.base.concurrent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;


/**
 * Created by tosborn1 on 8/4/15.
 * <p>
 * This is the default implementation of the DispatchLock that implements it using 10 different StampLocks
 */
public class DefaultDispatchLock implements DispatchLock {

    final private Map<Object, Object> references = new WeakHashMap<>();

    /**
     * Constructor.  Instantiate the level locks
     */
    public DefaultDispatchLock() {

    }

    /**
     * This method performs a lambda function by locking on whatever object you pass in.  In this case it has
     * to be the exact reference.
     *
     * @param lock     The Object you want to block on
     * @param consumer Function to invoke
     * @return The result from the function
     */
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter unchecked")
    public Object performWithLock(Object lock, Function consumer) {

        Object objectToLockOn;
        synchronized (references) {
            objectToLockOn = references.get(lock);
            if (objectToLockOn == null) {
                references.put(lock, lock);
            } else {
                lock = objectToLockOn;
            }
        }

        synchronized (lock) {
            return consumer.apply(lock);
        }
    }

}
