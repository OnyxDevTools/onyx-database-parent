package com.onyx.map.base;

import java.util.Observable;

/**
 * Created by tosborn1 on 8/30/16.
 *
 * This is a lock class used to count how many operations are in flight.  Once we drain to 0, we can fire an observer
 * for everything that is waiting for it to drain.
 */
public class ObservableCountDownLock extends Observable  {

    // Counter for how many operations are in flight
    private volatile int count = 0;

    /**
     * Default Constructor
     */
    public ObservableCountDownLock()
    {
    }

    /**
     * Increment
     */
    public synchronized void acquire()
    {
        count++;
        if(count == 1)
        {
            setChanged();
            notifyObservers(false);
            clearChanged();
        }
    }

    /**
     * Decrement
     * @return the current count
     */
    public synchronized int release()
    {
        count--;
        if(count == 0)
        {
            setChanged();
            notifyObservers(true);
            clearChanged();
        }
        return count;
    }
}
