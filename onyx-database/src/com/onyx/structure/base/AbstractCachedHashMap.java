package com.onyx.structure.base;

import com.onyx.structure.node.Header;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.store.Store;

import java.util.Map;

/**
 * Created by tosborn1 on 2/15/17.
 */
abstract class AbstractCachedHashMap<K,V> extends AbstractHashMap<K,V> {

    Map<Integer, Long> cache = new ConcurrentWeakHashMap();
    Map<Integer, Integer> mapCache = new ConcurrentWeakHashMap();

    AbstractCachedHashMap(Store fileStore, Header header, boolean headless, int loadFactor) {
        super(fileStore, header, headless, loadFactor);
    }

    long insertReference(int hash, long reference)
    {
        long ref = getReference(hash);
        if(ref == 0)
            super.insertReference(hash, reference);
        else
            super.updateReference(hash, reference);

        cache.put(hash, reference);
        return reference;
    }



    long getReference(int hash)
    {
        return cache.compute(hash, (integer, aLong) -> {
            if(aLong == null)
                aLong = AbstractCachedHashMap.super.getReference(hash);
            return aLong;
        });
    }

    int getMapIdentifier(int index) {
        return mapCache.compute(index, (integer, integer2) -> {
            if (integer2 != null)
                return integer2;
            return AbstractCachedHashMap.super.getMapIdentifier(index);
        });
    }

}
