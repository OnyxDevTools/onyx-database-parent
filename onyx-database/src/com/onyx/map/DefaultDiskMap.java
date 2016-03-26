package com.onyx.map;

import com.onyx.map.base.AbstractIterableDiskMap;
import com.onyx.map.base.CacheMap;
import com.onyx.map.node.BitMapNode;
import com.onyx.map.node.Header;
import com.onyx.map.node.RecordReference;
import com.onyx.map.store.Store;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class DefaultDiskMap<K, V> extends AbstractIterableDiskMap<K, V> implements DiskMap<K, V> {

    public DefaultDiskMap(Store fileStore, Header header)
    {
        super(fileStore, header);
    }

    public DefaultDiskMap(Store fileStore, Header header, boolean inMemory)
    {
        super(fileStore, header);
        if(inMemory)
        {
            nodeCache = Collections.synchronizedMap(new WeakHashMap());
            recordCache = Collections.synchronizedMap(new WeakHashMap());
            keyCache = Collections.synchronizedMap(new WeakHashMap());
        }
    }

    @Override
    public int size()
    {
        return (int) header.recordCount.get();
    }

    public long longSize()
    {
        return header.recordCount.get();
    }

    @Override
    public Store getFileStore() {
        return this.fileStore;
    }

    @Override
    public boolean isEmpty()
    {
        return (fileStore.getFileSize() == 0);
    }

    /**
     * Return the record id of the record.  The record id is represented by the record reference position in the data storage
     *
     * @param key
     * @return
     */
    public long getRecID(Object key)
    {
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);
        final int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        readWriteLock.readLock(hashDigits[1]).lock();

        try
        {
            Long ref = keyCache.get(key);
            if(ref != null)
                return ref;

            final BitMapNode node = this.seek(hash(key), false, hashDigits);
            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);
                if (references != null && references[1] != null)
                {
                    return references[1].position;
                }
            }
            return -1;
        } finally
        {
            readWriteLock.readLock(hashDigits[1]).unlock();
        }
    }

    /**
     * Get value with record id
     *
     * @param recordId
     * @return
     */
    public V getWithRecID(long recordId)
    {
        final RecordReference reference = this.getRecordReference(recordId);
        if(reference != null && reference.position == recordId)
        {
            return (V) getRecordValue(reference);
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key)
    {
        if(key == null)
        {
            return false;
        }

        if(keyCache.containsKey(key))
            return true;

        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);
        final int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        try
        {
            readWriteLock.readLock(hashDigits[1]).lock();

            final BitMapNode node = this.seek(hash(key), false, hashDigits);
            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);
                if (references != null && references[1] != null)
                {
                    return true;
                }
            }
            return false;
        } finally
        {
            readWriteLock.readLock(hashDigits[1]).unlock();
        }
    }

    @Override
    public boolean containsValue(Object value)
    {
        return false;
    }

    @Override
    public V get(Object key)
    {
        if(key == null)
            return null;

        Long ref = keyCache.get(key);
        if(ref != null)
        {
            return (V)getRecordValue(getRecordReference(ref));
        }

        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        try
        {

            readWriteLock.readLock(hashDigits[1]).lock();

            final BitMapNode node = this.seek(hash, false, hashDigits);
            if (node != null && node.next[hashDigits[BitMapNode.RECORD_REFERENCE_INDEX]] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);
                if (references != null && references[1] != null)
                {
                    return (V) getRecordValue(references[1]);
                }
            }
            return null;
        } finally
        {
            readWriteLock.readLock(hashDigits[1]).unlock();
        }
    }

    @Override
    public V put(K key, V value)
    {

        // Convert the hash number to digits with leading 0s
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {
            final BitMapNode node = this.seek(hash, true, hashDigits);

            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);

                if (references != null && references[1] != null)
                {
                    update(node, references[0], references[1], key, value, hashDigits);
                } else
                {
                    insert(references[0], node, key, value, hashDigits);
                }
            } else
            {
                insert(null, node, key, value, hashDigits);
            }
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
        return value;
    }

    @Override
    public V remove(Object key)
    {

        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {

            final BitMapNode node = this.seek(hash(key), true, hashDigits);
            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);
                if (references != null && references[1] != null)
                {
                    V value = (V)getRecordValue(references[1]);
                    delete(node, references[0], references[1], hashDigits, key);
                    return value;
                }
            }

            return null;
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
    }

    /**
     * Clear
     * <p/>
     * All we have to do is reset the header
     */
    @Override
    public void clear()
    {
        readWriteLock.writeLock().lock();

        try
        {
            header.firstNode = 0;
            header.recordCount.set(0);
            updateHeaderRecordCount();

            fileStore.write(header, header.position);

        } finally
        {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Put all values from one map to another.  This just runs through an iterator putting the values
     *
     * @param m
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m)
    {
        final Iterator<? extends Entry<? extends K, ? extends V>> iterator = m.entrySet().iterator();
        Entry<? extends K, ? extends V> entry = null;

        while (iterator.hasNext())
        {
            entry = iterator.next();
            this.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get or default, gets the value of the.  If it is null return default value
     *
     * @param key
     * @param defaultValue
     * @return
     */
    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        final V value = get(key);
        if (value == null)
        {
            return defaultValue;

        }
        return value;
    }

    /**
     * Put only if the key does not exist
     *
     * @param key
     * @param value
     * @return
     */
    @Override
    public V putIfAbsent(K key, V value)
    {
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {
            if (!containsKey(key))
            {
                return put(key, value);
            }
            return null;
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
    }

    /**
     * Remove object only if the value is equal to the one sent in
     *
     * @param key
     * @param value
     * @return
     */
    @Override
    public boolean remove(Object key, Object value)
    {

        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {
            V oldValue = get(key);
            if (value != null && value.equals(oldValue))
            {
                remove(key);
                return true;
            }
            return false;
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
    }

    /**
     * Replace only if the oldvalue != new value
     *
     * @param key
     * @param oldValue
     * @param newValue
     * @return
     */
    @Override
    public boolean replace(K key, V oldValue, V newValue)
    {
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {
            V value = get(key);
            if (value != null && !value.equals(oldValue))
            {
                put(key, newValue);
                return true;
            }
            return false;
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
    }

    /**
     * Simple replace
     *
     * @param key
     * @param value
     * @return
     */
    @Override
    public V replace(K key, V value)
    {
        return put(key, value);
    }

    /**
     * Compute if absent -  Nuf said.  Only put it if it does not exist
     *
     * @param key
     * @param mappingFunction
     * @return
     */
    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)
    {

        // Convert the hash number to digits with leading 0s
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {
            V value = null;
            final BitMapNode node = this.seek(hash, true, hashDigits);

            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);

                if (references != null && references[1] != null)
                {
                    return (V)getRecordValue(references[1]);
                } else
                {
                    value =  mappingFunction.apply(key);
                    insert(references[0], node, key, value, hashDigits);
                }
            } else
            {
                value =  mappingFunction.apply(key);
                insert(null, node, key, value, hashDigits);
            }
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
        return null;
    }

    /**
     * Only put it if the key exists
     *
     * @param key
     * @param remappingFunction
     * @return
     */
    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction)
    {
        // Convert the hash number to digits with leading 0s
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {
            V value = null;
            final BitMapNode node = this.seek(hash, true, hashDigits);

            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);

                if (references != null && references[1] != null)
                {
                    value =  remappingFunction.apply(key, (V)getRecordValue(references[1]));
                    update(node, references[0], references[1], key, value, hashDigits);
                    return value;
                } else
                {
                    return null;
                }
            }
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
        return null;
    }

    /**
     * Call handler to compute what value yous a gonna put
     *
     * @param key
     * @param remappingFunction
     * @return
     */
    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction)
    {
        // Convert the hash number to digits with leading 0s
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        V value = null;

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {


            final BitMapNode node = this.seek(hash, true, hashDigits);

            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);

                if (references != null && references[1] != null)
                {
                    value = remappingFunction.apply(key, (V)getRecordValue(references[1]));
                    update(node, references[0], references[1], key, value, hashDigits);
                } else
                {
                    value = remappingFunction.apply(key, null);
                    insert(references[0], node, key, value, hashDigits);
                }
            } else
            {
                value = remappingFunction.apply(key, null);
                insert(null, node, key, value, hashDigits);
            }
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
        return value;
    }

    /**
     * Merge the value.  Not sure, but calls a different fancy callback
     *
     * @param key
     * @param value
     * @param remappingFunction
     * @return
     */
    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction)
    {
        // Convert the hash number to digits with leading 0s
        int hash = hash(key);
        final int[] hashDigits = getHashDigits(hash);

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        readWriteLock.writeLock(hashDigits[1]).lock();

        try
        {
            final BitMapNode node = this.seek(hash, true, hashDigits);

            if (node != null && node.next[hashDigit] > 0)
            {
                final RecordReference[] references = this.getRecordReference(node, key, hashDigits);

                if (references != null && references[1] != null)
                {
                    value = remappingFunction.apply(value, (V)getRecordValue(references[1]));
                    update(node, references[0], references[1], key, value, hashDigits);
                } else
                {
                    value = remappingFunction.apply(value, null);
                    insert(references[0], node, key, value, hashDigits);
                }
            } else
            {
                value = remappingFunction.apply(value, null);
                insert(null, node, key, value, hashDigits);
            }
        } finally
        {
            readWriteLock.writeLock(hashDigits[1]).unlock();
        }
        return value;
    }
}
