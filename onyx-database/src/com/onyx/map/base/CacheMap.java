package com.onyx.map.base;


import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by timothy.osborn on 4/2/15.
 */
public class CacheMap implements Map {

    // MBean for monitoring
    protected MemoryPoolMXBean memoryPool = null;

    // Maximum threshold that will be used to flush the entire cache
    public static final Double MAX_THRESHOLD = 0.90;

    // Maximum threshold to release items
    public static final Double RELEASE_THRESHOLD = 0.80;

    // Maximum threshold to refrain from adding to cache
    public static final Double RELIEF_THRESHOLD = 0.70;

    // Number of items to release per release warning
    public static final int RELEASE_COUNT = 10000;

    /**
     * Constructor that initializes memory constraints
     *
     */
    public CacheMap()
    {

        // Get the HEAP memory pool
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans())
        {
            if (pool.getType() == MemoryType.HEAP && pool.isUsageThresholdSupported())
            {
                memoryPool = pool;
            }
        }

        // setting for threshold
        memoryPool.setCollectionUsageThreshold((int) Math.floor(memoryPool.getUsage().getMax() * MAX_THRESHOLD));
        memoryPool.setUsageThreshold((int) Math.floor(memoryPool.getUsage().getMax() * RELEASE_THRESHOLD));

        final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        final NotificationEmitter emitter = (NotificationEmitter) mbean;
        emitter.addNotificationListener(new NotificationListener() {
            public void handleNotification(Notification n, Object hb)
            {
                if (n.getType().equals( MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED))
                {
                    hash.clear(); // Clear the cache
                }
                else if (n.getType().equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED))
                {
                    flushLast(RELEASE_COUNT); // Release items
                }
            }
        }, null, null);
    }

    /**
     * The actual cache contained in a linked list
     */
    protected LinkedHashMap hash = new LinkedHashMap();

    /**
     * Getter to determine whether there is enough memory to add
     *
     * @return
     */
    protected boolean hasEnoughMemory()
    {
        long used = memoryPool.getUsage().getUsed();
        long threshold = (long) (memoryPool.getUsage().getMax() * RELIEF_THRESHOLD);
        return (used < threshold);
    }

    @Override
    public Object get(Object key)
    {
        return hash.get(key);
    }

    /**
     * Put into cache
     * @param key
     * @param value
     * @return
     */
    @Override
    public Object put(Object key, Object value)
    {
        if(hasEnoughMemory()) // If we have enough memory do this
            return hash.put(key, value);
        flushLast(1);// Otherwise, we result to a FIFO
        return hash.put(key, value);
    }

    /**
     * Flush items from cache
     *
     * @param count
     */
    public synchronized void flushLast(int count)
    {
        for (int i = 0; i < count; i++)
        {
            if(hash.size() > 0)
            {
                Iterator it = hash.keySet().iterator();
                if(it.hasNext())
                {
                    Object key = it.next();
                    hash.remove(key);
                }
            }
        }
    }

    @Override
    public int size()
    {
        return hash.size();
    }

    @Override
    public boolean isEmpty()
    {
        return hash.isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return hash.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        return hash.containsValue(value);
    }

    @Override
    public Object remove(Object key)
    {
        return hash.remove(key);
    }

    @Override
    public void putAll(Map m)
    {
        hash.putAll(m);
    }

    @Override
    public void clear()
    {
        hash.clear();
    }

    @Override
    public Set keySet()
    {
        return hash.keySet();
    }

    @Override
    public Collection values()
    {
        return hash.values();
    }

    @Override
    public Set<Entry> entrySet()
    {
        return hash.entrySet();
    }

}
