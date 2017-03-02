package com.onyx.diskmap.base.concurrent;

import java.util.function.Function;


/**
 * Created by tosborn1 on 8/4/15.
 * <p>
 * This is the default implementation of the LevelReadWriteLock that implements it using 10 different StampLocks
 */
public class DefaultLevelReadWriteLock implements LevelReadWriteLock {

    //private final Map<Object, Object> references = new ConcurrentWeakHashMap<>();

    /**
     * Constructor.  Instantiate the level locks
     */
    public DefaultLevelReadWriteLock() {

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
        /*
        The commented out code referes to a methodology that would not use the exact reference but, utalizes a map
        to check to see if the values are equal and uses the tracked reference rather than the actual object that
        is passed in.
        final Object lock = references.compute(lockId, (o, reference) -> {
            if(reference == null)
                return lockId;

            return reference;
        });*/

        synchronized (lock) {
            return consumer.apply(lock);
        }
    }

}
